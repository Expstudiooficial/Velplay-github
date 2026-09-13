package com.expstudio.facilitycore.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
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
    chapter: Int,
    startStage: Int,
    seed: Long,
    private val audio: Sfx
) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    val session = GameSession(chapter, startStage, seed, audio)

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
        controls.leftHanded = store.settings.leftHanded
        session.camera.shakeScale = store.settings.shakeAmount
        session.onHaptic = { /* wired by the activity */ }
        session.lightLift = store.settings.brightness
        session.particles.enabled = store.settings.effectsEnabled
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

    // ---- canvas ----------------------------------------------------------

    private var lockedHardware = false

    /**
     * Prefers a GPU canvas. The default SurfaceView lock path rasterises in
     * software, which the gradients, glows and full-screen passes this renderer
     * leans on would make expensive; on the hardware canvas they are close to
     * free. Falls back to the software path wherever that is not available.
     */
    private fun lockCanvas(): Canvas? {
        val surface = holder.surface
        if (surface == null || !surface.isValid) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val c = surface.lockHardwareCanvas()
                if (c != null) { lockedHardware = true; return c }
            } catch (t: Throwable) {
                // Fall through to the software canvas below.
            }
        }
        return try {
            lockedHardware = false
            holder.lockCanvas()
        } catch (t: Throwable) {
            null
        }
    }

    private fun unlockCanvas(canvas: Canvas) {
        try {
            if (lockedHardware) holder.surface.unlockCanvasAndPost(canvas)
            else holder.unlockCanvasAndPost(canvas)
        } catch (t: Throwable) {
            // The surface went away mid-frame; the next lock will fail cleanly.
        }
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

            val canvas = lockCanvas()
            if (canvas != null) {
                try {
                    synchronized(sessionLock) { render(canvas) }
                } finally {
                    unlockCanvas(canvas)
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
        val roll = controls.dodge.consumePress()
        val grab = controls.reach.consumePress()
        session.update(dt, controls.moveX, controls.sneakHeld, jump, use, roll, grab, controls.reach.held)

        controls.jump.enabled = session.player.controlEnabled
        controls.sneak.enabled = session.player.controlEnabled
        controls.interact.enabled = session.focusProp != null && session.player.controlEnabled
        controls.interact.label = if (session.focusLabel.isNotEmpty()) session.focusLabel else "USE"
        controls.dodge.visible = session.dodgeUnlocked
        controls.dodge.enabled = session.player.controlEnabled && session.player.dodgeReady
        controls.dodgeCharge = session.player.dodgeChargeFraction
        controls.reach.visible = session.reach.unlocked
        controls.reach.enabled = session.player.controlEnabled || session.reach.busy
        // Only lit when there is genuinely something to take hold of.
        controls.reachTargeted = session.reach.unlocked && session.reach.ready && session.reach.pick(session) != null

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
            if (session.cut == Cut.SMELTER_END && session.endingProgressCh2 >= 0.97f) {
                onQuit?.invoke()
                return@synchronized true
            }
            if (session.cut == Cut.CH3_ENDING && session.endingProgressCh3 >= 0.99f) {
                onQuit?.invoke()
                return@synchronized true
            }
            if (session.cut == Cut.CH4_ASCENT && session.endingProgressCh4 >= 0.97f) {
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
        } else if (session.cut == Cut.CH2_OPENING) {
            drawChapter2Opening(c, w, h)
        } else if (session.cut == Cut.SMELTER_END) {
            drawSmelterFinale(c, w, h)
        } else if (session.cut == Cut.CH3_LAVA) {
            drawChapter3Opening(c, w, h)
        } else if (session.cut == Cut.CH3_ENDING) {
            drawChapter3Ending(c, w, h)
        } else if (session.cut == Cut.CH4_CORE) {
            drawChapter3Ending(c, w, h)
        } else if (session.cut == Cut.CH4_ASCENT) {
            drawChapter4Ending(c, w, h)
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
        // Ask the running chapter, not Chapter 1: the ribbon was showing the
        // wrong chapter's objectives for the whole of Chapter 2.
        val objective = session.script.objectiveFor(session.stage)
        val size = h * 0.036f
        val tw = draw.measure(objective, size, true)
        val pad = h * 0.022f
        val rl = w * 0.5f - tw * 0.5f - pad
        val rr = w * 0.5f + tw * 0.5f + pad
        val rt = pad * 0.7f
        val rb = rt + size * 2f
        draw.round(c, rl, rt, rr, rb, size, Palette.withAlpha(Palette.VOID, 0.62f))
        draw.roundStroke(c, rl, rt, rr, rb, size, Palette.withAlpha(Palette.ACCENT, 0.30f), 2f)
        // Accent pips either side, so the ribbon reads as part of the facility.
        draw.circle(c, rl + pad * 0.55f, (rt + rb) * 0.5f, size * 0.16f, Palette.withAlpha(Palette.ACCENT, 0.8f))
        draw.circle(c, rr - pad * 0.55f, (rt + rb) * 0.5f, size * 0.16f, Palette.withAlpha(Palette.ACCENT, 0.8f))
        draw.textCentered(c, objective, w * 0.5f, (rt + rb) * 0.5f, size, Palette.withAlpha(Palette.TEXT, 0.95f), true)

        // Pause button.
        draw.circle(c, pauseButton.cx, pauseButton.cy, pauseButton.radius, Palette.withAlpha(Palette.VOID, 0.45f))
        draw.circleStroke(c, pauseButton.cx, pauseButton.cy, pauseButton.radius, Palette.withAlpha(Palette.TEXT_DIM, 0.7f), 3f)
        val bar = pauseButton.radius * 0.36f
        draw.rect(c, pauseButton.cx - bar * 0.6f, pauseButton.cy - bar, pauseButton.cx - bar * 0.15f, pauseButton.cy + bar, Palette.TEXT)
        draw.rect(c, pauseButton.cx + bar * 0.15f, pauseButton.cy - bar, pauseButton.cx + bar * 0.6f, pauseButton.cy + bar, Palette.TEXT)

        drawInventory(c, h)
        if (session.maxHealth > 0) drawHealth(c, w, h)
        drawEvacClock(c, w, h)
        if (session.hurtFlash > 0f) {
            draw.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.BAD, 0.22f * session.hurtFlash / 0.45f))
        }
        if (session.crouchHint && store.settings.hintsEnabled) drawCrouchHint(c, w, h)
        if (session.dialogueVisible) drawDialogue(c, w, h)
    }

    /**
     * Chapter 3's evacuation clock. Only drawn while the run out is live, and
     * only as a bar — a number would invite arithmetic, and the point is panic.
     */
    private fun drawEvacClock(c: Canvas, w: Float, h: Float) {
        val script = session.script as? Chapter3Script ?: return
        if (session.stage != Stage3.FINAL_RUN) return
        val left = script.sprintRemaining
        val f = MathX.clamp(left / Chapter3Script.SPRINT_SHOWN_MAX, 0f, 1f)
        val bw = w * 0.36f
        val bl = w * 0.5f - bw * 0.5f
        val bt = h * 0.135f
        val bh = h * 0.016f
        draw.round(c, bl, bt, bl + bw, bt + bh, bh * 0.5f, Palette.withAlpha(Palette.VOID, 0.7f))
        val tint = if (f < 0.28f) Palette.BAD else Palette.WARN
        draw.round(c, bl, bt, bl + bw * f, bt + bh, bh * 0.5f, tint)
        // It flickers when it is nearly gone, which is the only warning given.
        if (f < 0.28f) {
            val flash = 0.35f + 0.4f * sin(session.time * 14f)
            draw.roundStroke(c, bl, bt, bl + bw, bt + bh, bh * 0.5f, Palette.withAlpha(Palette.BAD, flash), 3f)
        }
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

    /**
     * Called out hard: a ring pulsing on the SNEAK button itself plus a line of
     * text, so the answer is both named and pointed at.
     */
    private fun drawCrouchHint(c: Canvas, w: Float, h: Float) {
        val pulse = 0.5f + 0.5f * sin(session.time * 7f)
        val b = controls.sneak
        draw.circleStroke(c, b.cx, b.cy, b.radius * (1.25f + pulse * 0.18f),
            Palette.withAlpha(Palette.WARN, 0.35f + pulse * 0.45f), 5f)
        draw.circle(c, b.cx, b.cy, b.radius, Palette.withAlpha(Palette.WARN, 0.18f * pulse))

        val text = "HOLD SNEAK TO FIT THROUGH"
        val size = h * 0.044f
        val tw = draw.measure(text, size, true)
        val pad = h * 0.026f
        val cx = w * 0.5f
        val t = h * 0.145f
        draw.round(c, cx - tw * 0.5f - pad, t, cx + tw * 0.5f + pad, t + size * 2.1f,
            size, Palette.withAlpha(Palette.VOID, 0.72f))
        draw.roundStroke(c, cx - tw * 0.5f - pad, t, cx + tw * 0.5f + pad, t + size * 2.1f,
            size, Palette.withAlpha(Palette.WARN, 0.4f + pulse * 0.4f), 2.5f)
        draw.textCentered(c, text, cx, t + size * 1.05f, size,
            Palette.withAlpha(Palette.TEXT, 0.85f + pulse * 0.15f), true)
    }

    /** Pips for a set-piece fight. Only drawn while a fight is running. */
    private fun drawHealth(c: Canvas, w: Float, h: Float) {
        val r = h * 0.021f
        val gap = r * 2.9f
        val total = (session.maxHealth - 1) * gap
        val cx = w * 0.5f - total * 0.5f
        val cy = h * 0.115f
        for (i in 0 until session.maxHealth) {
            val x = cx + i * gap
            val alive = i < session.health
            draw.circle(c, x, cy, r * 1.35f, Palette.withAlpha(Palette.VOID, 0.55f))
            draw.circleStroke(c, x, cy, r, Palette.withAlpha(if (alive) Palette.BAD else Palette.TEXT_DIM, 0.85f), 3f)
            if (alive) {
                draw.circle(c, x, cy, r * 0.62f, Palette.BAD)
                draw.glow(c, x, cy, r * 2.4f, Palette.BAD, 0.5f)
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

    private val elevatorScene = ElevatorScene()

    /**
     * Closing scene. The car drops away and something lands on the roof hard
     * enough to cave it in; Chapter 1 cuts to black the moment the plate gives.
     */
    private fun drawEnding(c: Canvas, w: Float, h: Float) {
        val p = session.endingProgress
        elevatorScene.update(p)
        if (elevatorScene.impactCue) {
            audio.play(Sfx.Id.THUD)
            audio.play(Sfx.Id.SCREAM)
            session.onHaptic?.invoke(220)
        }
        elevatorScene.draw(c, draw, w, h, session.time, p, ElevatorScene.Mode.CH1_ENDING)

        if (p >= 0.99f) {
            draw.textCentered(c, "CHAPTER 1 COMPLETE", w * 0.5f, h * 0.44f, h * 0.075f, Palette.TEXT, true)
            draw.textCentered(c, "Subfloor 1 unlocked in world creation", w * 0.5f, h * 0.54f, h * 0.036f, Palette.TEXT_DIM)
            val hint = 0.45f + 0.35f * sin(session.time * 3f)
            draw.textCentered(c, "tap to return to the menu", w * 0.5f, h * 0.66f, h * 0.034f,
                Palette.withAlpha(Palette.ACCENT, hint), true)
        }
    }

    /**
     * Chapter 2 opens on the same moment Chapter 1 closed on, and keeps going:
     * the hands take the torn plate and haul it wide enough to come through.
     */
    private fun drawChapter2Opening(c: Canvas, w: Float, h: Float) {
        val p = session.endingProgressCh2
        elevatorScene.update(p)
        if (elevatorScene.impactCue) {
            audio.play(Sfx.Id.THUD)
            audio.play(Sfx.Id.SCREAM)
            session.onHaptic?.invoke(220)
        }
        elevatorScene.draw(c, draw, w, h, session.time, p, ElevatorScene.Mode.CH2_OPENING)
        if (p < 0.12f) {
            draw.textCentered(c, "CHAPTER 2", w * 0.5f, h * 0.16f, h * 0.062f,
                Palette.withAlpha(Palette.TEXT, 1f - p / 0.12f), true)
            draw.textCentered(c, "SUBFLOOR 1", w * 0.5f, h * 0.23f, h * 0.030f,
                Palette.withAlpha(Palette.ACCENT, 1f - p / 0.12f), true)
        }
    }

    /** The closing shot: watched through the pour control window. */
    private fun drawSmelterFinale(c: Canvas, w: Float, h: Float) {
        val p = session.endingProgressCh2
        smelterScene.draw(c, draw, w, h, session.time, p)
        if (smelterScene.consumeImpact()) {
            audio.play(Sfx.Id.SCREAM)
            audio.play(Sfx.Id.RUMBLE)
            session.onHaptic?.invoke(200)
        }
        if (p >= 0.97f) {
            draw.textCentered(c, "CHAPTER 2 COMPLETE", w * 0.5f, h * 0.44f, h * 0.075f, Palette.TEXT, true)
            draw.textCentered(c, "It went into the pour. Ren is still down there.",
                w * 0.5f, h * 0.54f, h * 0.034f, Palette.TEXT_DIM)
            val hint = 0.45f + 0.35f * sin(session.time * 3f)
            draw.textCentered(c, "tap to return to the menu", w * 0.5f, h * 0.66f, h * 0.034f,
                Palette.withAlpha(Palette.ACCENT, hint), true)
        }
    }

    private val smelterScene = SmelterScene()

    /**
     * Chapter 3 opens on Chapter 2's last shot, replayed from the deck above the
     * pour — the same fall, the same window, one floor further up.
     */
    private fun drawChapter3Opening(c: Canvas, w: Float, h: Float) {
        val p = session.endingProgressCh3
        smelterScene.draw(c, draw, w, h, session.time, p)
        if (smelterScene.consumeImpact()) {
            audio.play(Sfx.Id.SCREAM)
            audio.play(Sfx.Id.RUMBLE)
            session.onHaptic?.invoke(200)
        }
        if (p < 0.14f) {
            val a = 1f - p / 0.14f
            draw.textCentered(c, "CHAPTER 3", w * 0.5f, h * 0.16f, h * 0.062f, Palette.withAlpha(Palette.TEXT, a), true)
            draw.textCentered(c, "SUBFLOOR 2", w * 0.5f, h * 0.23f, h * 0.030f, Palette.withAlpha(Palette.ACCENT, a), true)
        }
    }

    private val coreScene = CoreScene()

    /**
     * The last shot: they arrive at a run, take the jump that is not there any
     * more, and the well has them. Staged in [CoreScene] rather than here — it
     * is the chapter's closing image and deserved more than four rectangles.
     */
    private fun drawChapter3Ending(c: Canvas, w: Float, h: Float) {
        val p = session.endingProgressCh3
        coreScene.draw(c, draw, w, h, session.time, p)
        if (coreScene.consumeImpact()) {
            audio.play(Sfx.Id.IMPACT, 0.95f)
            audio.play(Sfx.Id.SCREAM, 0.8f)
            session.onHaptic?.invoke(180)
        }
    }

    private val escapeScene = EscapeScene()

    /** The only ending in the game with daylight in it. */
    private fun drawChapter4Ending(c: Canvas, w: Float, h: Float) {
        escapeScene.draw(c, draw, w, h, session.time, session.endingProgressCh4)
        if (escapeScene.consumeKick()) {
            audio.play(Sfx.Id.IMPACT, 1f)
            audio.play(Sfx.Id.SHUTTER, 0.7f)
            session.onHaptic?.invoke(120)
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
