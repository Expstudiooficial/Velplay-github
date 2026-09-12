package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Camera
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Something the extendable hand can take hold of.
 *
 * Anchors are ordinary props, so they light up on approach and sit in the room
 * data like anything else. What makes them anchors is that [Reach] can see them
 * from across the room, which is the whole point of the tool: the chapter is
 * built out of gaps that are obviously crossable and obviously not by walking.
 */
open class ReachAnchor(
    box: Box,
    val id: String,
    val kind: Kind,
    /** Anchors can be inert until the story arms them. */
    var armed: Boolean = true
) : Prop(box) {

    enum class Kind {
        /** Pulls the player to the bar. Vents, ledges, shafts. */
        BAR,

        /** Hauls a hanging shutter down and seals whatever it covers. */
        SHUTTER,

        /** Two hands into the seam of a jammed door, then pull it apart. */
        RIP,

        /** Drags a heavy crate to the player's feet. */
        CRATE,

        /** Throws a switch that is nowhere near your hands. */
        LEVER
    }

    /** Set once used; most anchors are one-shot. */
    var spent = false

    /** Filled in by whatever the anchor operates on. */
    var onPulled: ((GameSession) -> Unit)? = null

    /** Where the hand actually grips, in world space. */
    open fun gripX(): Float = (box.l + box.r) * 0.5f
    open fun gripY(): Float = (box.t + box.b) * 0.5f

    /** Seconds of strain before the anchor gives. RIP anchors fight back. */
    open val pullSeconds: Float
        get() = when (kind) {
            Kind.RIP -> 2.4f
            Kind.SHUTTER -> 1.3f
            Kind.CRATE -> 1.1f
            Kind.LEVER -> 0.5f
            Kind.BAR -> 0f
        }

    /** Progress 0..1 while being worked, for the anchor's own art. */
    var strain = 0f

    override val reach: Float = 0.6f

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l)
        val r = cam.sx(box.r)
        val t = cam.sy(box.t)
        val b = cam.sy(box.b)
        val live = armed && !spent
        val tint = when {
            spent -> Palette.TEXT_DIM
            !armed -> Palette.WALL_LIT
            else -> Palette.ACCENT
        }
        when (kind) {
            Kind.BAR -> {
                d.round(c, l, t, r, b, cam.s(0.09f), Palette.mix(Palette.TRIM, Palette.VOID, 0.35f))
                d.rect(c, l, t, r, t + cam.s(0.05f), Palette.withAlpha(tint, 0.75f))
                // End caps, so it reads as a bar bolted on rather than a floating line.
                d.round(c, l - cam.s(0.10f), t - cam.s(0.10f), l + cam.s(0.06f), b + cam.s(0.10f),
                    cam.s(0.04f), Palette.WALL_LIT)
                d.round(c, r - cam.s(0.06f), t - cam.s(0.10f), r + cam.s(0.10f), b + cam.s(0.10f),
                    cam.s(0.04f), Palette.WALL_LIT)
            }
            Kind.SHUTTER -> {
                // Drawn as a slab hanging from its housing; the pull brings it down.
                val drop = (b - t) * (0.18f + 0.82f * strain)
                d.rect(c, l, t, r, t + cam.s(0.22f), Palette.WALL_LIT)
                d.rect(c, l + cam.s(0.06f), t, r - cam.s(0.06f), t + drop,
                    Palette.mix(Palette.FLOOR_EDGE, Palette.VOID, 0.25f))
                var i = 1
                while (i < 5) {
                    val yy = t + drop * i / 5f
                    d.rect(c, l + cam.s(0.06f), yy, r - cam.s(0.06f), yy + cam.s(0.04f),
                        Palette.withAlpha(Palette.VOID, 0.5f))
                    i++
                }
            }
            Kind.RIP -> {
                d.rect(c, l, t, r, b, Palette.mix(Palette.FLOOR_EDGE, Palette.VOID, 0.3f))
                // The seam. It widens as the hands work it.
                val mid = (l + r) * 0.5f
                val gap = cam.s(0.04f) + cam.s(0.5f) * strain
                d.rect(c, mid - gap, t, mid + gap, b, Palette.VOID)
                d.rect(c, mid - gap - cam.s(0.05f), t, mid - gap, b, Palette.withAlpha(tint, 0.5f))
                d.rect(c, mid + gap, t, mid + gap + cam.s(0.05f), b, Palette.withAlpha(tint, 0.5f))
            }
            Kind.CRATE -> {
                d.round(c, l, t, r, b, cam.s(0.06f), Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.2f))
                d.roundStroke(c, l, t, r, b, cam.s(0.06f), Palette.withAlpha(tint, 0.55f), 3f)
            }
            Kind.LEVER -> {
                d.rect(c, l, b - cam.s(0.12f), r, b, Palette.WALL_LIT)
                val lean = if (spent) 1f else strain
                val hx = MathX.lerp(l + cam.s(0.1f), r - cam.s(0.1f), lean)
                d.line(c, (l + r) * 0.5f, b, hx, t, Palette.TRIM, cam.s(0.09f))
                d.circle(c, hx, t, cam.s(0.12f), tint)
            }
        }
        if (live) {
            // Anchors have to be readable from across a dark room, so they get a
            // slow pulse rather than a static highlight.
            val pulse = 0.45f + 0.35f * sin(g.time * 2.6f + box.l)
            d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(0.9f), Palette.ACCENT, 0.45f * pulse)
        }
    }
}

