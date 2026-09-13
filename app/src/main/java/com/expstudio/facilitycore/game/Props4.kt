package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.core.Art
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Camera
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.abs
import kotlin.math.sin

/**
 * Chapter 4's mechanisms.
 *
 * Every puzzle in the first three chapters eventually became a panel you stood
 * in front of and a grid you tapped. These are the other kind: things you solve
 * by where you put your body, what you put on top of what, and when you move.
 * None of them opens an overlay. The room is the puzzle.
 *
 * They all share one rule — a mechanism must read its own state from the world
 * every frame rather than being told about it — so that a crate hauled onto a
 * plate, a plate stood on, and a plate stood on by a monster all work without
 * any of them knowing about the others.
 */

/** Anything the chapter can ask "are you satisfied?". */
interface Mechanism {
    val group: String
    fun satisfied(): Boolean
}

/**
 * 1. A floor plate that needs weight on it.
 *
 * The player is weight. So is a heavy crate, which is the whole point: two
 * plates and one body means the crate has to hold one of them.
 */
class PressurePlate(box: Box, val id: String, override val group: String) : Prop(box), Mechanism {

    var pressed = false
        private set
    private var travel = 0f
    private var wasPressed = false

    override fun satisfied(): Boolean = pressed

    override fun update(g: GameSession, dt: Float) {
        val zone = Box(box.l, box.t - 0.45f, box.r, box.b)
        var weight = zone.overlaps(g.player.bounds())
        if (!weight) {
            for (p in g.room.props) {
                if (p is HeavyCrate && zone.overlaps(p.box)) { weight = true; break }
            }
        }
        pressed = weight
        travel = MathX.approach(travel, if (pressed) 1f else 0f, 6f, dt)
        if (pressed != wasPressed) {
            g.playSound(if (pressed) Sfx.Id.CLANK else Sfx.Id.CLICK, 0.55f)
            wasPressed = pressed
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val b = cam.sy(box.b)
        val depth = cam.s(0.16f)
        val t = b - depth * (1f - travel * 0.65f)
        val tint = if (pressed) Palette.GOOD else Palette.WARN
        d.rect(c, l - cam.s(0.1f), b - depth * 1.25f, r + cam.s(0.1f), b, Palette.mix(Palette.WALL, Palette.VOID, 0.4f))
        d.rect(c, l, t, r, b, Palette.mix(Palette.WALL_LIT, tint, 0.28f))
        d.rect(c, l, t, r, t + cam.s(0.04f), Palette.withAlpha(tint, 0.9f))
        d.glow(c, (l + r) * 0.5f, t, cam.s(0.7f), tint, if (pressed) 0.85f else 0.35f)
    }
}

/**
 * 2. A platform that will not hold you.
 *
 * Stand on it and it starts to go. Stop standing on it and it comes back. An
 * ascent made of these is a puzzle about not stopping.
 */
class CrumblePlatform(box: Box, val id: String) : Prop(box) {

    private val homeT = box.t
    private val homeB = box.b
    private var state = 0f          // 0 solid, 1 gone
    private var touched = 0f
    private var falling = false

    override val solid: Box? get() = if (state < 0.75f) box else null

    override fun update(g: GameSession, dt: Float) {
        val stood = state < 0.75f &&
            g.player.bounds().inflated(0.08f).overlaps(Box(box.l, box.t - 0.3f, box.r, box.b))
        if (stood && !falling) {
            touched += dt
            if (touched > HOLD_SECONDS) {
                falling = true
                g.playSound(Sfx.Id.DEBRIS, 0.5f)
            }
        }
        if (falling) {
            state = MathX.approach(state, 1f, 1.9f, dt)
            if (state >= 0.999f) { falling = false; touched = 0f }
        } else if (!stood && state > 0f) {
            state = MathX.approach(state, 0f, 0.55f, dt)
            if (state <= 0.001f) touched = 0f
        }
        // Sags as it goes, then is simply not there.
        val sag = state * 1.6f
        box.set(box.l, homeT + sag, box.r, homeB + sag)
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        if (state > 0.9f) return
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        val alpha = 1f - state
        val strain = if (touched > 0f && !falling) (touched / HOLD_SECONDS) else 0f
        val shake = if (strain > 0f) sin(g.time * 48f) * cam.s(0.035f) * strain else 0f
        d.rect(c, l + shake, t, r + shake, b, Palette.withAlpha(Palette.mix(Palette.FLOOR_EDGE, Art.RUST, 0.35f), alpha))
        d.rect(c, l + shake, t, r + shake, t + cam.s(0.05f),
            Palette.withAlpha(Palette.mix(Palette.WARN, Palette.BAD, strain), alpha))
        // Bolts pulling out of the wall as it takes weight.
        var i = 0
        while (i < 3) {
            val x = MathX.lerp(l, r, 0.2f + i * 0.3f)
            d.circle(c, x + shake, t + cam.s(0.12f), cam.s(0.04f), Palette.withAlpha(Palette.VOID, alpha * 0.7f))
            i++
        }
    }

