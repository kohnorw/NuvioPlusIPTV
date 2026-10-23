package com.nuvio.tv.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import com.nuvio.tv.livetv.model.LiveChannel
import com.nuvio.tv.ui.theme.NuvioTheme

/** What the bottom panel is showing. */
internal enum class PanelMode { LIVE, CATCHUP, SHIFT }

/** The panel's buttons and quick options, in the order the highlight moves through them. */
internal enum class PanelControl { TIMELINE, BACK, PLAY, FORWARD, LIVE, CC, AUDIO, SIZE, SLEEP, MORE }

/** Timeline: everything in wall-clock milliseconds. */
internal data class PanelTimeline(
    val startMs: Long,
    val endMs: Long,
    val positionMs: Long,
    /** Where "live" is on the line (pause and rewind), or null. */
    val liveMs: Long? = null,
    /** The recorded stretch ends here (pause and rewind), or null. */
    val recordedUntilMs: Long? = null,
    val leftLabel: String,
    val rightLabel: String,
    val status: String,
    val statusLive: Boolean = false
)

/** Which controls the panel offers in each mode. */
internal fun panelControls(mode: PanelMode, paused: Boolean, canSkip: Boolean): List<PanelControl> = buildList {
    val transport = mode != PanelMode.LIVE || paused
    // The timeline itself can be grabbed (Up from the buttons) and moved with ◀ / ▶.
    if (transport && canSkip) add(PanelControl.TIMELINE)
    if (transport && canSkip) add(PanelControl.BACK)
    add(PanelControl.PLAY)
    if (transport && canSkip) add(PanelControl.FORWARD)
    if (mode != PanelMode.LIVE) add(PanelControl.LIVE)
    // Audio and Sleep are in the ••• menu, so the time bar gets their room.
    add(PanelControl.CC); add(PanelControl.SIZE); add(PanelControl.MORE)
}

/**
 * The full-screen player's bottom panel, in two sizes:
 *  - compact ([full] = false): channel, show and progress, shown while changing channels;
 *  - full (OK): also the description, the timeline (live, catch-up, or a pause-and-rewind
 *    recording), the playback buttons and quick options.
 * [focused] is the highlighted control in the full panel, else null. Colors follow Nuvio's theme.
 */
