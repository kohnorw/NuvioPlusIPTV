package com.nuvio.tv.livetv.calendar

import androidx.compose.runtime.Immutable
import java.time.LocalDate

enum class CalendarKind { EPISODE, MOVIE }

/** Tags beside an entry. */
enum class CalendarTag(val label: String) {
    NEW("New"),
    SERIES_PREMIERE("Series premiere"),
    SEASON_PREMIERE("Season premiere"),
    SEASON_FINALE("Season finale"),
    SERIES_FINALE("Series finale"),
    IN_THEATERS("In theaters"),
    AT_HOME("At home"),
    ON_DISC("On disc")
}

/** A new episode of a show in your Library, or a release date of a movie in your Library. */
@Immutable
data class CalendarEntry(
    /** Unique per entry. */
    val key: String,
    val kind: CalendarKind,
    /** Nuvio's id for the show or movie (IMDb "tt…" when known). */
    val contentId: String,
    /** "series" or "movie". */
    val type: String,
    val title: String,
    val poster: String?,
    val background: String?,
    val addonBaseUrl: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** The last episode when several come out together (a whole season at once). */
    val lastEpisode: Int? = null,
    val episodeTitle: String? = null,
    val episodeImage: String? = null,
    /** When it airs for you (null: only the day is known). */
    val airMs: Long? = null,
    /** The day it airs in your time zone. */
    val day: LocalDate,
    /** The network or streaming service. */
    val network: String? = null,
    /** On a streaming service (no air time). */
    val streaming: Boolean = false,
    val tags: List<CalendarTag> = emptyList(),
    /** Movies: the other release ("In theaters Oct 2"). */
    val note: String? = null,
    /** You've watched it. */
    val watched: Boolean = false,
    val runtimeMin: Int? = null,
    /** What it's about (the episode's summary, the movie's overview). */
    val description: String? = null
) {
    val episodeCode: String?
        get() = if (season == null || episode == null) null else buildString {
            append("S$season · E$episode")
            if (lastEpisode != null && lastEpisode > episode) append("–E$lastEpisode")
        }
}

/** What the Calendar shows. */
@Immutable
data class CalendarState(
    val entries: List<CalendarEntry> = emptyList(),
    /** Still looking up shows. */
    val loading: Boolean = false,
    /** Titles looked up so far / in all (while loading). */
    val progress: Pair<Int, Int>? = null,
    val updatedAt: Long = 0L,
    /** Shows and movies from your Library on the Calendar. */
    val titleCount: Int = 0,
    val problem: String? = null
)

/**
 * TV listings give East Coast times. On Mountain, Pacific, Alaska or Hawaii time, a show on an
 * East Coast network airs at the same clock time (a West Coast feed), not hours earlier.
 */
object CalendarAirTimes {
    private val eastern: java.time.ZoneId = java.time.ZoneId.of("America/New_York")

    /** Hours to take off the listed clock time on this box (0 = Pacific or further west), or null: no change. */
    fun westShiftHours(): Int? {
        val zone = java.time.ZoneId.systemDefault()
        // Many boxes are set to a plain offset ("GMT-08:00") or an old name ("PST8PDT") instead of a city.
        val id = zone.id
        val american = id.startsWith("America/") || id.startsWith("US/") || id.startsWith("Canada/") ||
            id.startsWith("Pacific/Honolulu") || id in setOf("PST8PDT", "MST7MDT", "MST", "PST", "HST", "SystemV/PST8", "SystemV/MST7") ||
            zone is java.time.ZoneOffset || id.startsWith("GMT") || id.startsWith("UTC") || id.startsWith("Etc/GMT")
        if (!american) return null
        val std = zone.rules.getStandardOffset(java.time.Instant.now()).totalSeconds / 3600
        return when {
            std in -11..-8 -> 0
            std == -7 -> 1
            else -> null
        }
    }

    /**
     * The time [listedMs] airs here: the same clock time (less [westShiftHours]) when the show is on
     * an East Coast network ([networkZone], or, when that isn't known, a listed time that matches
     * the schedule's [scheduleTime] in Eastern time). Otherwise [listedMs] as it is.
     */
    fun local(listedMs: Long?, networkZone: String?, scheduleTime: String?, streaming: Boolean): Long? {
        if (listedMs == null || streaming) return listedMs
        val shift = westShiftHours() ?: return listedMs
        val east = java.time.Instant.ofEpochMilli(listedMs).atZone(eastern)
        val isEast = if (networkZone != null) {
            networkZone.startsWith("America/") && runCatching {
                java.time.ZoneId.of(networkZone).rules.getOffset(east.toInstant()) == east.offset
            }.getOrDefault(false)
        } else {
            scheduleTime != null && runCatching { java.time.LocalTime.parse(scheduleTime) == east.toLocalTime().withSecond(0).withNano(0) }.getOrDefault(false)
        }
        if (!isEast) return listedMs
        return east.toLocalDateTime().minusHours(shift.toLong()).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
