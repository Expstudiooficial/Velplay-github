package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Chapter4
import com.expstudio.facilitycore.game.Stage
import com.expstudio.facilitycore.game.Stage2
import com.expstudio.facilitycore.game.Stage3
import com.expstudio.facilitycore.game.Stage4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dying should cost you a corridor, not an afternoon.
 *
 * Every chapter is built as set pieces with long composed runs between them,
 * and the stage checkpoints sit on the set pieces. Under Subfloor 4 that put
 * fifty rooms between two of them: die in the last and you walked the whole
 * run again. Composed rooms carry no state but their own geometry, so the
 * furthest one reached is always a safe place to come back to.
 */
class CheckpointTest {

    private val dt = 1f / 60f

    @Test
    fun theLongRunsAreMarkedAsPlacesToComeBackTo() {
        val level = Chapter4.build()
        val corridors = level.rooms.values.count { it.waypoint }
        assertTrue("no corridor in Chapter 4 is a waypoint", corridors >= 60)
        // Set pieces must not be: their state belongs to their own stage.
        for (id in listOf("core4", "lift5", "bigdoor4", "ren4", "vent4", "liftup4",
                          "lock4a", "lock4b", "lock4c")) {
            assertTrue("$id is a set piece and must not be a waypoint",
                level.rooms.getValue(id).waypoint == false)
        }
    }

    @Test
    fun dyingDeepInARunComesBackToTheSameRun() {
        val g = Bot.session(Stage4.DEEP4, chapter = 4)
        // Walk in deliberately, the way the player gets there.
        g.enterRoomForTest("h30", 4f, 0f)
        repeat(10) { g.update(dt, 0f, false, false, false) }
        assertEquals("entering a corridor did not record it", "h30", g.waypointRoom)

        g.killPlayer()
        var t = 0f
        while (t < 6f && g.roomId() != "h30") { g.update(dt, 0f, false, false, false); t += dt }
        assertEquals(
            "a death thirty rooms in sent the player back to the start of the run",
            "h30", g.roomId()
        )
    }

    /**
     * No chapter may bury more than a few rooms between places to come back to.
     *
     * Counted as rooms per recovery point, where a recovery point is either a
     * stage checkpoint or a composed corridor. The hand-built chapters were
     * always dense — Chapter 1 is thirteen rooms across eight checkpoints — but
     * the composed ones were not: Chapter 3 ran at nearly seven rooms per
     * checkpoint and Chapter 4 at almost nine, which is what a death fifty
     * rooms into Subfloor 4 actually cost.
     */
    @Test
    fun noChapterBuriesTheWayBack() {
        val worst = ArrayList<String>()
        for ((chapter, stage) in listOf(
            1 to Stage.FIND_KEYPACK, 2 to Stage2.ARRIVAL,
            3 to Stage3.DESCEND, 4 to Stage4.DESCENT
        )) {
            val g = Bot.session(stage, chapter = chapter)
            val rooms = g.level.rooms.size
            val corridors = g.level.rooms.values.count { it.waypoint }
            val checkpoints = (0..40).map { g.script.checkpointFor(it) }.distinct().size
            val perRecovery = rooms.toFloat() / (corridors + checkpoints)
            if (perRecovery > 3f) {
                worst += "chapter %d: %d rooms, %.1f per place to come back to"
                    .format(chapter, rooms, perRecovery)
            }
        }
        assertTrue("chapters that make a death too expensive:\n  " + worst.joinToString("\n  "),
            worst.isEmpty())
    }

    /**
     * A waypoint from a different checkpoint is not used.
     *
     * Regressing a stage rebuilds the world differently, and a corridor
     * remembered from later may not be on the route any more.
     */
    @Test
    fun aWaypointFromAnotherCheckpointIsIgnored() {
        val g = Bot.session(Stage4.DEEP4, chapter = 4)
        g.enterRoomForTest("h30", 4f, 0f)
        repeat(10) { g.update(dt, 0f, false, false, false) }
        assertEquals("h30", g.waypointRoom)

        // Send it back to an earlier checkpoint entirely.
        g.restart(Stage4.DESCENT)
        assertTrue(
            "a stale waypoint survived a regression to an earlier checkpoint (room=${g.roomId()})",
            g.roomId() != "h30"
        )
    }
}
