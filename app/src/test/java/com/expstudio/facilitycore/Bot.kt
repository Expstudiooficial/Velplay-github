package com.expstudio.facilitycore

import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.game.GameSession
import kotlin.math.abs

/**
 * A deterministic stand-in for a competent player, used to prove the level is
 * actually traversable rather than merely plausible on paper.
 *
 * It only uses the vocabulary the real controls expose — a direction, JUMP,
 * SNEAK and USE — and decides with the same information a player reads off the
 * screen: solid ground a stride ahead, a step to climb, a slot to duck under.
 */
class Bot(private val session: GameSession, private val dir: Float = 1f) {

    private var stalled = 0f
    private var jumpCooldown = 0f
    var useInteract = true
    /** When set, the bot only presses USE for these prompts. */
    var interactLabels: Set<String>? = null
    /** When false the bot stays low, only hopping obstacles that block it. */
    var climb = true

    private fun solids(): List<Box> {
        val out = ArrayList<Box>()
        for (s in session.room.solids) out.add(s.box)
        for (p in session.room.props) p.solid?.let { out.add(it) }
        return out
    }

    fun step(dt: Float): Float {
        val player = session.player
        val solids = solids()
        val x = player.x
        val y = player.y
        val moving = abs(player.vx) > 0.6f
        if (!moving && player.controlEnabled) stalled += dt else stalled = (stalled - dt * 2f).coerceAtLeast(0f)

        // How far to the edge of whatever we are standing on.
        var edgeDistance = Float.MAX_VALUE
        for (s in solids) {
            if (s.l - 0.35f <= x && x <= s.r + 0.35f && s.t > y - 0.15f && s.t < y + 0.45f) {
                val d = if (dir > 0f) s.r - x else x - s.l
                if (d < edgeDistance) edgeDistance = d
            }
        }
        // A ledge to climb, within a running jump.
        val stepAhead = solids.any {
            it.t < y - 0.15f && it.t > y - 1.45f &&
                (if (dir > 0f) it.l in x..(x + 1.75f) else it.r in (x - 1.75f)..x)
        }
        // A slot whose ceiling is too low to walk under.
        val lowSlot = solids.any {
            it.b < y - 0.25f && it.b > y - 1.70f && it.t < y - 1.6f &&
                it.l <= x + 2.4f && it.r >= x - 2.4f &&
                (if (dir > 0f) it.r >= x else it.l <= x)
        }

        // Jumps are taken at speed. A standing hop wastes the run-up and is
        // exactly how a real player fails these gaps.
        // Directional: a jump taken while still reversing wastes the run-up.
        val fast = player.vx * dir > 3.6f * player.speedScale
        val crouch = lowSlot || stalled > 0.7f
        jumpCooldown -= dt
        val wantJump = player.onGround && !crouch && jumpCooldown <= 0f &&
            ((climb && fast && (edgeDistance < 0.55f || stepAhead)) || stalled > 0.15f)
        if (wantJump) jumpCooldown = 0.25f

        val label = session.focusLabel
        val allowed = interactLabels?.contains(label) ?: label.isNotEmpty()
        val interact = useInteract && label.isNotEmpty() && allowed

        session.update(dt, dir, crouch, wantJump, interact)
        return dt
    }

    fun runFor(seconds: Float, dt: Float = 1f / 60f, until: () -> Boolean = { false }): Float {
        var t = 0f
        while (t < seconds) {
            if (until()) return t
            step(dt)
            t += dt
        }
        return t
    }

    companion object {
        fun session(stage: Int): GameSession {
            val s = GameSession(stage, 12345L, Sfx())
            s.camera.resize(1920, 1080)
            return s
        }
    }
}
