package com.expstudio.facilitycore

import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.game.Chapter1
import com.expstudio.facilitycore.game.Player
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ledge pull-up must make honest obstacles fair without letting the player
 * climb out of the level or skip a route the chapter depends on.
 */
class MantleTest {

    private val dt = 1f / 60f
    private val level = Chapter1.build()

    private fun solidsOf(roomId: String): List<Box> {
        val room = level.room(roomId)
        val out = ArrayList<Box>()
        for (s in room.solids) out.add(s.box)
        for (p in room.props) p.solid?.let { out.add(it) }
        return out
    }

    /** Jumps and mantles hard for a while; reports the highest point reached. */
    private fun highestReach(roomId: String, startX: Float, startY: Float, dir: Float): Float {
        val solids = solidsOf(roomId)
        val player = Player()
        player.teleport(startX, startY)
        var best = startY
        var t = 0f
        while (t < 8f) {
            val jump = player.onGround
            player.update(dt, dir, false, jump, solids)
            if (player.onGround && player.y < best) best = player.y
            t += dt
        }
        return best
    }

    @Test
    fun theSealedDoorLedgeStillRequiresTheStaircase() {
        // Hammering jump at the foot of the ledge must not reach it: the climb
        // is the puzzle, and the pull-up must not shortcut it.
        val reached = highestReach("sealed", 23f, 0f, 1f)
        assertTrue(
            "the door ledge at -4.5 was reached from the floor (got $reached)",
            reached > -2.0f
        )
    }

    @Test
    fun theCollapsedBulkheadStillHasToBeCrawled() {
        // maze2's bulkhead is 7.45 m of solid; no amount of jumping gets over it.
        val reached = highestReach("maze2", 5.5f, 0f, 1f)
        assertTrue("the chase bulkhead was climbed instead of crawled", reached > -2.0f)
    }

    @Test
    fun theDuctLipStillHasToBeCrouchedUnder() {
        // The Data Spine duct is entered by crouching; mantling onto its lip
        // would put the player on top of the level geometry.
        val reached = highestReach("circuit", 27f, 0f, 1f)
        assertTrue("the duct lip was climbed onto", reached > -2.0f)
    }

    @Test
    fun aPullUpNeverEndsInsideGeometry() {
        // Sweep every room: land a jump against every surface and confirm the
        // player always finishes in free space.
        for (room in level.rooms.values) {
            val solids = solidsOf(room.id)
            for (s in room.solids) {
                if (s.box.h > 2.5f || s.box.b > 0.02f) continue
                for (dir in listOf(-1f, 1f)) {
                    val player = Player()
                    val startX = if (dir > 0f) s.box.l - 1.2f else s.box.r + 1.2f
                    // Crates are stacked shoulder to shoulder, so the approach
                    // spot is often on top of a neighbour rather than the floor.
                    var startY = 0f
                    for (candidate in solids) {
                        val spans = candidate.l < startX + Player.WIDTH * 0.5f &&
                            startX - Player.WIDTH * 0.5f < candidate.r
                        if (spans && candidate.t <= 0.001f && candidate.t >= -2.6f && candidate.t < startY) {
                            startY = candidate.t
                        }
                    }
                    player.teleport(startX, startY)
                    // If that spot is not free standing room, there is nothing
                    // meaningful to test from it.
                    val start = player.bounds()
                    if (solids.any { start.inflated(-0.02f).overlaps(it) }) continue
                    var t = 0f
                    while (t < 2.5f) {
                        player.update(dt, dir, false, player.onGround, solids)
                        t += dt
                        val b = player.bounds()
                        for (solid in solids) {
                            assertTrue(
                                "in ${room.id} the player ended up inside geometry at " +
                                    "(${player.x}, ${player.y})",
                                !b.inflated(-0.02f).overlaps(solid)
                            )
                        }
                    }
                }
            }
        }
    }
}
