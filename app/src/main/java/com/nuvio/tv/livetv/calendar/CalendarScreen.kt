package com.nuvio.tv.livetv.calendar

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.livetv.ui.LIVE_RED
import com.nuvio.tv.livetv.ui.LiveTag
import com.nuvio.tv.livetv.ui.LiveText
import com.nuvio.tv.livetv.ui.formatClock
import com.nuvio.tv.livetv.ui.liveCellColors
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// The Calendar's look is homelessbrian's (Nuvio + IPTV): the highlighted entry fills the top like
// Nuvio's Home, with rows of wide cards by day under it. Here it only shows your Library.

private enum class CalTab { UPCOMING, RECENT }
private enum class CalKind { ALL, SHOWS, MOVIES }

private val CARD_W = 208.dp
/** Holding Up/Down moves one row this often (ms). */
private const val HELD_ROW_MS = 180L
private const val HERO_H = 212

/** Nuvio's card focus: the theme's focus ring, 2 dp, and a 1.02 scale (same as ContentCard). */
private const val FOCUS_SCALE = 1.02f
private val RING_W = 2.dp

/** The theme's focus ring around [shape], only while [focused]. */
@Composable
private fun Modifier.focusRing(focused: Boolean, shape: Shape): Modifier {
    val ring = NuvioTheme.focusRing.border(RING_W)
    return if (focused) this.border(ring, shape) else this
}

/** At the end of a row, Right stays put instead of jumping to a card in another row. */
private fun Modifier.keepFocusInRow(): Modifier =
    this.focusProperties {
        onExit = { if (requestedFocusDirection == FocusDirection.Right) cancelFocusChange() }
    }.focusGroup()

/**
 * The Calendar: new episodes of the shows in your Library and release dates of the movies in
 * your Library (this profile's), as rows of big cards by day. OK opens the show's or movie's page.
 */
@Composable
fun CalendarScreen(
    /** Opens a show's or movie's page (at the episode, when there is one). */
    onOpenDetail: (id: String, type: String, addonBaseUrl: String?, season: Int?, episode: Int?) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val use24h by viewModel.use24h.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(CalTab.UPCOMING) }
    var kind by rememberSaveable { mutableStateOf(CalKind.ALL) }
    // Kept when you leave (a show page) so the same entry is up top when you come back.
    var heroKey by rememberSaveable { mutableStateOf<String?>(null) }
    val firstItem = remember { FocusRequester() }

    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val filtered = remember(state.entries, kind) {
        state.entries.filter {
            when (kind) {
                CalKind.ALL -> true
                CalKind.SHOWS -> it.kind == CalendarKind.EPISODE
                CalKind.MOVIES -> it.kind == CalendarKind.MOVIE
            }
        }
    }
    val upcoming = remember(filtered, today, now) {
        filtered.filter { !it.day.isBefore(today) && (it.day == today || whenOf(it, zone) + (it.runtimeMin ?: 30) * 60_000L > now) }
    }
    val recent = remember(filtered, today, now) {
        filtered.filter { !it.day.isAfter(today) && whenOf(it, zone) <= now }
            .sortedWith(compareByDescending<CalendarEntry> { it.day }.thenByDescending { it.airMs ?: 0L })
    }
    val pool = if (tab == CalTab.UPCOMING) upcoming else recent
    // The entry shown big at the top: the one highlighted, else the next thing coming.
    val hero = pool.firstOrNull { it.key == heroKey } ?: pool.firstOrNull()

    fun open(e: CalendarEntry) {
        onOpenDetail(e.contentId, e.type, e.addonBaseUrl, e.season, e.episode)
    }

    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        HeroBackdrop(hero)
        Column(Modifier.fillMaxSize()) {
            // ---------------------------------------------------------------- the big picture
            Hero(e = hero, now = now, use24h = use24h, loading = state.loading && state.entries.isEmpty(), state = state)
            // ---------------------------------------------------------------- tabs and refresh
            Row(
                Modifier.padding(start = 56.dp, end = 32.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabPill("Upcoming", tab == CalTab.UPCOMING) { tab = CalTab.UPCOMING; heroKey = null }
                TabPill("Recently released", tab == CalTab.RECENT) { tab = CalTab.RECENT; heroKey = null }
                Spacer(Modifier.weight(1f))
                if (state.loading) {
                    LiveText(
                        state.progress?.let { (a, b) -> "Updating… $a of $b" } ?: "Updating…",
                        size = 12.sp, color = NuvioTheme.colors.TextSecondary
                    )
                    Spacer(Modifier.width(10.dp))
                }
                IconPill(Modifier.focusProperties { right = FocusRequester.Cancel }) { viewModel.refresh() }
            }
            // ---------------------------------------------------------------- filters
            Row(
                Modifier.padding(start = 64.dp, end = 32.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Pill("All", kind == CalKind.ALL) { kind = CalKind.ALL; heroKey = null }
                Pill("Shows", kind == CalKind.SHOWS) { kind = CalKind.SHOWS; heroKey = null }
                Pill("Movies", kind == CalKind.MOVIES) { kind = CalKind.MOVIES; heroKey = null }
            }
            // ---------------------------------------------------------------- what's coming
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    pool.isEmpty() && state.problem != null && state.entries.isEmpty() -> EmptyNote(
                        text = "Check your connection and try again.",
                        headline = "Couldn't load the calendar",
                        action = "Retry", actionFocus = firstItem, onAction = { viewModel.refresh() }
                    )
                    pool.isEmpty() -> EmptyNote(
                        when {
                            state.loading && state.entries.isEmpty() -> "Looking up your shows and movies…"
                            state.titleCount == 0 -> "Nothing here yet. Add shows and movies to your Library and their new episodes and release dates show up here."
                            tab == CalTab.UPCOMING -> "Nothing from your Library coming up in the next four months."
                            else -> "Nothing from your Library came out in the last month."
                        }
                    )
                    else -> DayRows(
                        entries = pool,
                        newestFirst = tab == CalTab.RECENT,
                        use24h = use24h, now = now, firstFocus = firstItem,
                        onFocus = { heroKey = it.key }, onOpen = ::open
                    )
                }
            }
        }
    }
}

