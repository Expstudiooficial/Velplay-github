package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.core.Art
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Camera
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.cos
import kotlin.math.sin

/**
 * Props introduced in Chapter 2: battery cubes and their sockets, key-operated
 * switches, the lift's repair tasks, the ceiling grabbers, and the smelter.
 */

/** A battery cube. Carried in both hands and slotted into a socket. */
class PowerCube(box: Box, val id: String) : Prop(box) {
    var held = false
    var consumed = false

    override fun interactLabel(g: GameSession): String = when {
        consumed || held -> ""
        g.carriedCube != null || g.carriedCable != null -> ""
        else -> "TAKE CUBE"
    }

    override fun onInteract(g: GameSession) {
        if (consumed || held) return
        held = true
        g.player.carrying = true
        g.onCubeTaken(this)
    }

    override fun update(g: GameSession, dt: Float) {
        if (held) {
            val hx = g.player.x + g.player.facing * 0.30f
            val hy = g.player.y - g.player.height * 0.55f
            box.set(hx - 0.27f, hy - 0.27f, hx + 0.27f, hy + 0.27f)
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        if (consumed) return
        val bob = if (held) 0f else sin(g.time * 2.3f) * cam.s(0.05f)
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t) + bob; val b = cam.sy(box.b) + bob
        d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(1.1f), Palette.ACCENT, 0.7f)
        Art.plate(
            c, d, l, t, r, b,
            Palette.mix(Palette.WALL_LIT, Palette.ACCENT_DIM, 0.35f),
            Palette.mix(Palette.WALL, Palette.VOID, 0.4f),
            Palette.mix(Palette.TRIM, Palette.ACCENT, 0.4f),
            cam.scale, box.l, bolts = true
        )
        // Charge window across the face.
        val pulse = 0.55f + 0.45f * sin(g.time * 3.1f)
        d.round(
            c, l + cam.s(0.07f), t + cam.s(0.07f), r - cam.s(0.07f), b - cam.s(0.07f),
            cam.s(0.05f), Palette.withAlpha(Palette.ACCENT, 0.25f + 0.45f * pulse)
        )
        for (i in 0 until 3) {
            val y = t + (b - t) * (0.3f + i * 0.2f)
            d.line(c, l + cam.s(0.12f), y, r - cam.s(0.12f), y,
                Palette.withAlpha(Palette.TEXT, 0.35f * pulse), cam.s(0.03f))
        }
    }
}

/** The hole a cube goes into. Unlocks whatever it is wired to. */
class CubeSocket(box: Box, val id: String, val unlocksDoor: String = "") : Prop(box) {
    var filled = false
    var seatTime = 0f

    override val reach: Float = 1.5f

    override fun interactLabel(g: GameSession): String = when {
        filled -> ""
        g.carriedCube != null -> "INSERT"
        else -> ""
    }

    override fun onInteract(g: GameSession) {
        if (filled) return
        val cube = g.carriedCube ?: return
        cube.held = false
        cube.consumed = true
        g.player.carrying = false
        filled = true
        seatTime = 0f
        g.onCubeInserted(this)
    }

    override fun update(g: GameSession, dt: Float) {
        if (filled && seatTime < 1f) seatTime += dt
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        Art.plate(
            c, d, l - cam.s(0.12f), t - cam.s(0.12f), r + cam.s(0.12f), b + cam.s(0.12f),
            Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.4f),
            Palette.mix(Palette.WALL, Palette.VOID, 0.45f),
            Palette.mix(Palette.TRIM, Palette.TEXT_DIM, 0.4f),
            cam.scale, box.l
        )
        // The recess.
        d.round(c, l, t, r, b, cam.s(0.05f), Palette.withAlpha(Palette.VOID, 0.92f))
        if (filled) {
            val k = MathX.smoothStep(seatTime)
            d.round(c, l + cam.s(0.04f), t + cam.s(0.04f), r - cam.s(0.04f), b - cam.s(0.04f),
                cam.s(0.04f), Palette.withAlpha(Palette.ACCENT, 0.35f + 0.5f * k))
            d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(1.3f), Palette.ACCENT, 0.9f * k)
        } else {
            val pulse = 0.4f + 0.4f * sin(g.time * 4f)
            d.roundStroke(c, l, t, r, b, cam.s(0.05f), Palette.withAlpha(Palette.WARN, pulse), 3f)
        }
    }
}