@Composable
internal fun LiveTvPlayerPanel(
    channel: LiveChannel,
    mode: PanelMode,
    full: Boolean,
    title: String,
    meta: String,
    description: String?,
    poster: String?,
    showPosters: Boolean,
    timeline: PanelTimeline?,
    paused: Boolean,
    controls: List<PanelControl>,
    focused: PanelControl?,
    captionsOn: Boolean,
    audioLabel: String,
    sizeLabel: String,
    sleepLabel: String?,
    clock: String,
    resolution: String?,
    showNumber: Boolean,
    /** What's on next ("World Tonight") and when ("7:00 – 8:00 PM"), shown on the right. */
    nextTitle: String? = null,
    nextTime: String? = null,
    /** The channel offers catch-up (a tag beside the channel). */
    catchupAvailable: Boolean = false,
    /** The full panel's Recent row (channels watched before), or null for none. */
    recent: List<PanelRecent>? = null,
    /** The highlighted tile in the Recent row: 0 TV Guide, 1… the channels, then Clear. */
    recentFocus: Int? = null,
    /** The row's heading: "RECENT" or "FAVORITES". */
    recentTitle: String = "RECENT",
    /** A Clear tile at the end of the row (Recent only). */
    recentClear: Boolean = true,
    /** Channel surfing: this is a channel you're looking at, not the one playing. */
    browsing: Boolean = false,
    modifier: Modifier = Modifier,
    /** The sound of what's playing, next to the picture size ("5.1", "DD+ 5.1", "STEREO"). */
    audioBadge: String? = null,
    /** How dark the background is: "standard", "darker" or "solid" (Live TV settings). */
    background: String = "standard"
) {
    // The OK panel: one compact layout, the same height with or without the channel row.
    if (full) {
        FullInfoPanel(
            channel, mode, title, meta, description, poster, showPosters, timeline, paused, controls,
            focused, captionsOn, audioLabel, sizeLabel, sleepLabel, clock, resolution, showNumber,
            nextTitle, nextTime, catchupAvailable, recent, recentFocus, recentTitle, recentClear, modifier,
            audioBadge = audioBadge, background = background
        )
    } else {
    val withRecent = false
    val colors = NuvioTheme.colors
    val accent = colors.Secondary
    val posterW = if (withRecent) 88.dp else if (full) 116.dp else 110.dp
    val posterH = if (withRecent) 132.dp else if (full) 174.dp else 165.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Fades in above the text (in the top padding), then stays dark behind it.
            .panelScrim(colors.Background, background, fade = 56.dp)
            .padding(start = 48.dp, end = 48.dp, top = if (withRecent) 48.dp else 64.dp, bottom = if (withRecent) 14.dp else if (full) 18.dp else 24.dp)
    ) {
    Row(verticalAlignment = Alignment.Bottom) {
        // Poster (or the channel logo when posters are off or there isn't one).
        val posterShape = RoundedCornerShape(liveCorner())
        Box(
            modifier = Modifier
                .width(if (showPosters) posterW else 72.dp)
                .height(if (showPosters) posterH else 72.dp)
                .clip(posterShape)
                .background(colors.BackgroundCard.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), posterShape),
            contentAlignment = Alignment.Center
        ) {
            if (showPosters && !poster.isNullOrBlank()) {
                coil3.compose.AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ChannelLogo(channel.logo, if (showPosters) 56.dp else 48.dp)
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (browsing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveTag("BROWSING", accent, colors.OnSecondary)
                    Spacer(Modifier.width(10.dp))
                    LiveText(
                        "OK to watch  ·  ▲ ▼ more channels (hold to go faster)  ·  Back to stay",
                        size = 12.sp, color = colors.TextSecondary
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            // Channel line: logo, number and name, tags, and the clock on the right.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!channel.logo.isNullOrBlank()) {
                    ChannelLogo(channel.logo, 24.dp)
                    Spacer(Modifier.width(10.dp))
                }
                LiveText(
                    (if (showNumber) "${channel.number} · " else "") + channel.name,
                    size = 14.sp, weight = FontWeight.SemiBold, color = colors.TextSecondary,
                    shadow = true
                )
                when (mode) {
                    PanelMode.LIVE -> { Spacer(Modifier.width(10.dp)); LiveTag("LIVE", LIVE_RED, Color.White) }
                    PanelMode.CATCHUP -> { Spacer(Modifier.width(10.dp)); LiveTag("ARCHIVE", accent, colors.OnSecondary) }
                    PanelMode.SHIFT -> { Spacer(Modifier.width(10.dp)); LiveTag("PAUSED LIVE", accent, colors.OnSecondary) }
                }
                resolution?.let {
                    Spacer(Modifier.width(6.dp))
                    LiveTag(it.uppercase(), Color.Transparent, colors.TextSecondary,
                        Modifier.border(1.dp, colors.Border, RoundedCornerShape(5.dp)))
                }
                audioBadge?.let {
                    Spacer(Modifier.width(6.dp))
                    LiveTag(it, Color.Transparent, colors.TextSecondary,
                        Modifier.border(1.dp, colors.Border, RoundedCornerShape(5.dp)))
                }
                if (catchupAvailable && mode == PanelMode.LIVE) {
                    Spacer(Modifier.width(6.dp))
                    LiveTag("CATCH-UP", Color.Transparent, colors.TextSecondary,
                        Modifier.border(1.dp, colors.Border, RoundedCornerShape(5.dp)))
                }
                Spacer(Modifier.weight(1f))
                if (full) timeline?.let {
                    LiveText(it.status, size = 13.sp, color = if (it.statusLive) colors.Error else accent, modifier = Modifier.padding(end = 14.dp))
                }
                LiveText(clock, size = 18.sp, weight = FontWeight.Bold, color = colors.TextPrimary)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    LiveText(title, size = if (withRecent) 26.sp else 30.sp, weight = FontWeight.ExtraBold, maxLines = 1, marquee = true, color = colors.TextPrimary, shadow = true)
                    // Channel surfing: the lines are always there (empty until the details arrive),
                    // so nothing moves when the description fills in.
                    if (meta.isNotBlank() || browsing) {
                        Spacer(Modifier.height(2.dp))
                        LiveText(meta.ifBlank { " " }, size = 14.sp, color = colors.TextSecondary, maxLines = 1, shadow = true)
                    }
                    val desc = description?.takeIf { it.isNotBlank() }
                    if (desc != null || browsing) {
                        Spacer(Modifier.height(4.dp))
                        LiveText(desc ?: " ", size = 14.sp, color = colors.TextSecondary, maxLines = if (full && !withRecent) 4 else 1, shadow = true)
                    }
                }
                // What's on next, on the right.
                if (nextTitle != null) {
                    Spacer(Modifier.width(24.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        LiveText("NEXT", size = 11.sp, weight = FontWeight.Bold, color = colors.TextTertiary)
                        LiveText(nextTitle, size = 15.sp, weight = FontWeight.Bold, color = colors.TextPrimary, modifier = Modifier.widthIn(max = 260.dp), shadow = true)
                        nextTime?.let { LiveText(it, size = 13.sp, color = colors.TextSecondary) }
                    }
                }
            }
            timeline?.let { t ->
                Spacer(Modifier.height(if (full) 10.dp else 10.dp))
                PanelTimelineBar(t, grabbed = focused == PanelControl.TIMELINE, compact = !full)
            }
            if (full) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    controls.filter { it != PanelControl.TIMELINE }.forEach { c ->
                        if (c == PanelControl.CC) Spacer(Modifier.weight(1f))
                        PanelButton(c, focused == c && recentFocus == null, paused, captionsOn, audioLabel, sizeLabel, sleepLabel, mode)
                    }
                }
            }
        }
    }
    if (withRecent) {
        Spacer(Modifier.height(12.dp))
        RecentRow(recent.orEmpty(), recentFocus, recentTitle, recentClear)
    }
    }
    } // else (the smaller panel)
}

