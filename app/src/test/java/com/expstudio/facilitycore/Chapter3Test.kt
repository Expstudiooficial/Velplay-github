package com.expstudio.facilitycore

import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.game.Chapter3
import com.expstudio.facilitycore.game.Compose
import com.expstudio.facilitycore.game.Door
import com.expstudio.facilitycore.game.Player
import com.expstudio.facilitycore.game.Reach
import com.expstudio.facilitycore.game.ReachAnchor
import com.expstudio.facilitycore.game.Solid
import com.expstudio.facilitycore.game.Stage3
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Structural checks over Chapter 3.
 *
 * Most of the chapter is composed rather than authored, so these matter more
 * here than they did for Chapters 1 and 2: a bad slot template would not be one
 * broken room, it would be thirty.
 */
class Chapter3Test {

    private val level = Chapter3.build()

    /**
     * The tallest ledge a standing jump plus a mantle gets onto: the jump apex
     * is JUMP_SPEED^2 / 2*GRAVITY = 1.57 m, and the mantle adds its rise.
     */
    private val MAX_CLIMB = 1.57f + Player.MANTLE_RISE

    @Test
    fun everyExitPointsAtARealRoom() {
        for (room in level.rooms.values) {
            for (exit in room.exits) {
                assertNotNull("${room.id} exits to unknown room '${exit.toRoom}'", level.rooms[exit.toRoom])
            }
        }
    }

    @Test
    fun everyRoomIsReachableFromTheObservationDeck() {
        val seen = HashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add("obs3")
        seen.add("obs3")
        while (queue.isNotEmpty()) {
            val room = level.room(queue.removeFirst())
            for (exit in room.exits) if (seen.add(exit.toRoom)) queue.add(exit.toRoom)
        }
        // The Subfloor 3 head is reached by riding the car, which is a cutscene
        // rather than an exit, so the walk graph starts again there.
        val fromLift = HashSet<String>()
        val q2 = ArrayDeque<String>()
        q2.add("e0")
        fromLift.add("e0")
        while (q2.isNotEmpty()) {
            val room = level.room(q2.removeFirst())
            for (exit in room.exits) if (fromLift.add(exit.toRoom)) q2.add(exit.toRoom)
        }
        for (id in level.rooms.keys) {
            assertTrue("room '$id' is unreachable", id in seen || id in fromLift)
        }
    }

    @Test
    fun spawnPointsAreNotInsideGeometry() {
        for (room in level.rooms.values) {
            for (exit in room.exits) {
                val target = level.room(exit.toRoom)
                val box = Box(
                    exit.spawnX - Player.WIDTH * 0.5f,
                    exit.spawnY - Player.CROUCH_HEIGHT,
                    exit.spawnX + Player.WIDTH * 0.5f,
                    exit.spawnY
                )
                for (solid in target.solids) {
                    assertFalse(
                        "spawn from ${room.id} into ${exit.toRoom} at " +
                            "(${exit.spawnX}, ${exit.spawnY}) is inside geometry",
                        box.overlaps(solid.box)
                    )
                }
            }
        }
    }

    @Test
    fun checkpointSpawnsAreNotInsideGeometry() {
        for (stage in 0..Stage3.COMPLETE) {
            val (roomId, x, y) = Chapter3.spawnFor(stage)
            val room = level.rooms[roomId]
            assertNotNull("stage $stage spawns into unknown room '$roomId'", room)
            val box = Box(
                x - Player.WIDTH * 0.5f, y - Player.CROUCH_HEIGHT,
                x + Player.WIDTH * 0.5f, y
            )
            for (solid in room!!.solids) {
                assertFalse("stage $stage spawn is inside geometry of $roomId", box.overlaps(solid.box))
            }
        }
    }

    @Test
    fun everyGatedExitIsGatedByADoorInThatRoom() {
        for (room in level.rooms.values) {
            for (exit in room.exits) {
                val gate = exit.gate ?: continue
                assertTrue(
                    "${room.id} is gated by a door it does not own",
                    room.props.any { it === gate }
                )
            }
        }
    }

    @Test
    fun everyDoorNameIsUniqueWithinItsRoom() {
        for (room in level.rooms.values) {
            val names = room.props.filterIsInstance<Door>().map { it.name }
            assertTrue(
                "${room.id} has duplicate door names: $names",
                names.size == names.toSet().size
            )
        }
    }

