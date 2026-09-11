package com.expstudio.facilitycore

import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.game.Chapter1
import com.expstudio.facilitycore.game.GameSession
import com.expstudio.facilitycore.game.Player
import com.expstudio.facilitycore.game.Room
import com.expstudio.facilitycore.game.Solid
import com.expstudio.facilitycore.game.Stage
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The worst case a player can put themselves in: walk straight into an obstacle
 * so the run-up is gone, then tap JUMP while still holding the direction.
 *
 * Every obstacle standing on the floor must be clearable from that position,
 * from both sides. This is the regression guard for "I cannot jump over this".
 */
class ObstacleTest {

    private val dt = 1f / 60f

    private fun floorObstacles(room: Room): List<Box> =
        room.solids
            .filter { it.kind == Solid.Kind.CRATE }
            // Sitting on the floor, and low enough to be part of a walking route.
            .filter { abs(it.box.b) < 0.02f && it.box.h in 0.3f..2.4f }
            .map { it.box }

    private fun clears(room: Room, obstacle: Box, dir: Float): Boolean {
        // A session is only used for its solid list and physics; the player is
        // placed by hand, flush against the obstacle with no speed at all.
        val g = GameSession(Stage.INTRO, 1L, Sfx())
        g.camera.resize(1920, 1080)
        val solids = ArrayList<Box>()
        for (s in room.solids) solids.add(s.box)
        for (p in room.props) p.solid?.let { solids.add(it) }

        val player = Player()
        val startX = if (dir > 0f) obstacle.l - Player.WIDTH * 0.5f - 0.005f
        else obstacle.r + Player.WIDTH * 0.5f + 0.005f
        // Stand on whatever surface is actually under that spot: obstacles are
        // often stacked, so the approach is not always at floor level.
        var supportY = 0f
        for (s in solids) {
            val spans = s.l < startX + Player.WIDTH * 0.5f && startX - Player.WIDTH * 0.5f < s.r
            if (spans && s.t <= 0.001f && s.t >= -2.6f && s.t < supportY) supportY = s.t
        }
        player.teleport(startX, supportY)

        var t = 0f
        var jumped = false
        while (t < 2.5f) {
            // Settle on the ground for a beat, then hold the direction and jump.
            val press = !jumped && t > 0.15f && player.onGround
            if (press) jumped = true
            player.update(dt, dir, false, press, solids)
            t += dt
            val onTop = player.onGround && abs(player.y - obstacle.t) < 0.08f &&
                player.x > obstacle.l - Player.WIDTH * 0.5f && player.x < obstacle.r + Player.WIDTH * 0.5f
            val past = player.onGround &&
                (if (dir > 0f) player.x > obstacle.r else player.x < obstacle.l)
            if (onTop || past) return true
        }
        return false
    }

    @Test
    fun everyFloorObstacleCanBeClearedFromAStandstill() {
        val level = Chapter1.build()
        var checked = 0
        for (room in level.rooms.values) {
            for (obstacle in floorObstacles(room)) {
                checked++
                assertTrue(
                    "in ${room.id}, the obstacle at x=${obstacle.l}..${obstacle.r} " +
                        "(${obstacle.h}m tall) cannot be climbed moving right",
                    clears(room, obstacle, 1f)
                )
                assertTrue(
                    "in ${room.id}, the obstacle at x=${obstacle.l}..${obstacle.r} " +
                        "(${obstacle.h}m tall) cannot be climbed moving left",
                    clears(room, obstacle, -1f)
                )
            }
        }
        assertTrue("the level has no floor obstacles to check", checked >= 10)
    }
}
