package com.expstudio.facilitycore.game

import com.expstudio.facilitycore.core.MathX
import kotlin.math.abs

/**
 * How the thing behind you actually moves.
 *
 * The old pursuit had none of its own. It mapped the player's position onto the
 * chase route and placed the monster a fixed distance back along it, which
 * means it only ever moved when the player did, at exactly the player's speed,
 * and stopping made it stop too. It was a decal, not a pursuer.
 *
 * This gives it a body: a speed of its own, ground it has to follow, walls it
 * has to climb, and doorways it has to travel through room by room. It closes
 * when it is behind and eases when it is close — not to cheat, but because a
 * pursuer that is always exactly one stride away is as unconvincing as one
 * glued to your feet.
 */
class Pursuit {

    /** The rooms it will travel through, in order, and the floor of each. */
    private var legs: List<ChaseLeg> = emptyList()
    private var legIndex = 0

    /** Distance travelled along the whole route, in metres. */
    var travelled = 0f
        private set

    private var speed = 0f
    /** A smoothed measure of how fast the player is actually getting away. */
    private var advanceRate = BASE_SPEED
    private var lastPlayerAlong = Float.NaN
    /** Seconds of easing left this chase. Capped so it cannot be farmed. */
    private var assist = ASSIST_BUDGET
    /** Rises while it is walled in, and carries it over when it runs out. */
    private var clamber = 0f

    var routeLength = 0f
        private set

    fun setRoute(route: List<ChaseLeg>) {
        legs = route
        routeLength = route.sumOf { it.length.toDouble() }.toFloat().coerceAtLeast(0.001f)
    }

    val hasRoute: Boolean get() = legs.isNotEmpty()

    /** Puts it back at the start of the route, ready to come again. */
    fun reset(m: Monster) {
        legIndex = 0
        travelled = 0f
        speed = BASE_SPEED
        clamber = 0f
        advanceRate = BASE_SPEED
        lastPlayerAlong = Float.NaN
        assist = ASSIST_BUDGET
        legs.firstOrNull()?.let {
            m.roomId = it.roomId
            m.x = it.fromX
            m.y = it.groundY
            m.facing = if (it.toX > it.fromX) 1 else -1
        }
    }

    /**
     * Advances the pursuit by one frame.
     *
     * Returns true when it has hold of the player.
     */
    fun update(g: GameSession, m: Monster, dt: Float): Boolean {
        if (legs.isEmpty()) return false
        val leg = legs[legIndex.coerceIn(0, legs.size - 1)]
        val dir = if (leg.toX >= leg.fromX) 1f else -1f

        // How far ahead the player is along the same route. Null when they have
        // left it — through a side door, or into a vent — in which case it keeps
        // coming at its own pace rather than freezing.
        val playerAlong = routeDistanceOf(g.room.id, g.player.x)
        val gap = if (playerAlong != null) playerAlong - travelled else COMFORT_GAP

        // How fast the player is actually getting away, smoothed.
        //
        // Neither the crouch hint nor a plain stall timer works here. The hint
        // clears the moment they leave the ground, and jumping at the thing
        // blocking you is exactly what a stuck player does — which also nudges
        // them far enough each time to keep resetting a stall timer. What
        // separates stuck from moving is the rate, over a second or so.
        if (playerAlong != null) {
            if (lastPlayerAlong.isNaN()) lastPlayerAlong = playerAlong
            val moved = (playerAlong - lastPlayerAlong) / dt.coerceAtLeast(0.0001f)
            lastPlayerAlong = playerAlong
            val blend = (dt / RATE_SMOOTHING).coerceIn(0f, 1f)
            advanceRate += (moved.coerceIn(-10f, 10f) - advanceRate) * blend
        }
        // Only for someone who is trying. Standing still is not being stuck,
        // and must never buy breathing room.
        val easing = g.playerStruggling && advanceRate < STUCK_RATE && assist > 0f
        if (easing) assist -= dt

        // Closing speed. Far behind and it lengthens its stride; close and it
        // holds station, so the last few metres are a threat rather than a
        // coin toss.
        val want = when {
            // Stuck on something they have not worked out yet. It backs off,
            // but only for a few seconds a chase — enough to learn a mechanic,
            // never enough to wait one out.
            easing -> STUCK_SPEED
            gap > FAR_GAP -> SPRINT_SPEED
            gap > COMFORT_GAP -> MathX.lerp(BASE_SPEED, SPRINT_SPEED, (gap - COMFORT_GAP) / (FAR_GAP - COMFORT_GAP))
            gap > CLOSE_GAP -> BASE_SPEED
            else -> CLOSE_SPEED
        }
        speed = MathX.approach(speed, want, ACCEL, dt)

        val step = speed * dt
        m.x += dir * step
        travelled += step
        m.facing = dir.toInt()

        // Ground. It walks the same floors the player does, and clambers over
        // anything it cannot step onto rather than standing at it forever.
        val inPlayersRoom = m.roomId == g.room.id
        val surface = if (inPlayersRoom) surfaceUnder(g, m.x, m.y) else leg.groundY
        if (surface != null) {
            m.y = MathX.approach(m.y, surface, CLIMB_RATE, dt)
            clamber = 0f
        } else {
            clamber += dt
            if (clamber > CLAMBER_SECONDS) m.y -= CLIMB_RATE * dt
        }

        // Through the doorway into the next room on the route.
        val past = if (dir > 0f) m.x >= leg.toX else m.x <= leg.toX
        if (past && legIndex < legs.size - 1) {
            legIndex++
            val next = legs[legIndex]
            m.roomId = next.roomId
            m.x = next.fromX
            m.y = next.groundY
        } else if (past) {
            m.x = leg.toX
        }

        return inPlayersRoom && m.touching(g.player)
    }