/** A key-pack switch. Chapter 2 uses one to fire the ceiling grabbers. */
class KeySwitch(box: Box, val id: String, val label: String = "ACTIVATE") : Prop(box) {
    var used = false
    var scanning = false
    private var scanTime = 0f

    override val reach: Float = 1.5f

    // Keeps a prompt up through the read: the player has to know that standing
    // here is the instruction, not that the switch stopped working.
    override fun interactLabel(g: GameSession): String = when {
        used -> ""
        scanning -> "HOLD"
        else -> label
    }

    override fun onInteract(g: GameSession) {
        if (used || scanning) return
        if (!g.hasKeyPack || !g.keyPackRepaired) {
            g.say("The pack would open that. If I had it working.", blocking = false)
            g.playSound(Sfx.Id.DENY)
            return
        }
        scanning = true
        scanTime = 0f
        g.playSound(Sfx.Id.CHIME)
    }

    override fun update(g: GameSession, dt: Float) {
        if (!scanning) return
        // Walking away cancels the read. Holding position while something is
        // coming for you is the point of the beat.
        if (!playerInReach(g)) {
            scanning = false
            scanTime = 0f
            g.say("Lost the read. Stay on it.", blocking = false, hold = 0.2f)
            return
        }
        scanTime += dt
        if (scanTime >= SCAN_SECONDS) {
            scanning = false
            used = true
            g.onSwitchUsed(this)
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        val tint = if (used) Palette.GOOD else if (scanning) Palette.ACCENT else Palette.WARN
        Art.plate(
            c, d, l, t, r, b,
            Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.45f),
            Palette.mix(Palette.WALL, Palette.VOID, 0.4f),
            Palette.mix(Palette.TRIM, tint, 0.35f),
            cam.scale, box.l
        )
        val pulse = if (scanning) 0.5f + 0.5f * sin(g.time * 20f) else 1f
        d.circle(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(0.13f), Palette.withAlpha(tint, pulse))
        d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(0.8f), tint, 0.8f * pulse)
        if (scanning) {
            val p = (scanTime / SCAN_SECONDS).coerceIn(0f, 1f)
            val bw = cam.s(1.4f)
            val bx = (l + r) * 0.5f - bw * 0.5f
            val by = t - cam.s(0.45f)
            d.round(c, bx, by, bx + bw, by + cam.s(0.14f), cam.s(0.07f), Palette.withAlpha(Palette.VOID, 0.8f))
            d.round(c, bx, by, bx + bw * p, by + cam.s(0.14f), cam.s(0.07f), Palette.ACCENT)
        }
    }

    companion object { const val SCAN_SECONDS = 1.1f }
}

/**
 * A repair job inside the lift. Three of them have to be finished while the
 * thing in the car keeps swinging.
 */
class LiftTask(box: Box, val id: String, val kind: Kind, val title: String) : Prop(box) {

    enum class Kind { LOOM, BREAKERS, VALVE }

    var done = false
    /** VALVE is a hold, not a puzzle: turn it while dodging. */
    var holdProgress = 0f
    private var holding = false

    override val reach: Float = 1.4f

    override fun interactLabel(g: GameSession): String = when {
        done -> ""
        kind == Kind.VALVE -> "TURN"
        else -> "REPAIR"
    }

    override fun onInteract(g: GameSession) {
        if (done) return
        when (kind) {
            Kind.VALVE -> holding = true
            Kind.LOOM -> g.openOverlay(WiringPuzzle(g.puzzleSeed + id.hashCode().toLong())) { ok ->
                if (ok) { done = true; g.onTaskCompleted(this) }
            }
            Kind.BREAKERS -> g.openOverlay(BreakerPuzzle(g.puzzleSeed + id.hashCode().toLong())) { ok ->
                if (ok) { done = true; g.onTaskCompleted(this) }
            }
        }
    }