/**
 * The extendable hand.
 *
 * Given to the player in Chapter 3's archive room. It fires at the best anchor
 * in front of the player, and what happens next depends on the anchor: bars
 * pull the player, everything else pulls the world.
 *
 * The targeting deliberately forgives: a cone rather than a ray, biased toward
 * whatever the player is facing, because the game is played with a thumb.
 */
class Reach {

    enum class State { IDLE, EXTENDING, WORKING, FLYING, RETRACT }

    var unlocked = false
    var state = State.IDLE
        private set
    var target: ReachAnchor? = null
        private set

    /** How far along the arm is, 0..1, for both the art and the hit test. */
    var extend = 0f
        private set
    private var tipX = 0f
    private var tipY = 0f
    private var anchorX = 0f
    private var anchorY = 0f
    private var work = 0f
    private var flyTime = 0f
    private var flyFromX = 0f
    private var flyFromY = 0f

    /** Blocked while a hand is out; the player cannot roll mid-pull. */
    val busy: Boolean get() = state != State.IDLE

    /** Cooldown so the button cannot be mashed into a physics problem. */
    private var cooldown = 0f
    val ready: Boolean get() = unlocked && state == State.IDLE && cooldown <= 0f

    fun reset() {
        state = State.IDLE
        target = null
        extend = 0f
        work = 0f
        cooldown = 0f
    }

    /** The origin of the arm: the player's shoulder, not their feet. */
    private fun originX(g: GameSession) = g.player.x
    private fun originY(g: GameSession) = g.player.y - g.player.height * 0.72f

    /**
     * Picks the anchor to grab. Prefers what is in front, then what is close,
     * and never chooses something through a wall.
     */
    fun pick(g: GameSession): ReachAnchor? {
        val ox = originX(g)
        val oy = originY(g)
        var best: ReachAnchor? = null
        var bestScore = Float.MAX_VALUE
        for (p in g.room.props) {
            if (p !is ReachAnchor || !p.armed || p.spent) continue
            // Bars are for going up. Ignoring the one you are already standing
            // on is what stops the hand from grabbing the same rung forever.
            if (p.kind == ReachAnchor.Kind.BAR && p.gripY() > g.player.y - BAR_MIN_RISE) continue
            val dx = p.gripX() - ox
            val dy = p.gripY() - oy
            val dist = hypot(dx, dy)
            if (dist > RANGE) continue
            // Behind the player costs distance, so a facing anchor wins ties, but
            // something right behind you is still grabbable.
            val behind = g.player.facing > 0 && dx < -0.6f || g.player.facing < 0 && dx > 0.6f
            // Facing only means something while you are moving. Stood still,
            // it is just wherever you last walked — and bracing against a crate
            // means walking past it, which would then rule the crate out.
            val moving = abs(g.player.vx) > 0.6f
            val score = dist + if (behind && moving) BEHIND_PENALTY else 0f
            if (score < bestScore && clear(g, ox, oy, p.gripX(), p.gripY(), p)) {
                best = p
                bestScore = score
            }
        }
        return best
    }

    /**
     * No grabbing through structure. Sampled, because the arm is not a laser.
     *
     * Only the middle of the line is tested. Anchors are bolted to the thing
     * they hang off — a bar sits a hand's width above its own ledge — so a check
     * that ran all the way to the grip would reject every bar in the chapter.
     */
    private fun clear(
        g: GameSession,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        /** The anchor being aimed at. Its own body never blocks the shot. */
        target: ReachAnchor? = null
    ): Boolean {
        val own = target?.solid
        val steps = 12
        var i = 2
        while (i < steps - 2) {
            val t = i / steps.toFloat()
            val px = MathX.lerp(x1, x2, t)
            val py = MathX.lerp(y1, y2, t)
            for (s in g.solidScratch) {
                if (own != null && s === own) continue
                // A hair of slack: anchors are mounted flush to the things they
                // hang off, so an exact test would reject every one of them.
                if (s.inflated(-0.12f).contains(px, py)) return false
            }
            i++
        }
        return true
    }