    /** Where a point in a room falls along the route, in metres, or null. */
    private fun routeDistanceOf(roomId: String, x: Float): Float? {
        var acc = 0f
        for (leg in legs) {
            if (leg.roomId == roomId) {
                val along = if (leg.toX >= leg.fromX) x - leg.fromX else leg.fromX - x
                return acc + along.coerceIn(0f, leg.length)
            }
            acc += leg.length
        }
        return null
    }

    /**
     * The floor at [x], given it is standing at [from]. Null when there is
     * nothing it can reach — which is what starts it climbing.
     */
    private fun surfaceUnder(g: GameSession, x: Float, from: Float): Float? {
        var best: Float? = null
        for (s in g.room.solids) {
            if (x < s.box.l - 0.3f || x > s.box.r + 0.3f) continue
            val top = s.box.t
            if (top < from - STEP_UP) continue
            if (top > from + DROP) continue
            if (best == null || top < best!!) best = top
        }
        return best
    }

    /** 0..1 along the route, for anything that wants to show the pressure. */
    val progress: Float get() = (travelled / routeLength).coerceIn(0f, 1f)

    companion object {
        /**
         * Its cruise. Well under a running player's 5 m/s, because a player
         * threading crates and crawl gaps does not average their top speed —
         * at 4.55 the pursuit never lost ground even to someone playing well,
         * and the whole chase became a coin toss at the first obstacle.
         */
        const val BASE_SPEED = 3.9f
        /** What it opens up to when it has lost ground. */
        const val SPRINT_SPEED = 7.4f
        /** What it drops to once it is on top of you, so the kill is earned. */
        /**
         * What it drops to once it is on your heels.
         *
         * Deliberately below the pace of a player who is playing badly but
         * still moving. Being hunted at arm's length should be the worst part
         * of the chase, not an automatic kill — what kills you is stopping,
         * and stopping earns no easing at all.
         */
        const val CLOSE_SPEED = 2.1f
        const val ACCEL = 5.5f
        /** What it slows to while the player is stuck on a mechanic. */
        const val STUCK_SPEED = 1.5f
        /** Below this many metres a second, they are stuck on something. */
        const val STUCK_RATE = 1.7f
        /** Seconds the advance rate is averaged over. */
        const val RATE_SMOOTHING = 0.9f
        /** Total seconds of easing available in one chase. */
        const val ASSIST_BUDGET = 8f

        const val CLOSE_GAP = 5.0f
        const val COMFORT_GAP = 8f
        const val FAR_GAP = 22f

        const val STEP_UP = 1.3f
        const val DROP = 3.2f
        const val CLIMB_RATE = 6.5f
        /** How long it will stand against a wall before going over it. */
        const val CLAMBER_SECONDS = 0.35f
    }
}