/** A channel in the info panel's Recent row: what's on now and how far into it. */
internal data class PanelRecent(
    val channel: LiveChannel,
    val title: String?,
    val progress: Float?
)

/** Tiles in the Recent row: TV Guide, the channels, then Clear. */
internal fun recentTileCount(recent: List<PanelRecent>): Int = recent.size + 2

/** "RECENT", then TV Guide · the channels · Clear, scrolling to keep the highlight on screen. */
@Composable
private fun RecentRow(recent: List<PanelRecent>, focus: Int?, title: String, showClear: Boolean) {
    val colors = NuvioTheme.colors
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(focus) {
        val f = focus ?: return@LaunchedEffect
        val info = state.layoutInfo
        val fully = info.visibleItemsInfo.filter { it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }
        val first = fully.firstOrNull()?.index ?: 0
        val last = fully.lastOrNull()?.index ?: 0
        when {
            f < first -> state.animateScrollToItem(f)
            f > last -> state.animateScrollToItem((f - (fully.size - 1).coerceAtLeast(0)).coerceAtLeast(0))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
    // "RECENT" / "FAVORITES" up the side, so the row doesn't need a line of its own.
    Box(Modifier.width(14.dp).height(TILE_H), contentAlignment = Alignment.Center) {
        LiveText(
            title, size = 9.sp, weight = FontWeight.Bold, color = colors.TextTertiary,
            modifier = Modifier.wrapContentSize(unbounded = true).rotate(-90f),
            shadow = true
        )
    }
    Spacer(Modifier.width(4.dp))
    androidx.compose.foundation.lazy.LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        userScrollEnabled = false
    ) {
        item(key = "guide") {
            RecentTile(focused = focus == 0, width = 64.dp) {
                Icon(Icons.Default.GridView, contentDescription = null, tint = colors.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(2.dp))
                LiveText("TV Guide", size = 10.sp, weight = FontWeight.SemiBold, color = colors.TextPrimary)
            }
        }
        recent.forEachIndexed { i, r ->
            item(key = "ch:" + r.channel.key) {
                RecentTile(focused = focus == i + 1, width = 96.dp, progress = r.progress) {
                    if (!r.channel.logo.isNullOrBlank()) ChannelLogo(r.channel.logo, 17.dp)
                    else LiveText(r.channel.name, size = 12.sp, weight = FontWeight.Bold, color = colors.TextPrimary, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    LiveText(r.title ?: r.channel.name, size = 9.sp, color = colors.TextSecondary, maxLines = 1)
                }
            }
        }
        if (showClear) item(key = "clear") {
            RecentTile(focused = focus == recent.size + 1, width = 64.dp) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(2.dp))
                LiveText("Clear", size = 10.sp, weight = FontWeight.SemiBold, color = colors.TextSecondary)
            }
        }
    }
    }
}

