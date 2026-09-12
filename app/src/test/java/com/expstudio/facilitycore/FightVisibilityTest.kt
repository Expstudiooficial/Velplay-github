package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Monster
import com.expstudio.facilitycore.game.Stage2
import com.expstudio.facilitycore.game.Stage3
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A fight the player cannot see is not a fight.
 *
 * Chapter 3's hunters were being placed on whatever solid sat highest over
 * their x — which in every room is the ceiling slab. They spent both set-pieces
 * fifteen metres up, off the top of the screen, while the swings still landed.
 * This keeps them on the floor.
 */
class FightVisibilityTest {

    private val dt = 1f / 60f

    /** How far above the player's own feet a hunter may ever be and still count. */
    private val sameFloorTolerance = 5f

    private fun runFight(stage: Int, chapter: Int, room: String, seconds: Float): List<String> {
        val g = Bot.session(stage, chapter)
        g.enterRoomForTest(room, g.room.bounds.r * 0.5f, 0f)
        val problems = ArrayList<String>()
        var t = 0f
        while (t < seconds) {
            if (g.dialogueBlocking) g.tapDialogue()
            g.update(dt, 0f, false, false, false)
            t += dt
            for ((name, m) in listOf("monster" to g.monster, "monster2" to g.monster2)) {
                if (m.mode == Monster.Mode.HIDDEN) continue
                if (m.roomId != g.roomId()) continue
                val above = g.player.y - m.y
                if (above > sameFloorTolerance) {
                    problems.add(
                        "%s in %s is %.1f m above the player (y=%.1f)".format(name, room, above, m.y)
                    )
                    return problems
                }
                if (m.y < g.room.bounds.t + 1f) {
                    problems.add("%s in %s is inside the ceiling (y=%.1f)".format(name, room, m.y))
                    return problems
                }
            }
        }
        return problems
    }

    @Test
    fun chapter3HuntersStayOnTheFloorTheyAreHuntingOn() {
        val problems = ArrayList<String>()
        problems += runFight(Stage3.ARCHIVE, 3, "archive3", 6f)
        problems += runFight(Stage3.BOSS, 3, "core3", 6f)
        assertTrue("hunters off the floor:\n  " + problems.joinToString("\n  "), problems.isEmpty())
    }

    @Test
    fun chapter2LiftFightKeepsItInTheCar() {
        assertTrue(runFight(Stage2.LIFT_FIGHT, 2, "car", 5f).joinToString(), runFight(Stage2.LIFT_FIGHT, 2, "car", 5f).isEmpty())
    }

    /** Both hunters have to actually be in the room for the boss to be a boss. */
    @Test
    fun theLastFightHasBothOfThemInIt() {
        val g = Bot.session(Stage3.BOSS, chapter = 3)
        repeat(120) { g.update(dt, 0f, false, false, false) }
        assertTrue("the first hunter is not present", g.monster.mode != Monster.Mode.HIDDEN)
        assertTrue("the second hunter is not present", g.monster2.mode != Monster.Mode.HIDDEN)
        assertTrue("they are not in the player's room",
            g.monster.roomId == g.roomId() && g.monster2.roomId == g.roomId())
        assertTrue("they are the same thing twice", g.monster.kind != g.monster2.kind)
    }

    /**
     * The hoist has to take hold of him. It used to lift him to a fixed height
     * beside claws that could not reach the floor in the first place.
     */
    @Test
    fun theHoistActuallyGrabsThePlayer() {
        val g = Bot.session(Stage2.HOIST_ESCAPE, chapter = 2)
        g.enterRoomForTest("hoist", 11f, 0f)
        val sw = g.level.room("hoist").props
            .filterIsInstance<com.expstudio.facilitycore.game.KeySwitch>().first()
        g.monster.place("hoist", 5f, 0f, 1)
        g.monster.mode = Monster.Mode.CHASING
        g.onSwitchUsedForTest(sw)

        val claws = g.level.room("hoist").props
            .filterIsInstance<com.expstudio.facilitycore.game.Grabbers>().first()
        var lifted = 0f
        var heldByClaws = true
        var t = 0f
        while (t < 5f) {
            g.update(dt, 0f, false, false, false)
            t += dt
            if (claws.holding) {
                lifted = minOf(lifted, g.player.y)
                // Held means held: the body hangs from the claw tips, not near them.
                if (kotlin.math.abs((g.player.y - claws.tipY())) > 2.0f) heldByClaws = false
                if (kotlin.math.abs(g.player.x - claws.box.cx) > 0.6f) heldByClaws = false
            }
        }
        assertTrue("the claws never closed on him", lifted < -2.5f)
        assertTrue("he was lifted beside the claws rather than by them", heldByClaws)
    }

    /** And it has to walk away, not blink out of existence in plain sight. */
    @Test
    fun itLeavesTheHoistBayOnFootRatherThanVanishing() {
        val g = Bot.session(Stage2.HOIST_ESCAPE, chapter = 2)
        g.enterRoomForTest("hoist", 11f, 0f)
        val sw = g.level.room("hoist").props
            .filterIsInstance<com.expstudio.facilitycore.game.KeySwitch>().first()
        g.monster.place("hoist", 5f, 0f, 1)
        g.monster.mode = Monster.Mode.CHASING
        g.onSwitchUsedForTest(sw)

        var lastVisibleX = Float.NEGATIVE_INFINITY
        var t = 0f
        while (t < 6f) {
            g.update(dt, 0f, false, false, false)
            t += dt
            if (g.monster.mode != Monster.Mode.HIDDEN) lastVisibleX = g.monster.x
        }
        assertTrue("it never left", lastVisibleX > Float.NEGATIVE_INFINITY)
        assertTrue(
            "it was switched off inside the room instead of walking out of it (x=%.1f of %.1f)"
                .format(lastVisibleX, g.level.room("hoist").bounds.r),
            lastVisibleX > g.level.room("hoist").bounds.r
        )
    }
}
