package com.nuvio.tv.livetv.calendar

import android.content.Context
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Calendar: when the next episodes of the shows in your Library come out, and when the
 * movies in your Library come out (in theaters, to watch at home, on disc).
 *
 * Only your Library counts, and only the Library of the profile that's in use: switching
 * profiles clears the Calendar at once and builds it again from that profile's Library. Nuvio's
 * Library already follows the active profile (its own saved list, or the Trakt / Simkl lists that
 * profile is signed in to), so the Calendar reads it as it is.
 *
 * Dates come from TVmaze (episodes) and TMDB (movies) through [CalendarSources]; what's looked
 * up is kept on the TV, so most visits need no network at all.
 */
@Singleton
class CalendarRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val library: LibraryRepository,
    private val progress: WatchProgressRepository,
    private val profileManager: ProfileManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sources = CalendarSources(context)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(CalendarState())
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    /** The Library the Calendar was last built from, and whose it is. */
    @Volatile private var lastItems: List<LibraryEntry> = emptyList()
    @Volatile private var lastProfile: Int = -1
    @Volatile private var started = false

    /** Started the first time the Calendar is opened; keeps itself up to date after that. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            profileManager.activeProfileId.collectLatest { profileId ->
                // Another profile: nothing of the last one's stays on screen.
                lastProfile = profileId
                lastItems = emptyList()
                _state.value = CalendarState(loading = true)
                library.libraryItems
                    // Only what the Calendar uses: a new poster or list order doesn't rebuild it.
                    .map { items -> items.filter { isMovie(it.type) || isSeries(it.type) } }
                    .distinctUntilChanged { a, b -> fingerprint(a) == fingerprint(b) }
                    .debounce(800)
                    .collectLatest { items ->
                        lastItems = items
                        // What's kept on the TV first (instant), then a fresh look where it's due.
                        build(profileId, items, fetch = false, force = false)
                        build(profileId, items, fetch = true, force = false)
                    }
            }
        }
        scope.launch {
            // Every few hours while the app is open (times move, new episodes get announced).
            while (true) {
                delay(4 * CalendarSources.HOUR)
                refresh(force = false)
            }
        }
    }

    /** Looks everything up again ([force]: even what was looked up a moment ago). */
    fun refresh(force: Boolean = true) {
        val profileId = lastProfile
        if (profileId < 0) return
        val items = lastItems
        scope.launch { build(profileId, items, fetch = true, force = force) }
    }

    // ------------------------------------------------------------------ building the Calendar

    private fun fingerprint(items: List<LibraryEntry>): Set<String> =
        items.mapTo(HashSet()) { "${it.type}|${it.id}|${it.imdbId}|${it.tmdbId}" }

    /** A show or movie from the Library. */
    private data class Candidate(
        val id: String,
        /** The Library's own type ("series", "tv", "movie"…): what its page is opened with. */
        val libraryType: String,
        val series: Boolean,
        val name: String,
        val poster: String?,
        val background: String?,
        val imdb: String?,
        val tmdb: Int?,
        val addonBaseUrl: String?,
        val year: Int?,
        val listedAt: Long
    )

    private fun candidates(items: List<LibraryEntry>): List<Candidate> {
        val out = LinkedHashMap<String, Candidate>()
        items.sortedByDescending { it.listedAt }.forEach { e ->
            val series = isSeries(e.type)
            if (!series && !isMovie(e.type)) return@forEach
            val imdb = e.imdbId?.takeIf { it.startsWith("tt") } ?: e.id.takeIf { it.startsWith("tt") }
            // The same title twice (by IMDb id and by another id): once.
            val key = imdb ?: e.id
            if (key in out) return@forEach
            out[key] = Candidate(
                id = e.id, libraryType = e.type, series = series, name = e.name, poster = e.poster,
                background = e.background, imdb = imdb, tmdb = e.tmdbId ?: tmdbOf(e.id),
                addonBaseUrl = e.addonBaseUrl, year = yearOf(e.releaseInfo), listedAt = e.listedAt
            )
        }
        return out.values.toList()
    }

    /** Episodes you've seen of each show: the furthest one, and every one marked watched. */
    private data class Seen(val furthest: Pair<Int, Int>?, val watched: Set<Pair<Int, Int>>)

    private suspend fun seenEpisodes(): Map<String, Seen> {
        val prog: List<WatchProgress> = withTimeoutOrNull(15_000) { progress.allProgress.first() }.orEmpty()
        val watched: List<WatchedItem> = withTimeoutOrNull(15_000) { progress.watchedItems.first() }.orEmpty()
        val furthest = HashMap<String, Pair<Int, Int>>()
        val marks = HashMap<String, MutableSet<Pair<Int, Int>>>()
        fun note(id: String, s: Int?, e: Int?, done: Boolean) {
            if (s == null || e == null) return
            val p = s to e
            val f = furthest[id]
            if (f == null || compare(p, f) > 0) furthest[id] = p
            if (done) marks.getOrPut(id) { HashSet() } += p
        }
        prog.forEach { note(it.contentId, it.season, it.episode, it.isCompleted()) }
        watched.forEach { note(it.contentId, it.season, it.episode, true) }
        // Movies watched: (0, 0).
        prog.filter { isMovie(it.contentType) && it.isCompleted() }.forEach { marks.getOrPut(it.contentId) { HashSet() } += (0 to 0) }
        watched.filter { isMovie(it.contentType) }.forEach { marks.getOrPut(it.contentId) { HashSet() } += (0 to 0) }
        return (furthest.keys + marks.keys).associateWith { Seen(furthest[it], marks[it].orEmpty()) }
    }

    private suspend fun build(profileId: Int, items: List<LibraryEntry>, fetch: Boolean, force: Boolean) {
        try {
            mutex.withLock { buildLocked(profileId, items, fetch, force) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ProfileChanged) {
            // Another profile's Calendar is being built now: this one is simply dropped.
        } catch (error: Exception) {
            if (profileManager.activeProfileId.value == profileId) {
                _state.value = _state.value.copy(loading = false, progress = null, problem = error.message ?: "Couldn't update")
            }
        }
    }

    /** Still the profile (and Library) this build is for. */
    private suspend fun current(profileId: Int) {
        currentCoroutineContext().ensureActive()
        if (profileManager.activeProfileId.value != profileId) throw ProfileChanged()
    }

    private class ProfileChanged : Exception()

    private suspend fun buildLocked(profileId: Int, items: List<LibraryEntry>, fetch: Boolean, force: Boolean) {
        current(profileId)
        val all = candidates(items)
        val shows = all.filter { it.series }.take(MAX_SHOWS)
        // Movies: only recent ones can have a release coming (older ones were out long ago).
        val thisYear = LocalDate.now().year
        val movies = all.filter { !it.series && (it.year == null || it.year >= thisYear - 1) }.take(MAX_MOVIES)
        if (shows.isEmpty() && movies.isEmpty()) {
            _state.value = CalendarState(updatedAt = System.currentTimeMillis())
            return
        }
        if (fetch) _state.value = _state.value.copy(loading = true, progress = 0 to (shows.size + movies.size), problem = null)
        val seen = seenEpisodes()
        current(profileId)
        val today = LocalDate.now()
        val from = today.minusDays(30)
        val to = today.plusDays(120)
        val entries = ArrayList<CalendarEntry>()
        var done = 0
        var reached = 0
        // The same show can be in the Library twice under different ids: each show is taken once.
        val usedShows = HashSet<Int>()
        for (c in shows) {
            current(profileId)
            val tvmId = tvmazeOf(c.id) ?: if (fetch) safely { sources.tvmazeId(c.imdb, c.tmdb) } else sources.knownTvmazeId(c.imdb, c.tmdb)
            if (tvmId != null && !usedShows.add(tvmId)) { done++; continue }
            var data = tvmId?.let { sources.cachedShow(it) }
            if (fetch && tvmId != null && (force || data == null || stale(data))) {
                data = safely { sources.fetchShow(tvmId) } ?: data
            }
            done++
            if (data != null) reached++
            if (fetch && done % 5 == 0) _state.value = _state.value.copy(progress = done to (shows.size + movies.size))
            if (data == null) continue
            entries += episodesOf(c, data, seen[c.id] ?: c.imdb?.let { seen[it] }, from, to)
        }
        // Shows with no wide picture (TVmaze only had a poster): TMDB's, looked up once and kept.
        if (fetch) {
            val missing = entries.filter { it.background == null }
                .mapNotNull { e -> shows.firstOrNull { it.id == e.contentId }?.imdb }
                .distinct().filter { !sources.backdropLooked(it) }.take(150)
            for (imdb in missing) {
                current(profileId)
                safely { sources.showBackdrop(imdb) }
            }
            if (missing.isNotEmpty()) {
                sources.saveBackdrops()
                for (i in entries.indices) {
                    val e = entries[i]
                    if (e.background != null) continue
                    val imdb = shows.firstOrNull { it.id == e.contentId }?.imdb
                    sources.knownBackdrop(imdb)?.let { entries[i] = e.copy(background = it) }
                }
            }
        }
        val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
        for (m in movies) {
            current(profileId)
            val dates = safely { sources.movieDates(m.imdb, m.tmdb, country, cachedOnly = !fetch) }
            done++
            if (fetch && done % 5 == 0) _state.value = _state.value.copy(progress = done to (shows.size + movies.size))
            if (dates == null) continue
            reached++
            entries += moviesOf(m, dates, seen[m.id] ?: m.imdb?.let { seen[it] }, from, to)
        }
        sources.saveMovies()
        current(profileId)
        val sorted = entries.distinctBy { it.key }
            .sortedWith(compareBy<CalendarEntry> { it.day }.thenBy { it.airMs ?: Long.MAX_VALUE }.thenBy { it.title })
        val nothingReached = fetch && reached == 0 && (shows.size + movies.size) > 0
        _state.value = CalendarState(
            // A fresh look that reached nothing (no connection) keeps what's already showing.
            entries = if (nothingReached && sorted.isEmpty()) _state.value.entries else sorted,
            loading = false,
            progress = null,
            updatedAt = System.currentTimeMillis(),
            titleCount = shows.size + movies.size,
            problem = if (nothingReached && sorted.isEmpty()) "Couldn't reach TVmaze or TMDB" else null
        )
    }

    private suspend fun <T> safely(block: suspend () -> T?): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun stale(d: CalendarSources.ShowData): Boolean {
        val age = System.currentTimeMillis() - d.fetchedAt
        val ended = d.show.status.equals("Ended", ignoreCase = true)
        // An episode out in the next day or so: checked more often (times and dates move).
        val soon = d.episodes.any { e ->
            val t = e.airMs ?: e.airDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: return@any false
            kotlin.math.abs(t - System.currentTimeMillis()) < 36 * CalendarSources.HOUR
        }
        return when {
            ended -> age > 7 * CalendarSources.DAY
            soon -> age > 3 * CalendarSources.HOUR
            else -> age > 20 * CalendarSources.HOUR
        }
    }

    private fun episodesOf(
        c: Candidate,
        d: CalendarSources.ShowData,
        seen: Seen?,
        from: LocalDate,
        to: LocalDate
    ): List<CalendarEntry> {
        val now = System.currentTimeMillis()
        val eps = d.episodes.filter { e -> e.airDate != null && !e.airDate.isBefore(from) && !e.airDate.isAfter(to) }
            .sortedWith(compareBy<CalendarSources.Episode> { it.season }.thenBy { it.number })
        if (eps.isEmpty()) return emptyList()
        val lastSeason = d.episodes.maxOfOrNull { it.season }
        val ended = d.show.status.equals("Ended", ignoreCase = true)
        // Several episodes out at once (a streaming season) make one entry.
        val groups = ArrayList<MutableList<CalendarSources.Episode>>()
        for (e in eps) {
            val g = groups.lastOrNull()
            val prev = g?.last()
            if (prev != null && prev.season == e.season && prev.airDate == e.airDate && prev.number + 1 == e.number &&
                (prev.airMs == e.airMs || d.show.streaming)
            ) g.add(e) else groups += mutableListOf(e)
        }
        return groups.map { g ->
            val e = g.first()
            val last = g.last()
            val tags = ArrayList<CalendarTag>()
            when {
                e.number == 1 && e.season == 1 -> tags += CalendarTag.SERIES_PREMIERE
                e.number == 1 -> tags += CalendarTag.SEASON_PREMIERE
            }
            val total = last.seasonEpisodes
            if (total != null && last.number >= total && !(e.number == 1 && g.size == 1 && total > 1)) {
                tags += if (ended && last.season == lastSeason) CalendarTag.SERIES_FINALE else CalendarTag.SEASON_FINALE
            }
            // West Coast: the delayed airing's time (see CalendarAirTimes).
            val local = CalendarAirTimes.local(e.airMs, d.show.zone, d.show.airTime, d.show.streaming)
            val aired = (local ?: e.airDate!!.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) <= now
            val pos = e.season to e.number
            val watched = seen != null && (pos in seen.watched || (seen.furthest != null && compare(pos, seen.furthest) < 0))
            if (aired && !watched && !e.airDate!!.isBefore(LocalDate.now().minusDays(1))) tags.add(0, CalendarTag.NEW)
            CalendarEntry(
                key = "ep|${c.id}|${e.season}|${e.number}",
                kind = CalendarKind.EPISODE,
                contentId = c.id,
                type = c.libraryType,
                title = c.name.ifBlank { d.show.name },
                poster = c.poster ?: d.show.image,
                background = c.background ?: d.show.backdrop ?: sources.knownBackdrop(c.imdb),
                addonBaseUrl = c.addonBaseUrl,
                season = e.season,
                episode = e.number,
                lastEpisode = last.number.takeIf { g.size > 1 },
                episodeTitle = if (g.size > 1) null else e.name,
                episodeImage = e.image,
                description = e.summary.takeIf { g.size == 1 },
                airMs = local,
                day = local?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: e.airDate!!,
                network = d.show.network,
                streaming = d.show.streaming || e.airMs == null,
                tags = tags,
                watched = watched,
                runtimeMin = e.runtime
            )
        }
    }

    private fun moviesOf(
        m: Candidate,
        dates: CalendarSources.MovieDates,
        seen: Seen?,
        from: LocalDate,
        to: LocalDate
    ): List<CalendarEntry> {
        val out = ArrayList<CalendarEntry>()
        val watched = seen?.watched?.contains(0 to 0) == true
        fun fmt(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("MMM d"))
        fun entry(day: LocalDate, tag: CalendarTag, note: String?) = CalendarEntry(
            key = "mv|${m.id}|$tag", kind = CalendarKind.MOVIE, contentId = m.id, type = m.libraryType,
            title = m.name.ifBlank { dates.title ?: "" }, poster = m.poster ?: dates.poster, background = m.background ?: dates.backdrop,
            description = dates.overview,
            addonBaseUrl = m.addonBaseUrl, day = day, tags = listOf(tag), note = note, watched = watched
        )
        val home = dates.digital
        dates.theater?.takeIf { !it.isBefore(from) && !it.isAfter(to) }?.let { t ->
            out += entry(t, CalendarTag.IN_THEATERS, home?.let { "At home ${fmt(it)}" } ?: "Not out to watch at home yet")
        }
        home?.takeIf { !it.isBefore(from) && !it.isAfter(to) }?.let { h ->
            out += entry(h, CalendarTag.AT_HOME, dates.theater?.let { "In theaters ${fmt(it)}" })
        }
        if (home == null) dates.disc?.takeIf { !it.isBefore(from) && !it.isAfter(to) }?.let { b ->
            out += entry(b, CalendarTag.ON_DISC, dates.theater?.let { "In theaters ${fmt(it)}" })
        }
        return out
    }

    companion object {
        private const val MAX_SHOWS = 300
        private const val MAX_MOVIES = 150

        fun isSeries(type: String) = type.equals("series", true) || type.equals("tv", true) || type.equals("show", true) ||
            type.equals("anime", true)

        fun isMovie(type: String) = type.equals("movie", true)

        fun tvmazeOf(id: String): Int? = if (id.startsWith("tvmaze:")) id.removePrefix("tvmaze:").toIntOrNull() else null

        fun tmdbOf(id: String): Int? = if (id.startsWith("tmdb:")) id.removePrefix("tmdb:").substringBefore(':').toIntOrNull() else null

        fun yearOf(s: String?): Int? = s?.let { Regex("(19|20)\\d{2}").find(it)?.value?.toIntOrNull() }

        fun compare(a: Pair<Int, Int>, b: Pair<Int, Int>): Int = if (a.first != b.first) a.first.compareTo(b.first) else a.second.compareTo(b.second)
    }
}