    private companion object { const val HOLD_SECONDS = 0.55f }
}

/**
 * 3. A beam across the room, on a clock.
 *
 * Not an obstacle to be removed — one to be timed. It costs a life to walk
 * into, and the roll goes under it.
 */
class SweepBeam(
    box: Box,
    val id: String,
    private val period: Float,
    private val onFraction: Float,
    private val phase: Float
) : Prop(box) {

    var live = false
        private set
    /** Set by the chapter when the beam should stop mattering. */
    var disarmed = false

    override fun update(g: GameSession, dt: Float) {
        if (disarmed) { live = false; return }
        val t = ((g.time + phase) % period) / period
        live = t < onFraction
        if (!live) return
        if (g.player.isInvulnerable) return
        if (box.inflated(-0.05f).overlaps(g.player.bounds())) {
            g.takeHit(if (g.player.x < (box.l + box.r) * 0.5f) -1 else 1, g.stage)
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        // Emitters at both ends, always visible, so the beam is never a surprise.
        for (x in floatArrayOf(l, r)) {
            d.rect(c, x - cam.s(0.12f), t - cam.s(0.1f), x + cam.s(0.12f), b + cam.s(0.1f),
                Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.2f))
        }
        if (disarmed) return
        val cycle = ((g.time + phase) % period) / period
        if (!live) {
            // The tell: it brightens just before it fires.
            val warn = MathX.clamp((cycle - (onFraction + (1f - onFraction) * 0.72f)) /
                ((1f - onFraction) * 0.28f), 0f, 1f)
            if (warn > 0f) {
                d.rect(c, l, (t + b) * 0.5f - cam.s(0.02f), r, (t + b) * 0.5f + cam.s(0.02f),
                    Palette.withAlpha(Palette.BAD, 0.25f + 0.4f * warn))
            }
            return
        }
        val flicker = 0.82f + 0.18f * sin(g.time * 60f)
        d.rect(c, l, t, r, b, Palette.withAlpha(Palette.BAD, 0.55f * flicker))
        d.rect(c, l, (t + b) * 0.5f - cam.s(0.035f), r, (t + b) * 0.5f + cam.s(0.035f),
            Palette.withAlpha(Palette.TEXT, 0.85f * flicker))
        d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(1.1f), Palette.BAD, 0.7f * flicker)
    }
}

/**
 * 4. A column of moving air.
 *
 * While it is running, anything in it falls slowly and can be pushed up. It is
 * the only way onto some ledges, and it is on a switch.
 */
class Updraft(box: Box, val id: String) : Prop(box) {

    var running = false

