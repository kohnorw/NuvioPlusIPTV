package com.nuvio.tv.livetv.data

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Keeps Live TV and On Demand's heavy background jobs (playlist, guide and catalog downloads,
 * reading and saving them) out of the way while Nuvio's own player is on screen. On slower TV
 * boxes they competed with the video for the processor, storage and network, and movies and
 * episodes stuttered. Jobs already under way pause at their next step and carry on once the
 * player closes. Updates you start yourself (Update now) aren't held back.
 */
object BackgroundWork {
    /** Nuvio's player is open (set from the app's navigation). */
    val nuvioPlaying = MutableStateFlow(false)

    /**
     * Two low-priority threads for work nobody is waiting on: automatic playlist and guide
     * updates and the like. Android
     * gives the screen and the video first claim on the processor over these, so on a slower box
     * scrolling and playback stay smooth while they run (they just take a little longer).
     */
    val quiet: kotlinx.coroutines.CoroutineDispatcher by lazy {
        val count = java.util.concurrent.atomic.AtomicInteger()
        java.util.concurrent.Executors.newFixedThreadPool(2) { r ->
            Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                r.run()
            }, "nuvio-quiet-${count.incrementAndGet()}").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }

    /** Waits while Nuvio's player is open. */
    suspend fun awaitIdle() {
        if (nuvioPlaying.value) nuvioPlaying.first { !it }
    }
}