private val TILE_H = 44.dp

@Composable
private fun RecentTile(
    focused: Boolean,
    width: androidx.compose.ui.unit.Dp,
    progress: Float? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val colors = NuvioTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .graphicsLayer { val s = if (focused) 1.06f else 1f; scaleX = s; scaleY = s }
            .width(width)
            .height(TILE_H)
            .clip(shape)
            .background(Color.White.copy(alpha = if (focused) 0.16f else 0.08f))
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.FocusRing else Color.White.copy(alpha = 0.08f), shape)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
        progress?.let { p ->
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(p.coerceIn(0f, 1f)).height(3.dp)
                    .background(colors.Secondary)
            )
        }
    }
}

@Composable
private fun PanelTimelineBar(t: PanelTimeline, grabbed: Boolean, compact: Boolean) {
    val colors = NuvioTheme.colors
    val accent = colors.Secondary
    val span = (t.endMs - t.startMs).coerceAtLeast(1L).toFloat()
    fun frac(ms: Long) = ((ms - t.startMs) / span).coerceIn(0f, 1f)
    val track = if (compact) 4.dp else 4.dp
    if (compact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveText(t.leftLabel, size = 13.sp, color = colors.TextSecondary)
            Spacer(Modifier.width(12.dp))
            BoxWithConstraints(modifier = Modifier.weight(1f).height(8.dp)) {
                val w = maxWidth
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(track).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f)))
                t.recordedUntilMs?.let { r ->
                    Box(Modifier.align(Alignment.CenterStart).width(w * frac(r)).height(track).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f)))
                }
                Box(Modifier.align(Alignment.CenterStart).width(w * frac(t.positionMs)).height(track).clip(RoundedCornerShape(2.dp)).background(accent))
                t.liveMs?.let { l ->
                    Box(Modifier.align(Alignment.CenterStart).offset(x = (w * frac(l)) - 1.dp).width(2.dp).fillMaxHeight().background(colors.Error))
                }
            }
            Spacer(Modifier.width(12.dp))
            LiveText(t.rightLabel, size = 13.sp, color = if (t.liveMs != null) colors.Error else colors.TextSecondary)
            Spacer(Modifier.width(14.dp))
            LiveText(t.status, size = 13.sp, color = if (t.statusLive) colors.Error else colors.TextSecondary)
        }
        return
    }
    Column {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(if (compact) 8.dp else 16.dp)) {
            val w = maxWidth
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(track).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f)))
            t.recordedUntilMs?.let { r ->
                Box(Modifier.align(Alignment.CenterStart).width(w * frac(r)).height(track).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f)))
            }
            Box(Modifier.align(Alignment.CenterStart).width(w * frac(t.positionMs)).height(track).clip(RoundedCornerShape(2.dp)).background(accent))
            t.liveMs?.let { l ->
                Box(Modifier.align(Alignment.CenterStart).offset(x = (w * frac(l)) - 1.dp).width(2.dp).fillMaxHeight().background(colors.Error))
            }
            if (!compact) {
                // The handle: bigger, with a ring, while the timeline is grabbed.
                val knob = if (grabbed) 18.dp else 12.dp
                Box(
                    Modifier.align(Alignment.CenterStart).offset(x = (w * frac(t.positionMs)) - knob / 2).size(knob)
                        .clip(CircleShape).background(Color.White)
                        .border(if (grabbed) 3.dp else 2.dp, if (grabbed) colors.FocusRing else accent, CircleShape)
                )
            }
        }
        if (!compact) Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            LiveText(t.leftLabel, size = 11.sp, color = colors.TextSecondary)
            Spacer(Modifier.weight(1f))
            if (grabbed) LiveText("◀ ▶ to move · OK to play/pause", size = 11.sp, color = colors.TextSecondary)
            Spacer(Modifier.weight(1f))
            LiveText(t.rightLabel, size = 11.sp, color = if (t.liveMs != null) colors.Error else colors.TextSecondary)
        }
    }
}