    override fun update(g: GameSession, dt: Float) {
        if (!running) return
        if (!box.overlaps(g.player.bounds())) return
        // Lift, capped, so it is a ride rather than a launch.
        if (g.player.vy > -LIFT_SPEED) g.player.vy -= LIFT_ACCEL * dt
        if (g.player.vy < -LIFT_SPEED) g.player.vy = -LIFT_SPEED
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        d.rect(c, l, b - cam.s(0.2f), r, b, Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.25f))
        if (!running) {
            d.rect(c, l, b - cam.s(0.16f), r, b - cam.s(0.12f), Palette.withAlpha(Palette.TEXT_DIM, 0.5f))
            return
        }
        // Streamers, so the column is legible without a particle system.
        var i = 0
        while (i < 9) {
            val f = ((g.time * 1.3f + i * 0.111f) % 1f)
            val y = MathX.lerp(b, t, f)
            val x = MathX.lerp(l + cam.s(0.2f), r - cam.s(0.2f), ((i * 37) % 100) / 100f)
            val a = (1f - abs(f - 0.5f) * 2f) * 0.55f
            d.rect(c, x - cam.s(0.03f), y, x + cam.s(0.03f), y + cam.s(0.5f),
                Palette.withAlpha(Palette.ACCENT, a))
            i++
        }
        d.glow(c, (l + r) * 0.5f, b, cam.s(1.4f), Palette.ACCENT, 0.4f)
    }

    private companion object {
        const val LIFT_SPEED = 5.4f
        const val LIFT_ACCEL = 34f
    }
}

/**
 * 5. A carriage on a rail.
 *
 * It runs between two points on its own clock. Standing on it carries you; the
 * hand can also take hold of it, which is how you cross the gaps it does not
 * span.
 */
class Carriage(box: Box, val id: String, val fromX: Float, val toX: Float, private val speed: Float) :
    Prop(box) {

    private var t = 0f
    private var dir = 1f
    private val w = box.r - box.l
    private val h = box.b - box.t

    override val solid: Box? get() = box

    var running = true

    override fun update(g: GameSession, dt: Float) {
        if (!running) return
        val before = box.l
        t += dir * speed * dt / (toX - fromX).coerceAtLeast(0.001f)
        if (t >= 1f) { t = 1f; dir = -1f } else if (t <= 0f) { t = 0f; dir = 1f }
        val nx = MathX.lerp(fromX, toX, MathX.smoothStep(t))
        box.set(nx, box.t, nx + w, box.b)
        // Anything standing on it comes along. Without this it slides out from
        // under the player, which is the classic moving-platform bug.
        val dx = box.l - before
        val feet = Box(box.l, box.t - 0.35f, box.r, box.t + 0.1f)
        if (feet.overlaps(g.player.bounds())) g.player.teleport(g.player.x + dx, g.player.y)
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val tY = cam.sy(box.t); val b = cam.sy(box.b)
        // The rail it runs on, drawn the full span.
        d.rect(c, cam.sx(fromX) - cam.s(0.3f), tY - cam.s(0.34f), cam.sx(toX) + w * 0f + cam.s(0.3f + (box.r - box.l)),
            tY - cam.s(0.26f), Palette.mix(Palette.TRIM, Palette.VOID, 0.3f))
        d.round(c, l, tY, r, b, cam.s(0.06f), Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.15f))
        d.rect(c, l, tY, r, tY + cam.s(0.06f), Palette.withAlpha(Palette.ACCENT, 0.7f))
        // Hangers up to the rail.
        for (f in floatArrayOf(0.25f, 0.75f)) {
            val x = MathX.lerp(l, r, f)
            d.rect(c, x - cam.s(0.04f), tY - cam.s(0.3f), x + cam.s(0.04f), tY, Palette.TRIM)
        }
    }
}

/**
 * 6. A gate on a timer.
 *
 * The switch is never next to the door. Hitting it starts a clock, and the
 * clock is the puzzle: the route has to be learnt before it can be run.
 */
class TimedGate(box: Box, val name: String, private val openSeconds: Float) : Prop(box) {

    var open = false
        private set
    private var left = 0f
    private var slab = 0f

    override val solid: Box? get() = if (slab > 0.85f) null else box

    fun trigger(g: GameSession) {
        left = openSeconds
        if (!open) {
            open = true
            g.playSound(Sfx.Id.UNLOCK, 0.8f)
        }
    }

