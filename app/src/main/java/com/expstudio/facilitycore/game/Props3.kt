package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.core.Art
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Camera
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.sin

/**
 * The workhorse of Chapter 3's corridors: a wall terminal that opens one puzzle
 * and, on a solve, releases a named door in the same room.
 *
 * Which puzzle it opens is part of the terminal rather than the room, so a
 * composed corridor can be given a job without the composer knowing anything
 * about puzzles.
 */
class DataTerminal(
    box: Box,
    val id: String,
    private val kind: Kind,
    private val unlocksDoor: String,
    private val seedSalt: Long = 0L
) : Prop(box) {

    enum class Kind { FLOW, BREAKERS, WIRING }

    var solved = false

    /**
     * Optional precondition. The coolant gallery's terminal is dead until its
     * three valves are open, and a terminal that simply refuses says that far
     * better than a locked door somewhere else would.
     */
    var requires: ((GameSession) -> Boolean)? = null
    var blockedMessage = "Dead. Something upstream of this isn't open."

    override val reach: Float = 1.5f

    override fun interactLabel(g: GameSession): String = if (solved) "" else "ACCESS"

    override fun onInteract(g: GameSession) {
        if (solved) return
        val gate = requires
        if (gate != null && !gate(g)) {
            g.say(blockedMessage, blocking = false)
            g.playSound(Sfx.Id.DENY)
            return
        }
        val seed = g.puzzleSeed + seedSalt
        val overlay = when (kind) {
            Kind.FLOW -> FlowPuzzle(seed)
            Kind.BREAKERS -> BreakerPuzzle(seed)
            Kind.WIRING -> WiringPuzzle(seed)
        }
        g.openOverlay(overlay) { success ->
            if (success) {
                solved = true
                g.playSound(Sfx.Id.POWER, 0.8f)
                g.releaseDoor(unlocksDoor)
            }
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        val tint = if (solved) Palette.GOOD else Palette.WARN
        Art.plate(
            c, d, l, t, r, b,
            Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.4f),
            Palette.mix(Palette.WALL, Palette.VOID, 0.45f),
            Palette.mix(Palette.TRIM, tint, 0.3f),
            cam.scale, box.l
        )
        // A screen full of nothing useful, which is most of what this place has.
        val sl = l + cam.s(0.12f); val sr = r - cam.s(0.12f)
        val st = t + cam.s(0.12f); val sb = b - cam.s(0.3f)
        d.rect(c, sl, st, sr, sb, Palette.withAlpha(Palette.VOID, 0.85f))
        var i = 0
        while (i < 5) {
            val y = st + (sb - st) * (0.15f + i * 0.18f)
            val wobble = 0.35f + 0.6f * ((sin(g.time * 1.7f + i * 2.1f + box.l) + 1f) * 0.5f)
            d.rect(c, sl + cam.s(0.06f), y, sl + (sr - sl - cam.s(0.12f)) * wobble, y + cam.s(0.045f),
                Palette.withAlpha(tint, if (solved) 0.7f else 0.45f))
            i++
        }
        val pulse = if (solved) 1f else 0.45f + 0.45f * sin(g.time * 3.4f)
        d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(1.1f), tint, 0.5f * pulse)
    }
}

/**
 * A job that can be finished at a run. One press, a beat of work, done.
 *
 * These exist for the final sprint, where stopping to solve a grid would kill
 * the pace — the puzzle is remembering which of them you have already hit while
 * something is closing on you.
 */
class SnapValve(box: Box, val id: String, private val group: String) : Prop(box) {

    var done = false
    private var work = 0f

    override val reach: Float = 1.5f

    override fun interactLabel(g: GameSession): String = if (done) "" else "PULL"

    override fun onInteract(g: GameSession) {
        if (done || work > 0f) return
        work = 0.0001f
        g.playSound(Sfx.Id.CLANK, 0.6f)
    }

    override fun update(g: GameSession, dt: Float) {
        if (done || work <= 0f) return
        work += dt
        if (work >= WORK_SECONDS) {
            done = true
            g.playSound(Sfx.Id.CONFIRM, 0.8f)
            g.onValvePulled(this, group)
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        val tint = if (done) Palette.GOOD else Palette.WARN
        d.round(c, l, t, r, b, cam.s(0.07f), Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.25f))
        val mx = (l + r) * 0.5f
        val my = (t + b) * 0.5f
        val turn = if (done) 1f else (work / WORK_SECONDS).coerceIn(0f, 1f)
        val ang = turn * 1.35f
        val arm = cam.s(0.34f)
        d.line(c, mx - arm * kotlin.math.cos(ang), my - arm * kotlin.math.sin(ang),
            mx + arm * kotlin.math.cos(ang), my + arm * kotlin.math.sin(ang), tint, cam.s(0.09f))
        d.circle(c, mx, my, cam.s(0.10f), Palette.TRIM)
        d.glow(c, mx, my, cam.s(0.8f), tint, if (done) 0.8f else 0.45f + 0.35f * sin(g.time * 5f))
    }

    private companion object { const val WORK_SECONDS = 0.55f }
}

