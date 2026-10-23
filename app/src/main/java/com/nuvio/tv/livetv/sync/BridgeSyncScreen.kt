package com.nuvio.tv.livetv.sync

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

/**
 * Shown once after signing in, before Home: "Syncing" with a progress bar while the bridge's
 * playlists and favorites come over. Goes straight on when the account has no IPTV, after a
 * few seconds if the bridge can't be reached, or right away with Skip.
 */
@Composable
fun BridgeSyncScreen(
    sync: LiveTvBridgeSync,
    onDone: () -> Unit
) {
    var target by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf("Connecting to Nuvio Bridge") }
    var finished by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "bridgeSync"
    )
    val skipFocus = remember { FocusRequester() }
    // Accounts without IPTV move on in well under a second: don't flash the screen at them.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(700); visible = true; runCatching { skipFocus.requestFocus() } }

    LaunchedEffect(Unit) {
        val result = sync.firstSync { text, fraction -> message = text; target = fraction }
        if (finished) return@LaunchedEffect
        when (result) {
            // No IPTV on this account: straight to Home.
            LiveTvBridgeSync.FirstSyncResult.NO_IPTV -> Unit
            LiveTvBridgeSync.FirstSyncResult.DONE -> { message = "All set"; target = 1f; delay(400) }
            LiveTvBridgeSync.FirstSyncResult.FAILED -> {
                message = "Couldn't reach Nuvio Bridge. It'll keep trying in the background."
                target = 1f
                delay(1_500)
            }
        }
        if (!finished) { finished = true; onDone() }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center
    ) {
        if (visible) Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Syncing", fontSize = 30.sp, color = NuvioTheme.colors.TextPrimary)
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NuvioTheme.colors.Border)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(NuvioTheme.colors.TextPrimary)
                )
            }
            Text(message, fontSize = 15.sp, color = NuvioTheme.colors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { if (!finished) { finished = true; onDone() } },
                modifier = Modifier.focusRequester(skipFocus)
            ) { Text("Skip") }
        }
    }
}
