package com.jefe.autowarm

import kotlinx.coroutines.*
import kotlin.random.Random

class SessionManager(private val config: WarmConfig) {

    var scrolled = 0
        private set
    var liked = 0
        private set
    var running = false
        private set

    private var job: Job? = null
    private var videosSinceLike = 0
    private var nextLikeAt = safeRandomInterval()
    private val sessionMs = config.sessionMinutes * 60_000L

    var onScroll: (() -> Unit)? = null
    var onLike: (() -> Unit)? = null
    var onTick: ((remaining: Long) -> Unit)? = null
    var onStop: (() -> Unit)? = null

    fun start(scope: CoroutineScope) {
        if (running) return
        running = true
        scrolled = 0
        liked = 0
        videosSinceLike = 0
        nextLikeAt = safeRandomInterval()

        job = scope.launch {
            val endTime = System.currentTimeMillis() + sessionMs

            while (isActive && System.currentTimeMillis() < endTime) {
                onTick?.invoke(endTime - System.currentTimeMillis())

                // how long to watch this video
                val baseMin = config.watchMinSec * 1000L
                val baseMax = (config.watchMaxSec * 1000L).coerceAtLeast(baseMin + 500L)
                val watchMs = if (config.humanMode) {
                    // human mode: add random jitter ±20% on top
                    val base = Random.nextLong(baseMin, baseMax)
                    val jitter = (base * 0.20).toLong()
                    base + Random.nextLong(-jitter, jitter + 1)
                } else {
                    Random.nextLong(baseMin, baseMax)
                }
                delay(watchMs.coerceAtLeast(500L))

                if (!running) break

                // scroll to next video
                if (config.autoScroll) {
                    onScroll?.invoke()
                    scrolled++
                    videosSinceLike++

                    // human mode: small pause after scroll before anything else
                    if (config.humanMode) delay(Random.nextLong(300L, 900L))
                }

                // like logic
                if (config.autoLike && videosSinceLike >= nextLikeAt) {
                    if (Random.nextInt(100) < config.likeChancePct) {
                        // human mode: slight delay before tapping like
                        if (config.humanMode) delay(Random.nextLong(400L, 1200L))
                        onLike?.invoke()
                        liked++
                    }
                    videosSinceLike = 0
                    nextLikeAt = safeRandomInterval()
                }
            }

            running = false
            onStop?.invoke()
        }
    }

    fun stop() {
        running = false
        job?.cancel()
        job = null
        onStop?.invoke()
    }

    // coerce so nextInt never gets lo >= hi
    private fun safeRandomInterval(): Int {
        val lo = config.likeEveryMin.coerceAtLeast(1)
        val hi = config.likeEveryMax.coerceAtLeast(lo + 1)
        return Random.nextInt(lo, hi)
    }
}
