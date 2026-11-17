package com.jefe.autowarm

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jefe.autowarm.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var stats: StatsStore
    private var session: SessionManager? = null
    private var sessionDurationMin = 5

    // ─────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        stats = StatsStore(this)

        setupSessionLength()
        setupWatchTime()
        setupLikeEvery()
        setupLikeChance()
        setupStartStop()
        setupResetStats()
        resetClock()
        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        // If accessibility was just enabled, update button state
        if (session?.running == true) {
            b.btnStart.text = "Stop"
            b.btnStart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF3B30"))
        }
    }

    // ─────────────────────────────────────────────
    // SESSION LENGTH
    // ─────────────────────────────────────────────
    private fun setupSessionLength() {
        val options = listOf(b.btn5m to 5, b.btn10m to 10, b.btn15m to 15, b.btn30m to 30)

        fun select(min: Int) {
            sessionDurationMin = min
            options.forEach { (btn, v) ->
                val active = v == min
                btn.backgroundTintList = ColorStateList.valueOf(
                    if (active) Color.BLACK else Color.parseColor("#EEEEEE")
                )
                btn.setTextColor(if (active) Color.WHITE else Color.BLACK)
            }
            b.tvSessionLabel.text = "$min min"
            resetClock()
        }

        options.forEach { (btn, v) -> btn.setOnClickListener { select(v) } }
        select(5)
    }

    // ─────────────────────────────────────────────
    // WATCH TIME
    // ─────────────────────────────────────────────
    private fun setupWatchTime() {
        fun updateLabel() {
            val min = b.watchMin.text.toString().toIntOrNull() ?: 1
            val max = b.watchMax.text.toString().toIntOrNull() ?: 1
            b.watchTimeLabel.text = "$min – $max sec"
        }
        b.watchMinMinus.setOnClickListener {
            b.watchMin.text = ((b.watchMin.text.toString().toIntOrNull() ?: 1) - 1)
                .coerceAtLeast(1).toString(); updateLabel()
        }
        b.watchMinPlus.setOnClickListener {
            b.watchMin.text = ((b.watchMin.text.toString().toIntOrNull() ?: 1) + 1)
                .toString(); updateLabel()
        }
        b.watchMaxMinus.setOnClickListener {
            b.watchMax.text = ((b.watchMax.text.toString().toIntOrNull() ?: 1) - 1)
                .coerceAtLeast(1).toString(); updateLabel()
        }
        b.watchMaxPlus.setOnClickListener {
            b.watchMax.text = ((b.watchMax.text.toString().toIntOrNull() ?: 1) + 1)
                .toString(); updateLabel()
        }
    }

    // ─────────────────────────────────────────────
    // LIKE EVERY
    // ─────────────────────────────────────────────
    private fun setupLikeEvery() {
        fun updateLabel() {
            val min = b.likeMin.text.toString().toIntOrNull() ?: 2
            val max = b.likeMax.text.toString().toIntOrNull() ?: 5
            b.likeEveryLabel.text = "$min – $max videos"
        }
        b.likeMinMinus.setOnClickListener {
            b.likeMin.text = ((b.likeMin.text.toString().toIntOrNull() ?: 2) - 1)
                .coerceAtLeast(1).toString(); updateLabel()
        }
        b.likeMinPlus.setOnClickListener {
            b.likeMin.text = ((b.likeMin.text.toString().toIntOrNull() ?: 2) + 1)
                .toString(); updateLabel()
        }
        b.likeMaxMinus.setOnClickListener {
            b.likeMax.text = ((b.likeMax.text.toString().toIntOrNull() ?: 5) - 1)
                .coerceAtLeast(1).toString(); updateLabel()
        }
        b.likeMaxPlus.setOnClickListener {
            b.likeMax.text = ((b.likeMax.text.toString().toIntOrNull() ?: 5) + 1)
                .toString(); updateLabel()
        }
    }

    // ─────────────────────────────────────────────
    // LIKE CHANCE SEEKBAR
    // ─────────────────────────────────────────────
    private fun setupLikeChance() {
        b.likeChanceSlider.max = 100
        b.likeChanceSlider.progress = 100
        b.likeChanceLabel.text = "100%"
        b.likeChanceSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                b.likeChanceLabel.text = "$p%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    // ─────────────────────────────────────────────
    // START / STOP
    // ─────────────────────────────────────────────
    private fun setupStartStop() {
        b.btnStart.setOnClickListener {
            if (session?.running == true) {
                stopSession()
                return@setOnClickListener
            }

            // 1. check accessibility service is on
            if (!isAccessibilityEnabled()) {
                AlertDialog.Builder(this)
                    .setTitle("Enable Accessibility")
                    .setMessage("Jefe AutoWarm needs accessibility permission to scroll and like YouTube.\n\nTap OK to open settings, find \"Jefe AutoWarm\" and enable it.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@setOnClickListener
            }

            // 2. open YouTube if not already in foreground
            launchYouTube()

            // 3. small delay so YouTube loads, then start
            b.btnStart.isEnabled = false
            b.btnStart.text = "Launching…"
            b.root.postDelayed({ startSession() }, 2000L)
        }
    }

    /**
     * Launch YouTube to the home/subscriptions feed.
     * Falls back to Play Store if YouTube isn't installed.
     */
    private fun launchYouTube() {
        val pm = packageManager
        val ytIntent = pm.getLaunchIntentForPackage("com.google.android.youtube")
        if (ytIntent != null) {
            ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(ytIntent)
        } else {
            Toast.makeText(this, "YouTube not found — install it first", Toast.LENGTH_LONG).show()
        }
    }

    private fun startSession() {
        b.btnStart.isEnabled = true

        val svc = AutoWarmService.instance ?: run {
            b.btnStart.text = "Start"
            Toast.makeText(this, "Enable accessibility service first", Toast.LENGTH_LONG).show()
            return
        }

        val watchMin = (b.watchMin.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
        val watchMax = (b.watchMax.text.toString().toIntOrNull() ?: 1).coerceAtLeast(watchMin)
        val likeMin  = (b.likeMin.text.toString().toIntOrNull() ?: 2).coerceAtLeast(1)
        val likeMax  = (b.likeMax.text.toString().toIntOrNull() ?: 5).coerceAtLeast(likeMin + 1)

        val config = WarmConfig(
            sessionMinutes = sessionDurationMin,
            watchMinSec    = watchMin,
            watchMaxSec    = watchMax,
            likeEveryMin   = likeMin,
            likeEveryMax   = likeMax,
            likeChancePct  = b.likeChanceSlider.progress,
            autoScroll     = b.toggleAutoScroll.isChecked,
            autoLike       = b.toggleAutoLike.isChecked,
            humanMode      = b.toggleHumanMode.isChecked,
            nicheKeyword   = b.etKeyword.text?.toString()?.trim() ?: ""
        )

        // start foreground service so session survives screen dim
        WarmupService.start(this)

        session = SessionManager(config).also { mgr ->
            mgr.onScroll = {
                svc.doScroll {
                    runOnUiThread { b.tvScrolled.text = mgr.scrolled.toString() }
                }
            }
            mgr.onLike = {
                svc.doLike {
                    runOnUiThread { b.tvLiked.text = mgr.liked.toString() }
                }
            }
            mgr.onTick = { rem -> runOnUiThread { updateClock(rem) } }
            mgr.onStop = { runOnUiThread { finishSession() } }
            mgr.start(lifecycleScope)
        }

        b.btnStart.text = "Stop"
        b.btnStart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF3B30"))
        b.tvClockStatus.text = "RUNNING"
        b.statusDot.setBackgroundResource(R.drawable.dot_running)
    }

    private fun stopSession() {
        val mgr = session ?: return
        // persist stats before stop clears counters
        commitStats(mgr.scrolled, mgr.liked)
        mgr.stop()
        session = null
        finishSessionUI()
    }

    /** Called when session ends naturally (timer) or is stopped manually. */
    private fun finishSession() {
        val mgr = session
        if (mgr != null) {
            commitStats(mgr.scrolled, mgr.liked)
            session = null
        }
        finishSessionUI()
    }

    private fun finishSessionUI() {
        WarmupService.stop(this)
        b.btnStart.text = "Start"
        b.btnStart.isEnabled = true
        b.btnStart.backgroundTintList = ColorStateList.valueOf(Color.BLACK)
        b.tvScrolled.text = "0"
        b.tvLiked.text = "0"
        b.statusDot.setBackgroundResource(R.drawable.dot_idle)
        resetClock()
        refreshStats()
    }

    private fun commitStats(scrolled: Int, liked: Int) {
        stats.addScrolled(scrolled)
        stats.addLiked(liked)
        stats.addSession()
    }

    // ─────────────────────────────────────────────
    // STATS
    // ─────────────────────────────────────────────
    private fun setupResetStats() {
        b.btnResetStats.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset stats?")
                .setMessage("This will clear your total scrolled, liked and session counts.")
                .setPositiveButton("Reset") { _, _ ->
                    stats.reset()
                    refreshStats()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun refreshStats() {
        b.tvTotalScrolled.text = stats.totalScrolled.toString()
        b.tvTotalLiked.text    = stats.totalLiked.toString()
        b.tvSessions.text      = stats.sessions.toString()
    }

    // ─────────────────────────────────────────────
    // CLOCK
    // ─────────────────────────────────────────────
    private fun updateClock(remainingMs: Long) {
        val ms   = remainingMs.coerceAtLeast(0L)
        val mins = TimeUnit.MILLISECONDS.toMinutes(ms)
        val secs = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        b.tvClock.text = "%02d:%02d".format(mins, secs)
        b.tvClockStatus.text = "RUNNING"
    }

    private fun resetClock() {
        b.tvClock.text = "%02d:00".format(sessionDurationMin)
        b.tvClockStatus.text = "READY"
    }

    // ─────────────────────────────────────────────
    // ACCESSIBILITY CHECK
    // ─────────────────────────────────────────────
    private fun isAccessibilityEnabled(): Boolean {
        val cn   = "$packageName/${AutoWarmService::class.java.canonicalName}"
        val flat = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return TextUtils.SimpleStringSplitter(':')
            .also { it.setString(flat) }
            .any { it.equals(cn, ignoreCase = true) }
    }
}