    override fun update(g: GameSession, dt: Float) {
        if (left > 0f) {
            left -= dt
            if (left <= 0f) {
                open = false
                g.playSound(Sfx.Id.SHUTTER, 0.7f)
            }
        }
        slab = MathX.approach(slab, if (open) 1f else 0f, 3.2f, dt)
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        d.rect(c, l - cam.s(0.12f), t - cam.s(0.12f), r + cam.s(0.12f), b, Palette.WALL_LIT)
        val height = (b - t) * (1f - slab)
        if (height > 1f) {
            d.rect(c, l, t, r, t + height, Palette.mix(Palette.FLOOR_EDGE, Palette.TRIM, 0.4f))
            d.rect(c, l, t + height - cam.s(0.1f), r, t + height, Palette.TRIM)
        }
        // The clock, on the frame, where it can be read while running at it.
        if (open) {
            val f = (left / openSeconds).coerceIn(0f, 1f)
            val bw = cam.s(1.1f)
            val bx = (l + r) * 0.5f - bw * 0.5f
            val by = t - cam.s(0.5f)
            d.round(c, bx, by, bx + bw, by + cam.s(0.13f), cam.s(0.06f), Palette.withAlpha(Palette.VOID, 0.75f))
            d.round(c, bx, by, bx + bw * f, by + cam.s(0.13f), cam.s(0.06f),
                if (f < 0.3f) Palette.BAD else Palette.GOOD)
        }
    }
}

/**
 * 7. A switch you hit on the way past.
 *
 * One press, no hold, no panel. It exists to start [TimedGate]s and to arm
 * fans, and it is always placed where stopping costs you.
 */
class SlapSwitch(box: Box, val id: String, val label: String = "HIT") : Prop(box) {

    var used = false
    private var flash = 0f

    override val reach: Float = 1.45f

    override fun interactLabel(g: GameSession): String = label

    override fun onInteract(g: GameSession) {
        used = true
        flash = 1f
        g.playSound(Sfx.Id.CLANK, 0.8f)
        g.onSlapSwitch(this)
    }

    override fun update(g: GameSession, dt: Float) {
        if (flash > 0f) flash -= dt * 2.4f
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        val tint = if (flash > 0f) Palette.GOOD else if (used) Palette.ACCENT_DIM else Palette.WARN
        d.round(c, l, t, r, b, cam.s(0.08f), Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.2f))
        d.circle(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(0.22f), tint)
        d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(0.9f),
            tint, 0.4f + 0.4f * (if (flash > 0f) flash else 0.5f + 0.5f * sin(g.time * 4f)))
    }
}

/**
 * 8. A beam that a crate will stop.
 *
 * The beam runs until something solid is in the way. Where the crate goes is
 * the answer, and the hand is how it gets there.
 */
class BlockBeam(box: Box, val id: String, override val group: String) : Prop(box), Mechanism {

    var blocked = false
        private set

    override fun satisfied(): Boolean = blocked

    override fun update(g: GameSession, dt: Float) {
        var stopped = false
        for (p in g.room.props) {
            if (p is HeavyCrate && p.box.overlaps(box)) { stopped = true; break }
        }
        if (!stopped) {
            for (s in g.room.solids) {
                if (s.kind != Solid.Kind.STRUCTURE && s.box.overlaps(box)) { stopped = true; break }
            }
        }
        blocked = stopped
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        val tint = if (blocked) Palette.GOOD else Palette.ACCENT
        if (!blocked) {
            d.rect(c, l, t, r, b, Palette.withAlpha(tint, 0.35f))
            d.rect(c, l, (t + b) * 0.5f - cam.s(0.025f), r, (t + b) * 0.5f + cam.s(0.025f),
                Palette.withAlpha(Palette.TEXT, 0.6f))
        }
        for (x in floatArrayOf(l, r)) {
            d.rect(c, x - cam.s(0.1f), t - cam.s(0.12f), x + cam.s(0.1f), b + cam.s(0.12f),
                Palette.mix(Palette.WALL_LIT, tint, 0.3f))
        }
        d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(0.8f), tint, if (blocked) 0.8f else 0.35f)
    }
}

/**
 * 9. A counterweighted deck.
 *
 * It hangs at the top of its chain and only comes down while its pan has
 * something in it, so the way up is backwards: load the pan, walk onto the deck
 * the load brought down, and then take the weight back out of the pan from
 * where you are standing.
 */
