package com.expstudio.facilitycore.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.game.GameView
import com.expstudio.facilitycore.game.Stage
import com.expstudio.facilitycore.save.WorldSave
import com.expstudio.facilitycore.save.WorldStore

/** Runs one world. Owns the audio engine and writes progress back to the store. */
class GameActivity : AppCompatActivity() {

    private lateinit var store: WorldStore
    private lateinit var audio: Sfx
    private var view: GameView? = null
    private var world: WorldSave? = null
    private var vibrator: Vibrator? = null
    private var savedSeconds = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WorldStore(this)

        val id = intent.getStringExtra(EXTRA_WORLD_ID)
        val loaded = id?.let { store.get(it) }
        if (loaded == null) {
            Toast.makeText(this, "That world could not be loaded.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        world = loaded

        audio = Sfx().apply {
            enabled = store.settings.sfxEnabled
            ambienceEnabled = store.settings.ambienceEnabled
        }
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        // The seed is stable per world, so a world's puzzles never reshuffle
        // between sessions.
        val seed = loaded.id.hashCode().toLong() * 31L + loaded.createdAt

        val gameView = GameView(this, store, loaded.chapter, loaded.stage, seed, audio)
        gameView.session.restoreWaypoint(
            loaded.waypointRoom.takeIf { it.isNotEmpty() }, loaded.waypointStage
        )
        gameView.session.onStageChanged = { stage -> persist(stage) }
        gameView.session.onHaptic = { ms -> vibrate(ms) }
        gameView.onQuit = { finishSession() }
        gameView.onComplete = { onChapterComplete() }
        view = gameView
        setContentView(gameView)
        UiKit.goFullscreen(this)
    }

    override fun onResume() {
        super.onResume()
        UiKit.goFullscreen(this)
        // onCreate bails out early when the world cannot be loaded, so the
        // audio engine may legitimately not exist yet.
        if (!::audio.isInitialized) return
        audio.enabled = store.settings.sfxEnabled
        audio.ambienceEnabled = store.settings.ambienceEnabled
        view?.onResumed()
    }

    override fun onPause() {
        super.onPause()
        if (!::audio.isInitialized) return
        view?.onPaused()
        persist(view?.session?.stage ?: world?.stage ?: 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        view?.stopLoop()
        if (::audio.isInitialized) audio.release()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Back opens the in-game pause sheet rather than dropping the run.
        val v = view
        if (v == null) { super.onBackPressed(); return }
        v.openPauseSheet()
    }

    private fun persist(stage: Int) {
        val w = world ?: return
        val v = view
        w.stage = maxOf(w.stage, stage)
        w.checkpoint = v?.session?.script?.checkpointFor(w.stage) ?: Stage.checkpointFor(w.stage)
        w.lastPlayedAt = System.currentTimeMillis()
        // The corridor to come back to after a death, so quitting and resuming
        // does not undo the ground already walked.
        v?.session?.let {
            w.waypointRoom = it.waypointRoom ?: ""
            w.waypointStage = it.waypointStage
        }
        if (v != null) {
            // Only bank the time that has not been written yet.
            val delta = v.sessionSeconds - savedSeconds
            if (delta > 0f) {
                w.playSeconds += delta.toLong()
                savedSeconds = v.sessionSeconds
            }
        }
        val finish = v?.session?.script?.completeStage ?: Stage.COMPLETE
        if (w.stage >= finish) w.completed = true
        store.update(w)
    }

    private fun onChapterComplete() {
        val w = world ?: return
        w.completed = true
        persist(view?.session?.script?.completeStage ?: Stage.COMPLETE)
        if (w.chapter == 1) store.settings.chapter2Unlocked = true
        val message = if (w.chapter >= 2) "Chapter 2 complete."
        else "Chapter 1 complete — Chapter 2 worlds unlocked."
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private fun finishSession() {
        persist(view?.session?.stage ?: 0)
        runOnUiThread { finish() }
    }

    private fun vibrate(ms: Int) {
        if (!store.settings.hapticsEnabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms.toLong())
            }
        } catch (t: Throwable) {
            // Haptics are cosmetic; a device that refuses them is not an error.
        }
    }

    companion object {
        const val EXTRA_WORLD_ID = "world_id"
    }
}