    /**
     * The composer's contract. Every crate or platform a walking route meets has
     * to be climbable from the floor beside it, and every low gap has to be tall
     * enough to crawl through.
     */
    @Test
    fun everyLedgeCanBeClimbedFromSomethingBelowIt() {
        for (room in level.rooms.values) {
            for (s in room.solids) {
                if (s.kind == Solid.Kind.STRUCTURE) continue
                // A ledge with a bar on it is meant to be out of jump range —
                // that is the whole reason the hand exists.
                if (room.props.any { it is ReachAnchor && it.kind == ReachAnchor.Kind.BAR &&
                        it.box.r >= s.box.l - 0.6f && it.box.l <= s.box.r + 0.6f &&
                        it.box.b <= s.box.t + 1.2f && it.box.t >= s.box.t - 1.2f }) continue
                // Supports: the floor, and the top of anything lower that is
                // close enough horizontally to jump or step from.
                // The cheapest climb, not the highest support: a 4 m shelf with a
                // box 1 m under it is fine even though the floor is also "below".
                var best = Float.POSITIVE_INFINITY
                if (s.box.t < 0f) best = 0f
                for (other in room.solids) {
                    if (other === s) continue
                    val top = other.box.t
                    if (top <= s.box.t) continue
                    if (other.box.r < s.box.l - Compose.MAX_GAP) continue
                    if (other.box.l > s.box.r + Compose.MAX_GAP) continue
                    if (top < best) best = top
                }
                val climb = best - s.box.t
                assertTrue(
                    "${room.id}: a ledge at ${"%.2f".format(-s.box.t)} m (x=${"%.1f".format(s.box.l)}) " +
                        "needs a ${"%.2f".format(climb)} m climb, which is beyond a jump and a mantle",
                    climb <= MAX_CLIMB + 0.01f
                )
            }
        }
    }

    /** A crouch gap that is shorter than a crouched body is a wall. */
    @Test
    fun everyLowGapIsTallEnoughToCrawlThrough() {
        for (room in level.rooms.values) {
            for (s in room.solids) {
                if (s.kind != Solid.Kind.STRUCTURE) continue
                val underside = s.box.b
                // Only the composer's duck blocks hang clear of the floor.
                if (underside >= -0.05f || underside < -3f) continue
                val clearance = -underside
                assertTrue(
                    "${room.id}: a gap of ${"%.2f".format(clearance)} m cannot be crawled",
                    clearance >= Player.CROUCH_HEIGHT + 0.1f
                )
            }
        }
    }

    /**
     * Coming out of a crawl the body is still crouched, and it cannot stand up
     * while anything overhangs it. So the metres just past a low block have to
     * be clear of anything that needs a jump, or the crawl is a one-way trip
     * into a wall.
     */
    @Test
    fun nothingNeedingAJumpSitsRightAfterACrawl() {
        for (room in level.rooms.values) {
            for (duck in room.solids) {
                if (duck.kind != Solid.Kind.STRUCTURE) continue
                val underside = duck.box.b
                if (underside >= -0.05f || underside < -3f) continue
                for (s in room.solids) {
                    if (s === duck || s.box.t >= 0f) continue
                    val step = -s.box.t
                    if (step < 0.25f) continue
                    val gapAfter = s.box.l - duck.box.r
                    val gapBefore = duck.box.l - s.box.r
                    val offending = when {
                        gapAfter in 0f..CRAWL_CLEARANCE -> gapAfter
                        gapBefore in 0f..CRAWL_CLEARANCE -> gapBefore
                        else -> Float.NaN
                    }
                    assertFalse(
                        "${room.id}: a ${"%.2f".format(step)} m step at x=${"%.1f".format(s.box.l)} sits " +
                            "${"%.2f".format(offending)} m from the crawl block at " +
                            "x=${"%.1f".format(duck.box.l)}",
                        !offending.isNaN()
                    )
                }
            }
        }
    }

    /** Room to stand up and take a run-up in. */
    private val CRAWL_CLEARANCE = 1.6f

    /**
     * Every bar in the chapter has to be inside the hand's range of somewhere the
     * player can actually stand — otherwise the room is a dead end with a
     * beautiful glowing handle in it.
     */
    @Test
    fun everyReachAnchorIsInRangeOfSomeStandingSpot() {
        for (room in level.rooms.values) {
            for (p in room.props) {
                if (p !is ReachAnchor) continue
                val gx = (p.box.l + p.box.r) * 0.5f
                val gy = (p.box.t + p.box.b) * 0.5f
                val stands = standingSpots(room.id)
                val ok = stands.any { (sx, sy) ->
                    hypot(gx - sx, gy - (sy - Player.STAND_HEIGHT * 0.72f)) <= Reach.RANGE
                }
                assertTrue("${room.id}: anchor '${p.id}' is out of reach from anywhere", ok)
            }
        }
    }

    /** Floor level plus the top of every platform and crate. */
    private fun standingSpots(roomId: String): List<Pair<Float, Float>> {
        val room = level.room(roomId)
        val out = ArrayList<Pair<Float, Float>>()
        var x = room.bounds.l + 1f
        while (x < room.bounds.r - 1f) {
            out.add(x to 0f)
            x += 1f
        }
        for (s in room.solids) {
            if (s.kind == Solid.Kind.STRUCTURE) continue
            out.add((s.box.l + s.box.r) * 0.5f to s.box.t)
        }
        return out
    }

    /** The chapter is the size the outline says it is. */
    @Test
    fun theChapterIsAsLongAsItWasSpecified() {
        val count = level.rooms.size
        assertTrue("Chapter 3 has only $count rooms", count >= 80)
        assertTrue("Chapter 3 has $count rooms, which is more than intended", count <= 115)
    }

    @Test
    fun composedRoomsAreDeterministic() {
        val a = Chapter3.build()
        val b = Chapter3.build()
        for (id in a.rooms.keys) {
            val ra = a.room(id)
            val rb = b.room(id)
            assertTrue("$id changed shape between builds", abs(ra.bounds.w - rb.bounds.w) < 0.001f)
            assertTrue("$id changed solid count between builds", ra.solids.size == rb.solids.size)
        }
    }
}