/**
 * One of the archive's index nodes. They have to be touched in the order the
 * core calls out, which would be trivial if the room were empty and the thing
 * in it were not swinging at you.
 */
class ArchiveNode(box: Box, val index: Int) : Prop(box) {

    var lit = false
    var wrongFlash = 0f

    override val reach: Float = 1.4f

    override fun interactLabel(g: GameSession): String = if (lit) "" else "INDEX ${index + 1}"

    override fun onInteract(g: GameSession) {
        if (lit) return
        g.onArchiveNode(this)
    }

    override fun update(g: GameSession, dt: Float) {
        if (wrongFlash > 0f) wrongFlash -= dt
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        val tint = when {
            wrongFlash > 0f -> Palette.BAD
            lit -> Palette.GOOD
            else -> Palette.ACCENT_DIM
        }
        d.round(c, l, t, r, b, cam.s(0.08f), Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.35f))
        d.roundStroke(c, l, t, r, b, cam.s(0.08f), Palette.withAlpha(tint, 0.8f), 3f)
        val mx = (l + r) * 0.5f
        val my = (t + b) * 0.5f
        // The index number, because remembering an order needs something to hold.
        d.textCentered(c, "${index + 1}", mx, my + cam.s(0.12f), cam.s(0.55f),
            Palette.withAlpha(if (lit) Palette.GOOD else Palette.TEXT, 0.9f), true)
        val pulse = if (lit) 1f else 0.35f + 0.3f * sin(g.time * 2.2f + index)
        d.glow(c, mx, my, cam.s(1.0f), tint, 0.55f * pulse)
    }
}

/**
 * The archive core. Calls a sequence of index nodes, one wave at a time, each
 * wave longer than the last. Finishing all of them opens the core and gives up
 * what is inside it.
 */
class ArchiveCore(box: Box) : Prop(box) {

    /** The sequence currently being called. */
    var sequence: IntArray = IntArray(0)

    /** How many of the current sequence have been touched correctly. */
    var progress = 0

    var wave = 0
    var open = false

    /** Counts down while the core is showing the sequence back to the player. */
    var showTime = 0f

    override val reach: Float = 1.6f

    override fun interactLabel(g: GameSession): String = if (open) "TAKE" else ""

    override fun onInteract(g: GameSession) {
        if (!open) return
        g.onArchiveCoreTaken()
    }

    override fun update(g: GameSession, dt: Float) {
        if (showTime > 0f) showTime -= dt
    }

    /** Which index is lit right now while the core plays the sequence back. */
    private fun shownIndex(): Int {
        if (showTime <= 0f || sequence.isEmpty()) return -1
        val elapsed = SHOW_PER * sequence.size - showTime
        val i = (elapsed / SHOW_PER).toInt()
        return if (i in sequence.indices) sequence[i] else -1
    }

    fun beginShow() { showTime = SHOW_PER * sequence.size }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        val mx = (l + r) * 0.5f
        val my = (t + b) * 0.5f
        d.round(c, l, t, r, b, cam.s(0.12f), Palette.mix(Palette.WALL, Palette.VOID, 0.4f))
        d.roundStroke(c, l, t, r, b, cam.s(0.12f),
            Palette.withAlpha(if (open) Palette.GOOD else Palette.ACCENT, 0.7f), 4f)

        if (open) {
            // The drawer is out and there is a hand in it.
            d.rect(c, l + cam.s(0.2f), my - cam.s(0.1f), r - cam.s(0.2f), my + cam.s(0.5f), Palette.VOID)
            d.glow(c, mx, my, cam.s(2.0f), Palette.GOOD, 0.9f)
            return
        }

        // Sequence readout: one pip per step, the called one burning.
        val shown = shownIndex()
        val n = sequence.size.coerceAtLeast(1)
        var i = 0
        while (i < sequence.size) {
            val px = MathX.lerp(l + cam.s(0.35f), r - cam.s(0.35f), (i + 0.5f) / n)
            val hit = i < progress
            val calling = shown >= 0 && sequence[i] == shown &&
                ((SHOW_PER * sequence.size - showTime) / SHOW_PER).toInt() == i
            val tint = when {
                hit -> Palette.GOOD
                calling -> Palette.WARN
                else -> Palette.TEXT_DIM
            }
            d.circle(c, px, my, cam.s(if (calling) 0.22f else 0.15f), tint)
            if (calling) d.glow(c, px, my, cam.s(0.9f), Palette.WARN, 1f)
            // The index it is asking for, so the sequence is readable and not memorised blind.
            d.textCentered(c, "${sequence[i] + 1}", px, my + cam.s(0.62f), cam.s(0.34f),
                Palette.withAlpha(tint, 0.85f), true)
            i++
        }
        d.textCentered(c, "WAVE ${wave + 1} / $WAVES", mx, t - cam.s(0.35f), cam.s(0.36f),
            Palette.withAlpha(Palette.ACCENT, 0.85f), true)
        val pulse = 0.4f + 0.3f * sin(g.time * 2.6f)
        d.glow(c, mx, my, cam.s(2.2f), Palette.ACCENT, 0.35f * pulse)
    }

    companion object {
        const val WAVES = 3
        const val SHOW_PER = 0.62f
    }
}