@Composable
private fun PanelButton(
    c: PanelControl,
    focused: Boolean,
    paused: Boolean,
    captionsOn: Boolean,
    audioLabel: String,
    sizeLabel: String,
    sleepLabel: String?,
    mode: PanelMode,
    /** The compact full panel's smaller round buttons. */
    small: Boolean = false
) {
    val colors = NuvioTheme.colors
    val idle = Color.White.copy(alpha = 0.12f)
    when (c) {
        PanelControl.TIMELINE -> Unit
        PanelControl.BACK, PanelControl.FORWARD, PanelControl.PLAY -> {
            val big = c == PanelControl.PLAY
            val icon: ImageVector = when (c) {
                PanelControl.BACK -> Icons.Default.Replay30
                PanelControl.FORWARD -> Icons.Default.Forward30
                else -> if (paused) Icons.Default.PlayArrow else Icons.Default.Pause
            }
            Box(
                modifier = Modifier
                    .size(if (small) (if (big) 32.dp else 30.dp) else if (big) 40.dp else 34.dp)
                    .clip(CircleShape)
                    .background(if (focused) colors.FocusRing else if (big) Color.White.copy(alpha = 0.22f) else idle),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (focused) colors.Background else colors.TextPrimary, modifier = Modifier.size(if (small) 17.dp else if (big) 22.dp else 18.dp))
            }
        }
        PanelControl.LIVE -> Pill(
            text = if (mode == PanelMode.CATCHUP) "Back to live" else "Go live",
            icon = null, focused = focused, dot = true
        )
        PanelControl.CC -> Pill(
            text = if (captionsOn) "CC on" else "CC off",
            icon = if (captionsOn) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
            focused = focused, active = captionsOn
        )
        PanelControl.AUDIO -> Pill(audioLabel, Icons.Default.VolumeUp, focused)
        PanelControl.SIZE -> Pill(sizeLabel, Icons.Default.AspectRatio, focused)
        PanelControl.SLEEP -> Pill(sleepLabel ?: "Sleep", Icons.Default.Bedtime, focused)
        PanelControl.MORE -> Pill("", Icons.Default.MoreHoriz, focused)
    }
}

@Composable
private fun Pill(
    text: String,
    icon: ImageVector?,
    focused: Boolean,
    active: Boolean = false,
    dot: Boolean = false
) {
    val colors = NuvioTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(
                when {
                    focused -> colors.FocusRing
                    active -> colors.Secondary.copy(alpha = 0.30f)
                    else -> Color.White.copy(alpha = 0.12f)
                }
            )
            .padding(horizontal = if (text.isEmpty()) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (focused) colors.Background else colors.TextPrimary
        if (dot) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(colors.Error))
            Spacer(Modifier.width(6.dp))
        }
        icon?.let {
            Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            if (text.isNotEmpty()) Spacer(Modifier.width(6.dp))
        }
        if (text.isNotEmpty()) LiveText(text, size = 12.sp, color = color, weight = FontWeight.Medium)
    }
}


/**
 * The full panel (OK while watching), kept small: channel line and clock along the top of the
 * poster; the title with its category and what's next on one line; the description (only
 * without the channel row); then play/pause, the time bar and the quick options on one line.
 * The poster is as tall as the text beside it, and the panel is the same height with or without
 * the channel row (the row's space goes to the description and a bigger poster).
 */