private fun whenOf(e: CalendarEntry, zone: ZoneId): Long =
    e.airMs ?: e.day.atStartOfDay(zone).toInstant().toEpochMilli()

// -------------------------------------------------------------------- the big picture at the top

/**
 * The highlighted entry's wide picture, top right, fading into the page on the left and at the
 * bottom. Never a poster: those cut off heads when stretched wide.
 */
@Composable
private fun HeroBackdrop(e: CalendarEntry?) {
    val art = e?.let { it.background ?: it.episodeImage }
    Box(Modifier.fillMaxWidth().height((HERO_H + 120).dp)) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.74f)
                .fillMaxHeight()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent, 0.18f to Color.Black.copy(alpha = 0.25f),
                            0.42f to Color.Black.copy(alpha = 0.75f), 0.62f to Color.Black
                        ),
                        blendMode = BlendMode.DstIn
                    )
                    drawRect(
                        Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 0.8f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            Crossfade(targetState = art, animationSpec = tween(350), label = "calendarHero") { url ->
                if (url != null) {
                    coil3.compose.AsyncImage(
                        model = url, contentDescription = null, contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun Hero(e: CalendarEntry?, now: Long, use24h: Boolean, loading: Boolean, state: CalendarState) {
    Column(
        Modifier.fillMaxWidth().height(HERO_H.dp).padding(start = 64.dp, top = 26.dp, end = 40.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (e == null) {
            LiveText("Calendar", size = 30.sp, weight = FontWeight.ExtraBold)
            LiveText(
                when {
                    loading -> "Looking up your shows and movies…"
                    state.titleCount > 0 -> "New episodes and release dates from your Library"
                    else -> "Your Library's new episodes and release dates show up here"
                },
                size = 15.sp, color = NuvioTheme.colors.TextSecondary
            )
            return@Column
        }
        val today = LocalDate.now()
        val airMs = e.airMs
        val onNow = airMs != null && now in airMs until airMs + (e.runtimeMin ?: 30) * 60_000L
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onNow) LiveTag("ON NOW", LIVE_RED, Color.White)
            e.tags.take(2).forEach { TagChip(it) }
            LiveTag(soonText(e, now, today).uppercase(), Color.White.copy(alpha = 0.16f), Color.White)
            if (e.watched) LiveTag("✓ WATCHED", Color.White.copy(alpha = 0.16f), Color.White)
        }
        LiveText(e.title.uppercase(), size = 28.sp, weight = FontWeight.ExtraBold, shadow = true, modifier = Modifier.width(560.dp))
        val line = when (e.kind) {
            CalendarKind.MOVIE -> e.note ?: if (CalendarTag.AT_HOME in e.tags) "Out to watch at home" else ""
            CalendarKind.EPISODE -> listOfNotNull(e.episodeCode, seasonDrop(e), e.episodeTitle).joinToString(" · ")
        }
        if (line.isNotBlank()) LiveText(line, size = 16.sp, weight = FontWeight.SemiBold, shadow = true, modifier = Modifier.width(560.dp))
        val whenText = dayWord(e.day, today) + when {
            airMs != null && !e.streaming -> " " + formatClock(airMs, use24h)
            e.streaming && e.kind == CalendarKind.EPISODE -> " · streaming"
            else -> ""
        }
        LiveText(
            listOfNotNull(whenText, e.network, e.runtimeMin?.let { "$it min" }).joinToString(" · "),
            size = 13.sp, color = NuvioTheme.colors.TextSecondary, shadow = true
        )
        e.description?.takeIf { it.isNotBlank() }?.let {
            LiveText(it, size = 13.sp, color = NuvioTheme.colors.TextSecondary, maxLines = 1, modifier = Modifier.width(560.dp))
        }
    }
}

// -------------------------------------------------------------------- rows of cards, by day

@Composable
private fun DayRows(
    entries: List<CalendarEntry>,
    newestFirst: Boolean,
    use24h: Boolean,
    now: Long,
    firstFocus: FocusRequester,
    onFocus: (CalendarEntry) -> Unit,
    onOpen: (CalendarEntry) -> Unit
) {
    val today = LocalDate.now()
    val listState = rememberLazyListState()
    val rowStates = remember { HashMap<LocalDate, LazyListState>() }
    val rows = remember(entries, newestFirst) {
        entries.distinctBy { it.key }.groupBy { it.day }.toList()
            .let { if (newestFirst) it.sortedByDescending { d -> d.first } else it.sortedBy { d -> d.first } }
    }
    // Holding Up/Down: one row at a steady pace, and the big picture up top waits until the
    // highlight rests.
    var held by remember { mutableStateOf(false) }
    var lastStep by remember { mutableStateOf(0L) }
    var resting by remember { mutableStateOf<CalendarEntry?>(null) }
    LaunchedEffect(resting, held) {
        val e = resting ?: return@LaunchedEffect
        if (!held) {
            delay(60)
            onFocus(e)
        }
    }
    LazyColumn(
        state = listState, contentPadding = PaddingValues(bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.onPreviewKeyEvent { k ->
            val code = k.nativeKeyEvent.keyCode
            if (k.type == KeyEventType.KeyDown && k.nativeKeyEvent.repeatCount == 0) held = false
            if (code != android.view.KeyEvent.KEYCODE_DPAD_DOWN && code != android.view.KeyEvent.KEYCODE_DPAD_UP) return@onPreviewKeyEvent false
            when (k.type) {
                KeyEventType.KeyUp -> { held = false; false }
                KeyEventType.KeyDown -> {
                    val t = android.os.SystemClock.uptimeMillis()
                    if (k.nativeKeyEvent.repeatCount > 0) {
                        held = true
                        if (t - lastStep < HELD_ROW_MS) return@onPreviewKeyEvent true
                    }
                    lastStep = t
                    false
                }
                else -> false
            }
        }
    ) {
        itemsIndexed(rows, key = { _, r -> r.first.toString() }) { rowIndex, (day, list) ->
            Column {
                Row(Modifier.padding(start = 64.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                    LiveText(dayWord(day, today), size = 17.sp, weight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    LiveText(day.format(DateTimeFormatter.ofPattern("EEE MMM d")), size = 13.sp, color = NuvioTheme.colors.TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    LiveText("· ${list.size}", size = 13.sp, color = NuvioTheme.colors.TextSecondary)
                }
                LazyRow(
                    modifier = Modifier.keepFocusInRow(),
                    state = rowStates.getOrPut(day) { LazyListState() },
                    contentPadding = PaddingValues(start = 64.dp, end = 40.dp, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(list, key = { _, e -> e.key }) { i, e ->
                        WideCard(
                            e = e, use24h = use24h, now = now,
                            modifier = if (rowIndex == 0 && i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onFocus = { resting = e }, onClick = { onOpen(e) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WideCard(
    e: CalendarEntry,
    use24h: Boolean,
    now: Long,
    modifier: Modifier = Modifier,
    width: Dp = CARD_W,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val airMs = e.airMs
    val onNow = airMs != null && now in airMs until airMs + (e.runtimeMin ?: 30) * 60_000L
    val height = width / (16f / 9f)
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier
                .graphicsLayer { val s = if (focused) FOCUS_SCALE else 1f; scaleX = s; scaleY = s }
                .size(width, height)
                .clip(shape)
                .background(NuvioTheme.colors.BackgroundCard)
                .focusRing(focused, shape)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
                .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        ) {
            val art = e.episodeImage ?: e.background ?: e.poster
            if (art != null) {
                coil3.compose.AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            // A dark edge at the bottom so the name reads on any picture.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))))
            Row(Modifier.align(Alignment.TopStart).padding(7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LiveTag(cardTime(e, use24h), Color.Black.copy(alpha = 0.78f), Color.White)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(7.dp)) {
                when {
                    onNow -> LiveTag("ON NOW", LIVE_RED, Color.White)
                    e.watched -> LiveTag("✓", Color.Black.copy(alpha = 0.78f), Color.White)
                    e.tags.isNotEmpty() -> TagChip(e.tags.first())
                    else -> Unit
                }
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.Bottom) {
                LiveText(
                    e.title.uppercase(),
                    size = 13.sp, weight = FontWeight.ExtraBold, color = Color.White, shadow = true,
                    modifier = Modifier.weight(1f), marquee = focused
                )
                e.network?.let {
                    Spacer(Modifier.width(6.dp))
                    LiveTag(it.take(14), Color.White.copy(alpha = 0.9f), Color(0xFF111111))
                }
            }
        }
        LiveText(
            when (e.kind) {
                CalendarKind.MOVIE -> e.note ?: "Movie"
                CalendarKind.EPISODE -> listOfNotNull(e.episodeCode, seasonDrop(e), e.episodeTitle).joinToString(" · ")
            },
            size = 12.sp, weight = FontWeight.Medium,
            color = if (focused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
            modifier = Modifier.padding(horizontal = 2.dp), marquee = focused
        )
    }
}

/** "8 episodes" for a season (or part of one) that comes out at once. */
private fun seasonDrop(e: CalendarEntry): String? {
    val first = e.episode ?: return null
    val last = e.lastEpisode?.takeIf { it > first } ?: return null
    return "${last - first + 1} episodes" + if (first == 1) " (whole season)" else ""
}

// -------------------------------------------------------------------- small parts

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val theme = NuvioTheme.colors
    val colors = liveCellColors(focused, if (selected) theme.Secondary else theme.BackgroundElevated)
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) theme.Secondary.copy(alpha = 0.26f) else colors.background)
            .border(if (focused) 2.dp else 0.dp, colors.border, shape)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 4.dp)
    ) {
        LiveText(
            text, size = 12.sp, weight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) theme.TextPrimary else colors.text
        )
    }
}

/** A tab: text with an accent underline when it's the one you're on. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val theme = NuvioTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (focused) theme.FocusBackground else Color.Transparent)
            .focusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Column(Modifier.width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally) {
            LiveText(
                text, size = 15.sp, weight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected || focused) theme.TextPrimary else theme.TextSecondary
            )
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) theme.Secondary else Color.Transparent))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = liveCellColors(focused, NuvioTheme.colors.BackgroundElevated)
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .size(30.dp)
            .clip(shape)
            .background(colors.background)
            .border(if (focused) 2.dp else 0.dp, colors.border, shape)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.tv.material3.Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = colors.text, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun TagChip(tag: CalendarTag) {
    val (bg, fg) = when (tag) {
        CalendarTag.NEW -> LIVE_RED to Color.White
        CalendarTag.SERIES_PREMIERE, CalendarTag.SEASON_PREMIERE -> Color(0xFF2B4C8C) to Color(0xFFDCE6FF)
        CalendarTag.SEASON_FINALE, CalendarTag.SERIES_FINALE -> Color(0xFF8A4B12) to Color(0xFFFFE2C7)
        CalendarTag.AT_HOME -> Color(0xFF1C6B45) to Color(0xFFD6F5E4)
        CalendarTag.IN_THEATERS, CalendarTag.ON_DISC -> Color(0xFF3A3D48) to Color(0xFFE6E8EE)
    }
    LiveTag(tag.label.uppercase(), bg, fg)
}

/** Nothing to show, or it couldn't load: one line of what's going on and, when there is one, the thing to do. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmptyNote(text: String, headline: String? = null, action: String? = null, actionFocus: FocusRequester? = null, onAction: () -> Unit = {}) {
    val theme = NuvioTheme.colors
    Box(Modifier.fillMaxSize().padding(top = 24.dp, start = 64.dp, end = 40.dp), contentAlignment = Alignment.TopStart) {
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).background(theme.BackgroundElevated).padding(horizontal = 20.dp, vertical = 16.dp).width(620.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (headline != null) LiveText(headline, size = 18.sp, weight = FontWeight.SemiBold)
            LiveText(text, size = 15.sp, color = theme.TextSecondary, maxLines = 4)
            if (action != null) {
                Spacer(Modifier.height(6.dp))
                var focused by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape(50)
                Box(
                    Modifier
                        .then(if (actionFocus != null) Modifier.focusRequester(actionFocus) else Modifier)
                        .clip(shape)
                        .background(theme.Secondary)
                        .focusRing(focused, shape)
                        .onFocusChanged { focused = it.isFocused }
                        .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAction)
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) { LiveText(action, size = 13.sp, weight = FontWeight.SemiBold, color = theme.OnSecondary) }
            }
        }
    }
}

private fun cardTime(e: CalendarEntry, use24h: Boolean): String {
    val airMs = e.airMs
    return when {
        e.kind == CalendarKind.MOVIE -> if (CalendarTag.IN_THEATERS in e.tags) "In theaters" else if (CalendarTag.ON_DISC in e.tags) "On disc" else "At home"
        airMs != null && !e.streaming -> formatClock(airMs, use24h)
        e.streaming -> "Streaming"
        else -> "All day"
    }
}

/** "In 3 h", "Tomorrow", "In 5 days", "2 days ago". */
private fun soonText(e: CalendarEntry, now: Long, today: LocalDate): String {
    val at = whenOf(e, ZoneId.systemDefault())
    val days = ChronoUnit.DAYS.between(today, e.day)
    return when {
        at <= now && days == 0L -> "Today"
        at <= now -> if (days == -1L) "Yesterday" else "${-days} days ago"
        days == 0L && e.airMs != null -> ((at - now) / 3_600_000L).let { h -> if (h < 1) "In ${((at - now) / 60_000L).coerceAtLeast(1)} min" else "In $h h" }
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "In $days days"
    }
}

private fun dayWord(day: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, day)) {
    0L -> "Today"
    1L -> "Tomorrow"
    -1L -> "Yesterday"
    in 2L..6L -> day.format(DateTimeFormatter.ofPattern("EEEE"))
    else -> day.format(DateTimeFormatter.ofPattern("EEE MMM d"))
}