/**
 * The lasers behind the observation glass. Inert until every emitter in the
 * room has been armed; then they fire, and what they make is the core.
 */
class LaserEmitter(box: Box, val id: String) : Prop(box) {

    var armed = false
    var firing = false
    private var heat = 0f

    override val reach: Float = 1.5f

    override fun interactLabel(g: GameSession): String = if (armed) "" else "ARM"

    override fun onInteract(g: GameSession) {
        if (armed) return
        armed = true
        g.playSound(Sfx.Id.CONFIRM, 0.85f)
        g.onEmitterArmed(this)
    }

    override fun update(g: GameSession, dt: Float) {
        heat = MathX.approach(heat, if (firing) 1f else if (armed) 0.35f else 0f, 1.6f, dt)
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        d.round(c, l, t, r, b, cam.s(0.06f), Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.2f))
        val tint = if (firing) Palette.BAD else if (armed) Palette.WARN else Palette.TEXT_DIM
        d.rect(c, l + cam.s(0.08f), t + cam.s(0.08f), r - cam.s(0.08f), t + cam.s(0.2f), tint)
        d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(1.2f * (0.4f + heat)), tint, 0.5f + heat * 0.6f)
    }
}

/**
 * The well the lasers pour into, and the last thing two monsters ever see.
 */
class CoreWell(box: Box) : Prop(box) {

    /** 0 = a dry pit, 1 = a sun with a lid on it. */
    var charge = 0f

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        val mx = (l + r) * 0.5f
        d.rect(c, l, t, r, b, Palette.VOID)
        if (charge <= 0.01f) {
            d.rect(c, l, t, r, t + cam.s(0.12f), Palette.withAlpha(Palette.TRIM, 0.5f))
            return
        }
        // Heat haze in bands rather than one flat fill, so it moves.
        var i = 0
        while (i < 7) {
            val f = i / 7f
            val yy = MathX.lerp(b, t, f)
            val wob = sin(g.time * (2.2f + i * 0.4f) + i) * cam.s(0.35f) * charge
            val a = charge * (1f - f * 0.7f)
            d.rect(c, l + wob, yy - cam.s(0.4f), r + wob, yy,
                Palette.withAlpha(Palette.mix(Palette.WARN, Palette.BAD, f), 0.45f * a))
            i++
        }
        d.glow(c, mx, t, cam.s(6f * charge), Palette.WARN, 1.1f * charge)
        d.glow(c, mx, t, cam.s(2.6f * charge), Palette.TEXT, 0.7f * charge)
    }
}

/**
 * The super database. Not a puzzle in itself — the reward for one — and the
 * only thing in the facility that will open the last door.
 */
class SuperDatabase(box: Box) : Prop(box) {

    var available = false
    var taken = false
    private var upload = 0f

    override val reach: Float = 1.7f

    override fun interactLabel(g: GameSession): String = when {
        taken -> ""
        !available -> ""
        upload > 0f -> "HOLD"
        else -> "DOWNLOAD"
    }

    override fun onInteract(g: GameSession) {
        if (taken || !available || upload > 0f) return
        upload = 0.0001f
        g.playSound(Sfx.Id.CHIME, 0.9f)
    }

    override fun update(g: GameSession, dt: Float) {
        if (taken || upload <= 0f) return
        if (!playerInReach(g)) { upload = 0f; g.say("Lost the link. Stay on it.", blocking = false); return }
        upload += dt
        if (upload >= UPLOAD_SECONDS) {
            taken = true
            g.onSuperDatabaseTaken()
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        val mx = (l + r) * 0.5f
        val tint = when {
            taken -> Palette.GOOD
            available -> Palette.ACCENT
            else -> Palette.TEXT_DIM
        }
        d.round(c, l, t, r, b, cam.s(0.1f), Palette.mix(Palette.WALL, Palette.VOID, 0.35f))
        d.roundStroke(c, l, t, r, b, cam.s(0.1f), Palette.withAlpha(tint, 0.75f), 4f)
        // Rack rows, climbing as the download runs.
        val rows = 9
        var i = 0
        while (i < rows) {
            val f = i / rows.toFloat()
            val y = MathX.lerp(b - cam.s(0.2f), t + cam.s(0.2f), f)
            val on = taken || (available && f < upload / UPLOAD_SECONDS)
            d.rect(c, l + cam.s(0.16f), y - cam.s(0.06f), r - cam.s(0.16f), y,
                Palette.withAlpha(if (on) tint else Palette.PANEL_EDGE, 0.85f))
            i++
        }
        if (upload > 0f && !taken) {
            d.textCentered(c, "${((upload / UPLOAD_SECONDS) * 100f).toInt()}%", mx, t - cam.s(0.35f),
                cam.s(0.42f), Palette.ACCENT, true)
        }
        d.glow(c, mx, (t + b) * 0.5f, cam.s(2.4f), tint, if (taken) 0.9f else 0.45f)
    }

    private companion object { const val UPLOAD_SECONDS = 6.5f }
}