    fun fire(g: GameSession): Boolean {
        if (!ready) return false
        val a = pick(g) ?: run {
            g.sfx(Sfx.Id.DENY)
            cooldown = 0.35f
            return false
        }
        target = a
        anchorX = a.gripX()
        anchorY = a.gripY()
        tipX = originX(g)
        tipY = originY(g)
        extend = 0f
        work = 0f
        state = State.EXTENDING
        g.playSound(Sfx.Id.CLANK, 0.5f)
        return true
    }

    /** Cancels whatever is in flight — used by deaths, cutscenes and room changes. */
    fun release(g: GameSession) {
        if (state == State.FLYING) g.player.controlEnabled = true
        target?.strain = 0f
        state = State.IDLE
        target = null
        extend = 0f
        cooldown = COOLDOWN
    }

    fun update(g: GameSession, dt: Float, holding: Boolean) {
        if (cooldown > 0f) cooldown -= dt
        val a = target
        if (a == null) { state = State.IDLE; extend = MathX.approach(extend, 0f, 8f, dt); return }

        val ox = originX(g)
        val oy = originY(g)

        when (state) {
            State.EXTENDING -> {
                extend = MathX.approach(extend, 1f, EXTEND_RATE, dt)
                tipX = MathX.lerp(ox, anchorX, extend)
                tipY = MathX.lerp(oy, anchorY, extend)
                if (extend >= 0.999f) {
                    g.playSound(Sfx.Id.CONNECT, 0.6f)
                    if (a.kind == ReachAnchor.Kind.BAR) beginFly(g, a) else state = State.WORKING
                }
            }
            State.WORKING -> {
                tipX = anchorX
                tipY = anchorY
                // Letting go mid-haul drops it. The strain does not persist, so a
                // half-finished rip has to be done again — that is the tension.
                if (!holding) { a.strain = 0f; release(g); return }
                work += dt
                a.strain = (work / a.pullSeconds).coerceIn(0f, 1f)
                g.addTension(dt * 0.3f)
                if (a.kind == ReachAnchor.Kind.CRATE) haulCrate(g, a, dt)
                if (work >= a.pullSeconds) {
                    a.spent = true
                    a.strain = 1f
                    g.playSound(if (a.kind == ReachAnchor.Kind.LEVER) Sfx.Id.CONFIRM else Sfx.Id.IMPACT, 0.9f)
                    g.camera.shake(if (a.kind == ReachAnchor.Kind.RIP) 0.55f else 0.25f, 0.35f)
                    a.onPulled?.invoke(g)
                    g.onAnchorPulled(a)
                    release(g)
                }
            }
            State.FLYING -> {
                flyTime += dt
                val p = MathX.clamp(flyTime / FLY_SECONDS, 0f, 1f)
                val e = MathX.smoothStep(p)
                // Land on top of the bar, not inside it.
                val landX = anchorX
                val landY = a.box.t - 0.02f
                g.player.teleport(
                    MathX.lerp(flyFromX, landX, e),
                    MathX.lerp(flyFromY, landY, e)
                )
                // A small arc so it looks thrown rather than slid.
                g.player.y -= sin(p * Math.PI.toFloat()) * 0.55f
                tipX = anchorX
                tipY = anchorY
                if (p >= 1f) {
                    g.player.teleport(landX, landY)
                    g.player.controlEnabled = true
                    g.player.vx = 0f
                    g.player.vy = 0f
                    g.playSound(Sfx.Id.THUD, 0.55f)
                    a.onPulled?.invoke(g)
                    g.onAnchorPulled(a)
                    release(g)
                }
            }
            State.RETRACT, State.IDLE -> {
                extend = MathX.approach(extend, 0f, 8f, dt)
                if (extend <= 0.01f) { state = State.IDLE; target = null }
            }
        }
    }

    private fun beginFly(g: GameSession, a: ReachAnchor) {
        state = State.FLYING
        flyTime = 0f
        flyFromX = g.player.x
        flyFromY = g.player.y
        g.player.controlEnabled = false
        g.player.vx = 0f
        g.player.vy = 0f
    }

    /**
     * Crates come to you. The crate's solid follows its box, so the haul is real
     * geometry — it can be used as a step, or dropped into a gap to block a jump.
     */
    private fun haulCrate(g: GameSession, a: ReachAnchor, dt: Float) {
        if (a !is HeavyCrate) return
        val feet = g.player.y
        val targetX = g.player.x + g.player.facing * 1.1f
        val dx = targetX - (a.box.l + a.box.r) * 0.5f
        val step = MathX.clamp(dx, -CRATE_SPEED * dt, CRATE_SPEED * dt)
        a.shift(step, feet)
        anchorX = a.gripX()
        anchorY = a.gripY()
        if (abs(dx) < 0.12f) work = a.pullSeconds
    }

    // ---- art -------------------------------------------------------------

    fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        if (state == State.IDLE && extend <= 0.01f) return
        val ox = cam.sx(originX(g))
        val oy = cam.sy(originY(g))
        val tx = cam.sx(tipX)
        val ty = cam.sy(tipY)
        val ang = atan2(ty - oy, tx - ox)
        val len = hypot(tx - ox, ty - oy)
        if (len < 1f) return

        // The arm thins as it stretches, which is where the horror of the thing
        // lives: it is still your arm, and there is much too much of it.
        val baseW = cam.s(0.115f)
        val tipW = baseW * MathX.lerp(1f, 0.42f, extend)
        val nx = -sin(ang)
        val ny = cos(ang)
        d.poly(
            c, floatArrayOf(
                ox + nx * baseW, oy + ny * baseW,
                tx + nx * tipW, ty + ny * tipW,
                tx - nx * tipW, ty - ny * tipW,
                ox - nx * baseW, oy - ny * baseW
            ), Palette.PLAYER_SHADE
        )
        // A lit edge along the top so it is not a flat bar.
        d.line(c, ox + nx * baseW * 0.5f, oy + ny * baseW * 0.5f,
            tx + nx * tipW * 0.5f, ty + ny * tipW * 0.5f,
            Palette.withAlpha(Palette.PLAYER, 0.5f), cam.s(0.03f))

        // Segment banding: the stretch is visibly not skin.
        var i = 1
        val bands = 7
        while (i < bands) {
            val t = i / bands.toFloat()
            val bx = MathX.lerp(ox, tx, t)
            val by = MathX.lerp(oy, ty, t)
            val bw = MathX.lerp(baseW, tipW, t) * 1.25f
            d.line(c, bx + nx * bw, by + ny * bw, bx - nx * bw, by - ny * bw,
                Palette.withAlpha(Palette.VOID, 0.22f), cam.s(0.025f))
            i++
        }

        // The hand.
        val handR = tipW * 2.1f
        d.circle(c, tx, ty, handR, Palette.PLAYER)
        var f = 0
        while (f < 4) {
            val fa = ang + (-0.55f + f * 0.37f)
            d.line(c, tx, ty, tx + cos(fa) * handR * 1.8f, ty + sin(fa) * handR * 1.8f,
                Palette.PLAYER, tipW * 0.55f)
            f++
        }

        val a = target
        if (a != null && state == State.WORKING && a.strain > 0f) {
            // Strain shown on the arm itself rather than as a bar on the HUD.
            val shudder = sin(g.time * 46f) * cam.s(0.04f) * a.strain
            d.line(c, ox, oy + shudder, tx, ty + shudder,
                Palette.withAlpha(Palette.WARN, 0.18f + 0.3f * a.strain), baseW * 0.6f)
        }
    }

    companion object {
        /** Long enough to make rooms feel crossable, short enough to be a puzzle. */
        const val RANGE = 8.5f
        const val BEHIND_PENALTY = 4.5f
        const val EXTEND_RATE = 5.2f
        const val FLY_SECONDS = 0.42f
        const val COOLDOWN = 0.30f
        const val CRATE_SPEED = 3.2f

        /** How far above the feet a bar has to hang before the hand will take it. */
        const val BAR_MIN_RISE = 0.9f
    }
}

/**
 * A crate heavy enough that it can only be moved with the hand. It owns a real
 * solid, so once hauled it is a step, a bridge, or a plug for a hole.
 */
class HeavyCrate(box: Box, id: String) : ReachAnchor(box, id, Kind.CRATE) {

    private var bx = box.l
    private var by = box.t
    private val w = box.r - box.l
    private val h = box.b - box.t

    override val solid: Box? get() = box

    /** Hauls the crate horizontally and drops it so its base rests at [feetY]. */
    fun shift(dx: Float, feetY: Float) {
        bx += dx
        by = feetY - h
        box.set(bx, by, bx + w, by + h)
    }

    /** Lets the story park it somewhere exact. */
    fun placeAt(x: Float, feetY: Float) {
        bx = x
        by = feetY - h
        box.set(bx, by, bx + w, by + h)
    }

    override fun gripX(): Float = (box.l + box.r) * 0.5f
    override fun gripY(): Float = box.t + h * 0.35f

    override val pullSeconds: Float get() = 99f  // ends when the crate arrives

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l)
        val r = cam.sx(box.r)
        val t = cam.sy(box.t)
        val b = cam.sy(box.b)
        com.expstudio.facilitycore.core.Art.crate(c, d, l, t, r, b, cam.s(1f), box.l)
        if (!spent) {
            val pulse = 0.4f + 0.3f * sin(g.time * 2.6f + box.l)
            d.roundStroke(c, l, t, r, b, cam.s(0.05f), Palette.withAlpha(Palette.ACCENT, 0.35f * pulse), 3f)
        }
    }
}