    override fun update(g: GameSession, dt: Float) {
        if (done || kind != Kind.VALVE) return
        if (holding && playerInReach(g)) {
            holdProgress += dt / VALVE_SECONDS
            if (holdProgress >= 1f) {
                holdProgress = 1f
                done = true
                holding = false
                g.onTaskCompleted(this)
            }
        } else {
            holding = false
            // Slips back if you have to break off and dodge.
            holdProgress = (holdProgress - dt * 0.25f).coerceAtLeast(0f)
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        val tint = if (done) Palette.GOOD else Palette.WARN
        Art.plate(
            c, d, l, t, r, b,
            Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.42f),
            Palette.mix(Palette.WALL, Palette.VOID, 0.45f),
            Palette.mix(Palette.TRIM, tint, 0.3f),
            cam.scale, box.l
        )
        when (kind) {
            Kind.LOOM -> {
                for (i in 0 until 4) {
                    val y = t + (b - t) * (0.22f + i * 0.19f)
                    val colour = if (done) Palette.GOOD else Palette.WIRE[i % Palette.WIRE.size]
                    d.line(c, l + cam.s(0.10f), y, r - cam.s(0.10f), y,
                        Palette.withAlpha(colour, if (done) 0.9f else 0.7f), cam.s(0.045f))
                    if (!done) {
                        d.rect(c, (l + r) * 0.5f - cam.s(0.05f), y - cam.s(0.03f),
                            (l + r) * 0.5f + cam.s(0.05f), y + cam.s(0.03f),
                            Palette.withAlpha(Palette.VOID, 0.9f))
                    }
                }
            }
            Kind.BREAKERS -> {
                for (i in 0 until 3) {
                    val x = l + (r - l) * (0.25f + i * 0.25f)
                    val up = done || i == 1
                    d.line(c, x, (t + b) * 0.5f, x, if (up) t + (b - t) * 0.28f else b - (b - t) * 0.28f,
                        Palette.withAlpha(if (up) Palette.GOOD else Palette.BAD, 0.85f), cam.s(0.05f))
                    d.circle(c, x, if (up) t + (b - t) * 0.28f else b - (b - t) * 0.28f, cam.s(0.07f),
                        if (up) Palette.GOOD else Palette.BAD)
                }
            }
            Kind.VALVE -> {
                val cx = (l + r) * 0.5f
                val cy = (t + b) * 0.5f
                val rad = MathX.min(r - l, b - t) * 0.34f
                val angle = holdProgress * 9f
                d.circleStroke(c, cx, cy, rad, Palette.withAlpha(tint, 0.85f), cam.s(0.05f))
                for (i in 0 until 4) {
                    val a = angle + i * 1.5708f
                    d.line(c, cx, cy, cx + cos(a) * rad, cy + sin(a) * rad,
                        Palette.withAlpha(tint, 0.9f), cam.s(0.045f))
                }
                if (!done && holdProgress > 0.01f) {
                    val bw = cam.s(1.2f)
                    val bx = cx - bw * 0.5f
                    val by = t - cam.s(0.4f)
                    d.round(c, bx, by, bx + bw, by + cam.s(0.12f), cam.s(0.06f), Palette.withAlpha(Palette.VOID, 0.8f))
                    d.round(c, bx, by, bx + bw * holdProgress, by + cam.s(0.12f), cam.s(0.06f), Palette.WARN)
                }
            }
        }
        if (done) d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(0.9f), Palette.GOOD, 0.6f)
        d.textCentered(c, title, (l + r) * 0.5f, t - cam.s(0.62f), cam.s(0.22f),
            Palette.withAlpha(if (done) Palette.GOOD else Palette.TEXT_DIM, 0.9f), true)
    }

    companion object { const val VALVE_SECONDS = 2.6f }
}

/** Ceiling claws that drop, take the player, and hold them out of reach. */
class Grabbers(box: Box) : Prop(box) {
    /** 0 = stowed in the ceiling, 1 = fully extended. */
    var extend = 0f
    var armed = false
    var holding = false

    /**
     * When set, the script drives the claws frame by frame instead of the
     * armed ramp. The grab has to be able to stop the claws exactly where the
     * player is, and then carry them up at its own pace.
     */
    var commanded: Float? = null

    /** World y of the claw tips — where whatever they have hold of hangs from. */
    fun tipY(): Float = box.t + box.h * extend

    override fun interactLabel(g: GameSession): String = ""

    override fun update(g: GameSession, dt: Float) {
        val driven = commanded
        if (driven != null) {
            extend = driven
            return
        }
        val target = if (armed) 1f else 0f
        extend = MathX.moveToward(extend, target, dt * 1.6f)
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val cx = cam.sx(box.cx)
        val topY = cam.sy(box.t)
        val drop = cam.s(box.h) * extend
        val armW = cam.s(0.14f)

        // Rail the claws hang from.
        d.rect(c, cam.sx(box.l), topY - cam.s(0.22f), cam.sx(box.r), topY, Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.5f))
        for (side in intArrayOf(-1, 1)) {
            val ax = cx + side * cam.s(0.42f)
            d.line(c, ax, topY, ax, topY + drop, Palette.mix(Palette.TRIM, Palette.VOID, 0.25f), armW)
            // Claw fingers.
            val fy = topY + drop
            val spread = cam.s(0.30f) * (if (holding) 0.45f else 1f)
            for (f in -1..1) {
                d.line(c, ax, fy, ax + f * spread, fy + cam.s(0.34f),
                    Palette.mix(Palette.TRIM, Palette.VOID, 0.15f), armW * 0.6f)
            }
            d.circle(c, ax, fy, armW * 0.75f, Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.4f))
        }
        if (extend > 0.05f) {
            d.glow(c, cx, topY + drop, cam.s(0.9f), Palette.ACCENT, 0.35f * extend)
        }
    }
}

