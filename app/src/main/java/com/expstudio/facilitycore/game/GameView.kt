package com.expstudio.facilitycore.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.core.Controls
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import com.expstudio.facilitycore.core.TouchButton
import com.expstudio.facilitycore.save.WorldStore
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Hosts the render/update loop on its own thread and draws everything that is
 * not part of the world: the HUD, the pause sheet, the jumpscare and the
 * chapter's closing scene.
 */
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    private val store: WorldStore,
    startStage: Int,
    seed: Long,
    private val audio: Sfx
) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    val session = GameSession(startStage, seed, audio)

    private val draw = Draw()
    private val controls = Controls()
    private val pauseButton = TouchButton("II")

    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var paused = false

    private var widthPx = 0
    private var heightPx = 0

    private var fps = 0f
    private var fpsAccum = 0f
    private var fpsFrames = 0

    /** Elapsed play time in this sitting, handed back to the activity on exit. */
    var sessionSeconds = 0f
        private set

    private var pauseSheet = false
    private var completeShown = false
    private var overlayWasOpen = false

    /**
     * Touch arrives on the UI thread while the loop steps and renders on its
     * own. Everything that reads or mutates the session is taken under this
     * lock rather than trying to make the session itself thread safe.
     */
    private val sessionLock = Any()

    /** Hooks the activity installs. */
    var onQuit: (() -> Unit)? = null
    var onComplete: (() -> Unit)? = null

    init {
        holder.addCallback(this)
        isFocusable = true
        controls.uiScale = store.settings.controlScale
        session.onHaptic = { /* wired by the activity */ }
    }

    // ---- surface lifecycle ----------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this, "facility-core-loop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        widthPx = width
        heightPx = height
        session.camera.resize(width, height)
        controls.layout(width, height)
        session.overlay?.layout(width, height)
        pauseButton.radius = MathX.min(width * 0.5f, height.toFloat()) * 0.052f
        pauseButton.cx = width - pauseButton.radius * 1.5f
        pauseButton.cy = pauseButton.radius * 1.5f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
    }

    fun stopLoop() {
        running = false
        val t = thread
        thread = null
        try { t?.join(1200) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
    }

    fun onResumed() {
        paused = false
        if (store.settings.ambienceEnabled) audio.startAmbience()
    }

    fun onPaused() {
        paused = true
        pauseSheet = true
        controls.reset()
        audio.stopAmbience()
    }

    /** Opened by the activity's back gesture. */
    fun openPauseSheet() {
        pauseSheet = true
        controls.reset()
    }

    // ---- loop ------------------------------------------------------------

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            // Clamp dt so a stall never teleports the player through geometry.
            var dt = ((now - last) / 1_000_000_000.0).toFloat()
            last = now
            if (dt > 0.05f) dt = 0.05f
            if (dt <= 0f) dt = 0.0001f

            synchronized(sessionLock) {
                if (!paused && !pauseSheet) {
                    step(dt)
                    sessionSeconds += dt
                } else {
                    controls.update(dt)
                }
            }

            fpsAccum += dt
            fpsFrames++
            if (fpsAccum >= 0.5f) {
                fps = fpsFrames / fpsAccum
                fpsAccum = 0f
                fpsFrames = 0
            }

            val canvas = try { holder.lockCanvas() } catch (t: Throwable) { null }
            if (canvas != null) {
                try {
                    synchronized(sessionLock) { render(canvas) }
                } finally {
                    try { holder.unlockCanvasAndPost(canvas) } catch (t: Throwable) { /* surface gone */ }
                }
            }

            // Cap at ~60 Hz; the surface has no vsync callback on this path.
            val spent = (System.nanoTime() - now) / 1_000_000L
            val sleep = 16L - spent
            if (sleep > 0) {
                try { Thread.sleep(sleep) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); return }
            }
        }
    }

    private fun step(dt: Float) {
        // A puzzle opening mid-drag must not leave the stick or a button stuck.
        val overlayOpen = session.overlay != null
        if (overlayOpen && !overlayWasOpen) controls.reset()
        overlayWasOpen = overlayOpen

        controls.update(dt)
        val jump = controls.jump.consumePress()
        val use = controls.interact.consumePress()
        session.update(dt, controls.moveX, controls.sneakHeld, jump, use)

        controls.jump.enabled = session.player.controlEnabled
        controls.sneak.enabled = session.player.controlEnabled
        controls.interact.enabled = session.focusProp != null && session.player.controlEnabled
        controls.interact.label = if (session.focusLabel.isNotEmpty()) session.focusLabel else "USE"

        if (session.completed && !completeShown) {
            completeShown = true
            onComplete?.invoke()
        }
    }

    // ---- input -----------------------------------------------------------

    override fun onTouchEvent(e: MotionEvent): Boolean = synchronized(sessionLock) {
        performClickIfNeeded(e)
        val overlay = session.overlay
        if (overlay != null) {
            overlay.layout(widthPx, heightPx)
            return@synchronized overlay.onTouch(e)
        }
        if (pauseSheet) {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) handlePauseSheetTouch(e.x, e.y)
            return@synchronized true
        }
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            // The closing card is the only screen with no pause button.
            if (session.cut == Cut.ENDING && session.endingProgress >= 0.99f) {
                onQuit?.invoke()
                return@synchronized true
            }
            if (hypot(e.x - pauseButton.cx, e.y - pauseButton.cy) <= pauseButton.radius * 1.4f) {
                audio.play(Sfx.Id.CLICK)
                pauseSheet = true
                controls.reset()
                return@synchronized true
            }
            // Only a line that holds control swallows the tap; otherwise the
            // player would lose a jump to a passing remark.
            if (session.dialogueBlocking && session.tapDialogue()) return@synchronized true
            if (tapKeyPackIcon(e.x, e.y)) return@synchronized true
        }
        controls.onTouch(e)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun performClickIfNeeded(e: MotionEvent) {
        if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
    }

    private fun tapKeyPackIcon(x: Float, y: Float): Boolean {
        if (!session.hasKeyPack || session.keyPackRepaired) return false
        val r = heightPx * 0.055f
        val cx = r * 1.6f
        val cy = heightPx - r * 1.6f
        if (hypot(x - cx, y - cy) > r * 1.5f) return false
        audio.play(Sfx.Id.CLICK)
        session.openRepairPuzzle()
        session.overlay?.layout(widthPx, heightPx)
        return true
    }

    private fun handlePauseSheetTouch(x: Float, y: Float) {
        val bw = widthPx * 0.34f
        val bh = heightPx * 0.13f
        val cx = widthPx * 0.5f
        val first = heightPx * 0.36f
        val gap = bh * 1.25f
        for (i in 0..2) {
            val t = first + gap * i
            if (x in (cx - bw / 2)..(cx + bw / 2) && y in t..(t + bh)) {
                audio.play(Sfx.Id.CLICK)
                when (i) {
                    0 -> { pauseSheet = false; if (store.settings.ambienceEnabled) audio.startAmbience() }
                    1 -> { pauseSheet = false; session.restart(session.stage) }
                    2 -> onQuit?.invoke()
                }
                return
            }
        }
    }

    // ---- rendering -------------------------------------------------------

    private fun render(c: Canvas) {
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        if (w <= 0f || h <= 0f) return

        if (session.cut == Cut.ENDING) {
            drawEnding(c, w, h)
        } else {
            session.render(c, draw, w, h)
            drawHud(c, w, h)
        }

        session.overlay?.let { o ->
            o.layout(widthPx, heightPx)
            o.draw(c, draw)
        }

        if (session.cut == Cut.DEATH) drawJumpscare(c, w, h)

        if (session.fade > 0.002f) {
            draw.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.VOID, session.fade))
        }

        if (pauseSheet) drawPauseSheet(c, w, h)

        if (store.settings.showFps) {
            draw.textLeft(c, "${fps.toInt()} fps", 14f, h - 14f, h * 0.032f, Palette.withAlpha(Palette.TEXT_DIM, 0.8f))
        }
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        controls.draw(c, draw)

        // Objective ribbon.
        val objective = Stage.objectiveFor(session.stage)
        val size = h * 0.036f
        val tw = draw.measure(objective, size, true)
        val pad = h * 0.022f
        draw.round(c, w * 0.5f - tw * 0.5f - pad, pad * 0.7f, w * 0.5f + tw * 0.5f + pad, pad * 0.7f + size * 2f,
            size, Palette.withAlpha(Palette.VOID, 0.55f))
        draw.textCentered(c, objective, w * 0.5f, pad * 0.7f + size, size, Palette.withAlpha(Palette.TEXT, 0.92f), true)

        // Pause button.
        draw.circle(c, pauseButton.cx, pauseButton.cy, pauseButton.radius, Palette.withAlpha(Palette.VOID, 0.45f))
        draw.circleStroke(c, pauseButton.cx, pauseButton.cy, pauseButton.radius, Palette.withAlpha(Palette.TEXT_DIM, 0.7f), 3f)
        val bar = pauseButton.radius * 0.36f
        draw.rect(c, pauseButton.cx - bar * 0.6f, pauseButton.cy - bar, pauseButton.cx - bar * 0.15f, pauseButton.cy + bar, Palette.TEXT)
        draw.rect(c, pauseButton.cx + bar * 0.15f, pauseButton.cy - bar, pauseButton.cx + bar * 0.6f, pauseButton.cy + bar, Palette.TEXT)

        drawInventory(c, h)
        if (session.dialogueVisible) drawDialogue(c, w, h)
    }

    private fun drawInventory(c: Canvas, h: Float) {
        if (!session.hasKeyPack) return
        val r = h * 0.055f
        val cx = r * 1.6f
        val cy = h - r * 1.6f
        val tint = if (session.keyPackRepaired) Palette.ACCENT else Palette.BAD
        draw.circle(c, cx, cy, r, Palette.withAlpha(Palette.VOID, 0.5f))
        draw.circleStroke(c, cx, cy, r, Palette.withAlpha(tint, 0.85f), 3f)
        draw.round(c, cx - r * 0.34f, cy - r * 0.44f, cx + r * 0.34f, cy + r * 0.44f, r * 0.16f,
            Palette.withAlpha(tint, 0.30f))
        draw.rect(c, cx - r * 0.22f, cy - r * 0.30f, cx + r * 0.22f, cy - r * 0.16f, Palette.withAlpha(tint, 0.9f))
        if (!session.keyPackRepaired) {
            val pulse = 0.5f + 0.5f * sin(session.time * 6f)
            draw.textCentered(c, "REPAIR", cx, cy + r * 1.45f, h * 0.028f, Palette.withAlpha(Palette.BAD, pulse), true)
        } else if (session.keyPackHasElevator) {
            draw.circle(c, cx + r * 0.62f, cy - r * 0.62f, r * 0.2f, Palette.GOOD)
        }

        // Data shards.
        if (session.shards > 0) {
            for (i in 0 until session.shards) {
                val sx = cx + r * 2.3f + i * r * 1.1f
                draw.poly(
                    c,
                    floatArrayOf(sx, cy - r * 0.42f, sx + r * 0.32f, cy, sx, cy + r * 0.42f, sx - r * 0.32f, cy),
                    Palette.withAlpha(if (session.circuitAssembled) Palette.GOOD else Palette.ACCENT, 0.95f)
                )
            }
        }
    }

    private fun drawDialogue(c: Canvas, w: Float, h: Float) {
        val boxW = MathX.min(w * 0.78f, h * 1.7f)
        val l = (w - boxW) * 0.5f
        val size = h * 0.042f
        val lines = draw.wrap(session.dialogueText.ifEmpty { " " }, size, boxW - h * 0.08f)
        val boxH = size * 1.45f * lines.size + h * 0.055f
        val t = h * 0.66f
        draw.panel(c, l, t, l + boxW, t + boxH, h * 0.022f)
        var y = t + h * 0.028f + size
        for (line in lines) {
            draw.textLeft(c, line, l + h * 0.04f, y, size, Palette.TEXT)
            y += size * 1.45f
        }
        if (session.dialogueBlocking) {
            val hint = 0.4f + 0.35f * sin(session.time * 4f)
            draw.textCentered(c, "tap to continue", l + boxW - h * 0.12f, t + boxH - h * 0.018f,
                h * 0.026f, Palette.withAlpha(Palette.TEXT_DIM, hint))
        }
    }

    private fun drawJumpscare(c: Canvas, w: Float, h: Float) {
        val p = MathX.clamp(session.cutTime / 0.45f, 0f, 1f)
        val zoom = MathX.lerp(0.35f, 1.35f, MathX.smoothStep(p))
        draw.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.VOID, 0.55f + 0.45f * p))
        draw.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.BAD, 0.22f * session.deathFlash))

        val cx = w * 0.5f + sin(session.cutTime * 41f) * w * 0.012f
        val cy = h * 0.48f + sin(session.cutTime * 33f) * h * 0.012f
        val r = h * 0.42f * zoom

        draw.circle(c, cx, cy, r, Palette.MONSTER)
        // Split-open jaw.
        draw.poly(
            c,
            floatArrayOf(cx - r * 0.42f, cy + r * 0.28f, cx + r * 0.42f, cy + r * 0.28f, cx, cy + r * 0.95f),
            Palette.withAlpha(Palette.MONSTER_CRACK, 0.55f)
        )
        var i = -2
        while (i <= 2) {
            val tx = cx + i * r * 0.16f
            draw.poly(
                c,
                floatArrayOf(tx - r * 0.05f, cy + r * 0.30f, tx + r * 0.05f, cy + r * 0.30f, tx, cy + r * 0.58f),
                Palette.withAlpha(Palette.TEXT, 0.85f)
            )
            i++
        }
        // Eyes.
        val gap = r * 0.38f
        val ey = cy - r * 0.16f
        draw.glow(c, cx - gap, ey, r * 0.5f, Palette.MONSTER_EYE, 1.4f)
        draw.glow(c, cx + gap, ey, r * 0.5f, Palette.MONSTER_EYE, 1.4f)
        draw.circle(c, cx - gap, ey, r * 0.13f, Palette.MONSTER_EYE)
        draw.circle(c, cx + gap, ey, r * 0.13f, Palette.MONSTER_EYE)
        // Rot seams.
        draw.line(c, cx - r * 0.7f, cy - r * 0.5f, cx - r * 0.2f, cy + r * 0.1f, Palette.withAlpha(Palette.MONSTER_CRACK, 0.8f), h * 0.008f)
        draw.line(c, cx + r * 0.65f, cy - r * 0.42f, cx + r * 0.18f, cy + r * 0.05f, Palette.withAlpha(Palette.MONSTER_CRACK, 0.7f), h * 0.006f)

        val fadeOut = MathX.clamp((session.cutTime - 1.1f) / 0.8f, 0f, 1f)
        if (fadeOut > 0f) draw.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.VOID, fadeOut))
    }

    /**
     * Closing scene: the car drops away, and something lands on its roof.
     * Drawn entirely in screen space so it can be framed exactly.
     */
    private fun drawEnding(c: Canvas, w: Float, h: Float) {
        val p = session.endingProgress
        draw.rect(c, 0f, 0f, w, h, Palette.VOID)

        val carW = MathX.min(w * 0.42f, h * 0.85f)
        val carH = h * 0.62f
        val cx = w * 0.5f
        val carT = h * 0.22f
        val carB = carT + carH
        val shakeY = if (p > 0.62f) sin((p - 0.62f) * 140f) * h * 0.012f * ((p - 0.62f) / 0.38f) else 0f

        // Shaft walls sliding upward sell the descent.
        val bandH = h * 0.18f
        var y = -((p * 7.5f * bandH) % bandH)
        while (y < h) {
            draw.rect(c, 0f, y, w, y + bandH * 0.10f, Palette.withAlpha(Palette.WALL_LIT, 0.45f))
            y += bandH
        }

        // Car.
        draw.rect(c, cx - carW * 0.5f, carT + shakeY, cx + carW * 0.5f, carB + shakeY,
            Palette.mix(Palette.WALL, Palette.ACCENT_DIM, 0.18f))
        draw.roundStroke(c, cx - carW * 0.5f, carT + shakeY, cx + carW * 0.5f, carB + shakeY, h * 0.01f,
            Palette.TRIM, 4f)
        draw.rect(c, cx - carW * 0.5f, carB - h * 0.02f + shakeY, cx + carW * 0.5f, carB + shakeY, Palette.TRIM)
        draw.glow(c, cx, carT + h * 0.05f + shakeY, carW * 0.45f, Palette.ACCENT, 0.5f)

        // The white figure, standing very still.
        val feetY = carB - h * 0.035f + shakeY
        val fh = h * 0.30f
        val fx = cx
        draw.circle(c, fx, feetY, fh * 0.10f, Palette.withAlpha(Palette.VOID, 0.5f))
        draw.line(c, fx, feetY - fh * 0.44f, fx - fh * 0.07f, feetY, Palette.PLAYER_SHADE, fh * 0.055f)
        draw.line(c, fx, feetY - fh * 0.44f, fx + fh * 0.07f, feetY, Palette.PLAYER, fh * 0.055f)
        draw.poly(
            c,
            floatArrayOf(
                fx - fh * 0.11f, feetY - fh * 0.80f,
                fx + fh * 0.11f, feetY - fh * 0.80f,
                fx + fh * 0.08f, feetY - fh * 0.44f,
                fx - fh * 0.08f, feetY - fh * 0.44f
            ),
            Palette.PLAYER
        )
        draw.circle(c, fx, feetY - fh * 0.90f, fh * 0.13f, Palette.PLAYER)

        // The landing.
        if (p > GameSession.LANDING_CUE) {
            val k = MathX.clamp((p - GameSession.LANDING_CUE) / 0.14f, 0f, 1f)
            val roofY = carT + shakeY
            val mh = h * 0.34f * MathX.lerp(0.5f, 1f, k)
            session.monster.drawAt(c, draw, cx, roofY + mh, mh, session.time)
            if (k >= 1f) {
                draw.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.BAD, 0.10f * (0.5f + 0.5f * sin(p * 90f))))
            }
        }

        // Screaming face fills the frame, then black.
        if (p > 0.76f) {
            val k = MathX.clamp((p - 0.76f) / 0.16f, 0f, 1f)
            val r = h * 0.55f * k
            draw.circle(c, cx, h * 0.5f, r, Palette.withAlpha(Palette.MONSTER, k))
            val gap = r * 0.36f
            draw.glow(c, cx - gap, h * 0.46f, r * 0.45f, Palette.MONSTER_EYE, 1.5f * k)
            draw.glow(c, cx + gap, h * 0.46f, r * 0.45f, Palette.MONSTER_EYE, 1.5f * k)
        }
        if (p > 0.88f) {
            draw.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.VOID, MathX.clamp((p - 0.88f) / 0.10f, 0f, 1f)))
        }
        if (p >= 0.99f) {
            draw.textCentered(c, "CHAPTER 1 COMPLETE", w * 0.5f, h * 0.44f, h * 0.075f, Palette.TEXT, true)
            draw.textCentered(c, "Subfloor 1 unlocked in world creation", w * 0.5f, h * 0.54f, h * 0.036f, Palette.TEXT_DIM)
            val hint = 0.45f + 0.35f * sin(session.time * 3f)
            draw.textCentered(c, "tap to return to the menu", w * 0.5f, h * 0.66f, h * 0.034f,
                Palette.withAlpha(Palette.ACCENT, hint), true)
        }
    }

    private fun drawPauseSheet(c: Canvas, w: Float, h: Float) {
        draw.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.VOID, 0.78f))
        draw.textCentered(c, "PAUSED", w * 0.5f, h * 0.22f, h * 0.08f, Palette.TEXT, true)
        draw.textCentered(c, Stage.objectiveFor(session.stage), w * 0.5f, h * 0.29f, h * 0.034f, Palette.TEXT_DIM)

        val labels = arrayOf("RESUME", "RESTART CHECKPOINT", "QUIT TO MENU")
        val bw = w * 0.34f
        val bh = h * 0.13f
        val cx = w * 0.5f
        val first = h * 0.36f
        val gap = bh * 1.25f
        for (i in labels.indices) {
            val t = first + gap * i
            val tint = if (i == 2) Palette.BAD else Palette.ACCENT
            draw.round(c, cx - bw / 2, t, cx + bw / 2, t + bh, bh * 0.28f, Palette.withAlpha(tint, 0.12f))
            draw.roundStroke(c, cx - bw / 2, t, cx + bw / 2, t + bh, bh * 0.28f, Palette.withAlpha(tint, 0.75f), 3f)
            draw.textCentered(c, labels[i], cx, t + bh * 0.5f, bh * 0.32f, Palette.TEXT, true)
        }
    }
}