@Composable
private fun FullInfoPanel(
    channel: LiveChannel,
    mode: PanelMode,
    title: String,
    meta: String,
    description: String?,
    poster: String?,
    showPosters: Boolean,
    timeline: PanelTimeline?,
    paused: Boolean,
    controls: List<PanelControl>,
    focused: PanelControl?,
    captionsOn: Boolean,
    audioLabel: String,
    sizeLabel: String,
    sleepLabel: String?,
    clock: String,
    resolution: String?,
    showNumber: Boolean,
    nextTitle: String?,
    nextTime: String?,
    catchupAvailable: Boolean,
    recent: List<PanelRecent>?,
    recentFocus: Int?,
    recentTitle: String,
    recentClear: Boolean,
    modifier: Modifier,
    audioBadge: String? = null,
    background: String = "standard"
) {
    val colors = NuvioTheme.colors
    val accent = colors.Secondary
    val withRow = recent != null
    val density = androidx.compose.ui.platform.LocalDensity.current
    // The poster matches the height of the text beside it.
    var infoHeightPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val posterH = with(density) { infoHeightPx.toDp() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Fades in above the text (in the top padding), then stays dark behind it.
            .panelScrim(colors.Background, background, fade = 38.dp)
            .padding(start = 40.dp, end = 40.dp, top = 42.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            val posterShape = RoundedCornerShape(liveCorner())
            Box(
                modifier = Modifier
                    .width(posterH * (2f / 3f))
                    .height(posterH)
                    .clip(posterShape)
                    .background(colors.BackgroundCard.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), posterShape),
                contentAlignment = Alignment.Center
            ) {
                if (showPosters && !poster.isNullOrBlank()) {
                    coil3.compose.AsyncImage(
                        model = poster,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ChannelLogo(channel.logo, (posterH * 0.3f).coerceAtLeast(20.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged { sz ->
                        // A couple of pixels either way doesn't matter (and can't go back and forth).
                        if (kotlin.math.abs(sz.height - infoHeightPx) > 2) infoHeightPx = sz.height
                    }
            ) {
                // Channel line, with the clock on the right: level with the top of the poster.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!channel.logo.isNullOrBlank()) {
                        ChannelLogo(channel.logo, 18.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    LiveText(
                        (if (showNumber) "${channel.number} · " else "") + channel.name,
                        size = 12.sp, weight = FontWeight.SemiBold, color = colors.TextSecondary,
                        shadow = true
                    )
                    when (mode) {
                        PanelMode.LIVE -> { Spacer(Modifier.width(8.dp)); LiveTag("LIVE", LIVE_RED, Color.White) }
                        PanelMode.CATCHUP -> { Spacer(Modifier.width(8.dp)); LiveTag("ARCHIVE", accent, colors.OnSecondary) }
                        PanelMode.SHIFT -> { Spacer(Modifier.width(8.dp)); LiveTag("PAUSED LIVE", accent, colors.OnSecondary) }
                    }
                    resolution?.let {
                        Spacer(Modifier.width(6.dp))
                        LiveTag(it.uppercase(), Color.Transparent, colors.TextSecondary,
                            Modifier.border(1.dp, colors.Border, RoundedCornerShape(5.dp)))
                    }
                    audioBadge?.let {
                        Spacer(Modifier.width(6.dp))
                        LiveTag(it, Color.Transparent, colors.TextSecondary,
                            Modifier.border(1.dp, colors.Border, RoundedCornerShape(5.dp)))
                    }
                    if (catchupAvailable && mode == PanelMode.LIVE) {
                        Spacer(Modifier.width(6.dp))
                        LiveTag("CATCH-UP", Color.Transparent, colors.TextSecondary,
                            Modifier.border(1.dp, colors.Border, RoundedCornerShape(5.dp)))
                    }
                    Spacer(Modifier.weight(1f))
                    // Time left / how far behind live: up here, so the time bar keeps its length.
                    timeline?.let {
                        LiveText(
                            it.status, size = 12.sp, weight = FontWeight.SemiBold,
                            color = if (it.statusLive) colors.Error else accent,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                    if (clock.isNotEmpty()) LiveText(clock, size = 16.sp, weight = FontWeight.Bold, color = colors.TextPrimary, shadow = true)
                }
                Spacer(Modifier.height(2.dp))
                // Title · category, and what's next on the right.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveText(
                        title, size = 22.sp, weight = FontWeight.ExtraBold, maxLines = 1, marquee = true,
                        color = colors.TextPrimary, modifier = Modifier.weight(1f, fill = false),
                        shadow = true
                    )
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        LiveText(meta, size = 12.sp, color = colors.TextSecondary, maxLines = 1, modifier = Modifier.weight(1f, fill = false), shadow = true)
                    }
                    Spacer(Modifier.weight(0.001f))
                    if (nextTitle != null) {
                        Spacer(Modifier.width(16.dp))
                        LiveText("Next: ", size = 12.sp, color = colors.TextSecondary, shadow = true)
                        LiveText(nextTitle, size = 12.sp, weight = FontWeight.Bold, color = colors.TextPrimary, modifier = Modifier.widthIn(max = 220.dp), shadow = true)
                        nextTime?.let {
                            Spacer(Modifier.width(6.dp))
                            LiveText(it.substringBefore(" –"), size = 12.sp, color = colors.TextSecondary, shadow = true)
                        }
                    }
                }
                // The description: only without the channel row (the row takes its place).
                if (!withRow) {
                    Spacer(Modifier.height(6.dp))
                    LiveText(
                        description?.takeIf { it.isNotBlank() } ?: " ",
                        // Grows to fit (the panel and poster get taller), up to 5 lines.
                        size = 12.sp, color = colors.TextSecondary, maxLines = 5,
                        modifier = Modifier.heightIn(min = 48.dp),
                        shadow = true
                    )
                }
                Spacer(Modifier.height(if (withRow) 8.dp else 10.dp))
                // Play / pause (and skip) first, then the time bar, then the quick options.
                val buttons = controls.filter { it != PanelControl.TIMELINE }
                val transport = buttons.filter { it == PanelControl.BACK || it == PanelControl.PLAY || it == PanelControl.FORWARD }
                val rest = buttons - transport.toSet()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    transport.forEach { c ->
                        PanelButton(c, small = true, focused = focused == c && recentFocus == null, paused = paused, captionsOn = captionsOn,
                            audioLabel = audioLabel, sizeLabel = sizeLabel, sleepLabel = sleepLabel, mode = mode)
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.weight(1f)) {
                        timeline?.let { InlineTimeline(it, grabbed = focused == PanelControl.TIMELINE) }
                    }
                    Spacer(Modifier.width(4.dp))
                    rest.forEach { c ->
                        PanelButton(c, small = true, focused = focused == c && recentFocus == null, paused = paused, captionsOn = captionsOn,
                            audioLabel = audioLabel, sizeLabel = sizeLabel, sleepLabel = sleepLabel, mode = mode)
                    }
                }
            }
        }
        if (withRow) {
            Spacer(Modifier.height(8.dp))
            RecentRow(recent.orEmpty(), recentFocus, recentTitle, recentClear)
        }
    }
}

/** The time bar on the buttons' line: start, the bar (with a handle you can grab), end · time left. */
@Composable
private fun InlineTimeline(t: PanelTimeline, grabbed: Boolean) {
    val colors = NuvioTheme.colors
    val accent = colors.Secondary
    val span = (t.endMs - t.startMs).coerceAtLeast(1L).toFloat()
    fun frac(ms: Long) = ((ms - t.startMs) / span).coerceIn(0f, 1f)
    // Just the times at each end ("From" / "Live" dropped); how far in is shown by the clock.
    val left = t.leftLabel.removePrefix("From ")
    val right = t.rightLabel.removePrefix("Live ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        LiveText(left, size = 11.sp, color = colors.TextSecondary)
        Spacer(Modifier.width(8.dp))
        BoxWithConstraints(modifier = Modifier.weight(1f).height(16.dp)) {
            val w = maxWidth
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f)))
            t.recordedUntilMs?.let { r ->
                Box(Modifier.align(Alignment.CenterStart).width(w * frac(r)).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f)))
            }
            Box(Modifier.align(Alignment.CenterStart).width(w * frac(t.positionMs)).height(3.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            t.liveMs?.let { l ->
                Box(Modifier.align(Alignment.CenterStart).offset(x = (w * frac(l)) - 1.dp).width(2.dp).fillMaxHeight().background(colors.Error))
            }
            if (grabbed) {
                val knob = 14.dp
                Box(
                    Modifier.align(Alignment.CenterStart).offset(x = (w * frac(t.positionMs)) - knob / 2).size(knob)
                        .clip(CircleShape).background(Color.White)
                        .border(3.dp, colors.FocusRing, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        LiveText(
            if (grabbed) "◀ ▶ to move · OK play/pause" else right,
            size = 11.sp,
            color = if (t.liveMs != null && !grabbed) colors.Error else colors.TextSecondary
        )
    }
}

/**
 * The info panel's background in the theme's color: clear at the very top, dark by the time the
 * text starts (the fade sits in the top padding, [fade] tall), a touch darker toward the bottom.
 * [level]: "standard", "darker" or "solid" (Live TV settings, Info Panel Background).
 */
private fun Modifier.panelScrim(base: Color, level: String, fade: androidx.compose.ui.unit.Dp): Modifier =
    this.drawBehind {
        val (top, bottom) = when (level) {
            "solid" -> 1f to 1f
            "darker" -> 0.95f to 0.99f
            else -> 0.88f to 0.97f
        }
        val f = fade.toPx().coerceAtMost(size.height)
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to base.copy(alpha = 0f), 1f to base.copy(alpha = top), startY = 0f, endY = f
            ),
            size = androidx.compose.ui.geometry.Size(size.width, f)
        )
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to base.copy(alpha = top), 1f to base.copy(alpha = bottom), startY = f, endY = size.height
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, f),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - f)
        )
    }