/** The smelter: a lit pit of molten metal. Lethal, and the chapter's finale. */
class Smelter(box: Box) : Prop(box) {
    var active = false
    private var heat = 0f

    override fun interactLabel(g: GameSession): String = ""

    override fun update(g: GameSession, dt: Float) {
        heat = MathX.moveToward(heat, if (active) 1f else 0.15f, dt * 0.5f)
        if (active && g.particles.enabled) {
            if (g.time % 0.08f < dt) {
                g.particles.embers(
                    box.l + (box.w * ((g.time * 0.37f) % 1f)), box.t, 2, LAVA_HOT
                )
            }
            if (g.time % 0.35f < dt) g.particles.steam(box.cx, box.t, 1, LAVA_GLOW)
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        // The molten surface, churning.
        d.vGradient(c, l, t, r, b, LAVA_HOT, LAVA_DEEP)
        val waves = 5
        for (i in 0 until waves) {
            val f = i / waves.toFloat()
            val y = t + (b - t) * (0.12f + f * 0.6f)
            val amp = cam.s(0.06f) * heat
            val shift = sin(g.time * (0.8f + f) + i) * cam.s(0.5f)
            d.rect(c, l + shift, y, r + shift, y + amp, Palette.withAlpha(LAVA_GLOW, 0.5f * heat))
        }
        d.glow(c, (l + r) * 0.5f, t, cam.s(4.5f), LAVA_GLOW, 0.85f * heat)
        // Crusted rim.
        d.rect(c, l, t - cam.s(0.10f), r, t, Palette.mix(Palette.VOID, Art.RUST, 0.45f))
    }

    companion object {
        val LAVA_HOT = 0xFFFFC24A.toInt()
        val LAVA_GLOW = 0xFFFF7A2B.toInt()
        val LAVA_DEEP = 0xFF8C2408.toInt()
    }
}

/** A door the new monster smashes through. Solid until the script breaks it. */
class BreakableDoor(box: Box, val name: String) : Prop(box) {
    var broken = false
    var breakTime = 0f

    override val solid: Box? get() = if (broken) null else box
    override fun interactLabel(g: GameSession): String = ""

    override fun update(g: GameSession, dt: Float) {
        if (broken && breakTime < 1.4f) breakTime += dt
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        if (!broken) {
            Art.plate(
                c, d, l, t, r, b,
                Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.35f),
                Palette.mix(Palette.WALL, Palette.VOID, 0.5f),
                Palette.mix(Palette.TRIM, Palette.TEXT_DIM, 0.35f),
                cam.scale, box.l
            )
            Art.hazardBand(c, d, l, (t + b) * 0.5f - cam.s(0.12f), r, (t + b) * 0.5f + cam.s(0.12f), Palette.WARN)
            return
        }
        // Blown open: the frame keeps a ring of torn plate.
        val k = MathX.clamp(breakTime / 0.5f, 0f, 1f)
        d.rect(c, l, t, r, t + (b - t) * 0.18f * (1f - k * 0.4f), Palette.mix(Palette.WALL, Palette.VOID, 0.4f))
        d.rect(c, l, b - (b - t) * 0.14f, r, b, Palette.mix(Palette.WALL, Palette.VOID, 0.4f))
        for (side in intArrayOf(-1, 1)) {
            val ex = if (side < 0) l else r
            for (i in 0 until 4) {
                val f = i / 3f
                val y = MathX.lerp(t + (b - t) * 0.2f, b - (b - t) * 0.18f, f)
                val len = cam.s(0.28f + 0.22f * sin(i * 2.1f + side))
                d.poly(
                    c,
                    floatArrayOf(ex, y, ex - side * len, y + cam.s(0.14f), ex - side * len * 0.4f, y + cam.s(0.3f)),
                    Palette.mix(Palette.TRIM, Palette.VOID, 0.25f)
                )
            }
        }
    }
}