class Counterweight(
    box: Box,
    val id: String,
    /** How far it drops when the pan is loaded. Its home is the raised position. */
    private val rise: Float,
    /** The pan, on the floor beside it. Anything heavy in here brings the deck down. */
    val pan: Box
) : Prop(box) {

    private val homeT = box.t
    private val homeB = box.b
    var loaded = false
        private set
    private var offset = 0f

    override val solid: Box? get() = box

    /** Where to put the weight, and where to take it back from. */
    fun panCentre(): Float = (pan.l + pan.r) * 0.5f

    override fun update(g: GameSession, dt: Float) {
        // Cargo only. Standing in the pan yourself would hold the deck down at
        // exactly the moment you needed it to let go, and the pan's edge would
        // become a switch you flicked by shuffling.
        var weight = false
        for (p in g.room.props) {
            if (p is HeavyCrate && pan.overlaps(p.box)) { weight = true; break }
        }
        loaded = weight

        // A machine on a chain travels at a speed, not on an easing curve.
        val before = offset
        offset = MathX.moveToward(offset, if (loaded) rise else 0f, TRAVEL_SPEED * dt)
        box.set(box.l, homeT + offset, box.r, homeB + offset)

        // Carry a rider with it, or the deck simply slides out from under them.
        val dy = offset - before
        val zone = Box(box.l, box.t - 0.5f, box.r, box.t + 0.2f)
        if (dy != 0f && zone.overlaps(g.player.bounds()) && g.player.y <= box.t + 0.3f) {
            g.player.teleport(g.player.x, g.player.y + dy)
        }
    }

    private companion object { const val TRAVEL_SPEED = 1.7f }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        // The pan first, so the deck's chains cross in front of it.
        val pl = cam.sx(pan.l); val pr = cam.sx(pan.r); val pb = cam.sy(pan.b)
        d.rect(c, pl, pb - cam.s(0.5f), pl + cam.s(0.1f), pb, Palette.mix(Palette.TRIM, Palette.VOID, 0.3f))
        d.rect(c, pr - cam.s(0.1f), pb - cam.s(0.5f), pr, pb, Palette.mix(Palette.TRIM, Palette.VOID, 0.3f))
        d.rect(c, pl, pb - cam.s(0.12f), pr, pb,
            Palette.mix(Palette.FLOOR_EDGE, if (loaded) Palette.GOOD else Palette.WARN, 0.35f))
        d.line(c, (pl + pr) * 0.5f, pb - cam.s(0.5f), (pl + pr) * 0.5f, cam.sy(pan.t) - cam.s(7f),
            Palette.withAlpha(Art.CABLE, 0.8f), cam.s(0.05f))

        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        d.round(c, l, t, r, b, cam.s(0.05f), Palette.mix(Palette.FLOOR_EDGE, Palette.VOID, 0.2f))
        d.rect(c, l, t, r, t + cam.s(0.05f), Palette.withAlpha(if (loaded) Palette.WARN else Palette.GOOD, 0.9f))
        // Chains up out of frame, so the counterweighting is visible.
        for (f in floatArrayOf(0.2f, 0.8f)) {
            val x = MathX.lerp(l, r, f)
            d.line(c, x, t, x, t - cam.s(7f), Palette.withAlpha(Art.CABLE, 0.8f), cam.s(0.05f))
        }
    }
}

/**
 * 10. A rail the hand can take hold of.
 *
 * Not a bar that pulls you to it — a runner that pulls you along it, all the
 * way to the far end. The chapter's long horizontal gaps are crossed with it.
 */
class PullRail(box: Box, railId: String, val toX: Float) : ReachAnchor(box, railId, Kind.BAR) {

    override fun gripX(): Float = (box.l + box.r) * 0.5f
    override fun gripY(): Float = (box.t + box.b) * 0.5f

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        d.rect(c, cam.sx(minOf(box.l, toX)) - cam.s(0.2f), t - cam.s(0.06f),
            cam.sx(maxOf(box.r, toX)) + cam.s(0.2f), t, Palette.mix(Palette.TRIM, Palette.VOID, 0.35f))
        d.round(c, l, t, r, b, cam.s(0.06f), Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.1f))
        val pulse = 0.45f + 0.35f * sin(g.time * 2.6f + box.l)
        if (!spent) d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(1.0f), Palette.ACCENT, 0.5f * pulse)
    }
}
