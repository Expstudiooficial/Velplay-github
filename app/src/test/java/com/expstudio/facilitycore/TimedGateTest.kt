package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Chapter4
import com.expstudio.facilitycore.game.GameSession
import com.expstudio.facilitycore.game.SlapSwitch
import com.expstudio.facilitycore.game.Stage4
import com.expstudio.facilitycore.game.TimedGate
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A clock you cannot beat is not a puzzle, it is a wall with a countdown on it.
 *
 * Every timed gate in the game is played here the way a player plays it: stand
 * at the switch, hit it, run at the gate. If a competent run cannot get through
 * with time left over, the room is a dead end and the test says so.
 *
 * This exists because Subfloor 4's first gate asked for a 22 m run over three
 * obstacles on a 5.2 second clock. A flat sprint of that distance takes 5.1
 * seconds, so there was no route through it at all — the player hit the switch,
 * ran, watched it shut, walked back, and did it again forever.
 */
class TimedGateTest {

    private val dt = 1f / 60f

    /**
     * Room ids only. The props have to be read off the live session afterwards
     * — a prop from a separately built level is a different object, and the
     * interaction prompt compares by identity.
     */
    private fun roomsWithGates(): List<String> =
        Chapter4.build().rooms.values
            .filter { r -> r.props.any { it is TimedGate } && r.props.any { it is SlapSwitch } }
            .map { it.id }

    @Test
    fun thereAreTimedGatesToCheck() {
        assertTrue("no timed gates found at all", roomsWithGates().isNotEmpty())
    }

    @Test
    fun everyTimedGateCanBeBeatenByAGoodRun() {
        val failures = ArrayList<String>()
        for (id in roomsWithGates()) {
            val g = Bot.session(Stage4.DEEP4, chapter = 4)
            g.enterRoomForTest(id, 3f, 0f)
            val gate = g.room.props.filterIsInstance<TimedGate>().first()
            val sw = g.room.props.filterIsInstance<SlapSwitch>().first()
            g.enterRoomForTest(id, (sw.box.l + sw.box.r) * 0.5f, 0f)
            // Settle, then hit it the way the player does.
            repeat(30) { g.update(dt, 0f, false, false, false) }
            press(g, sw)

            val bot = Bot(g, 1f).apply { useReach = false }
            var t = 0f
            var through = -1f
            while (t < 30f && g.roomId() == id) {
                if (g.dialogueBlocking) g.tapDialogue()
                bot.step(dt)
                t += dt
                if (through < 0f && g.player.x > gate.box.r + 0.4f) through = t
                if (through >= 0f) break
            }
            when {
                through < 0f -> failures += "$id: never got through the gate at all"
                // A player is not a robot. If the only way through is a perfect
                // line with nothing to spare, the room will read as broken.
                through > gate.openSecondsForTest * 0.75f ->
                    failures += ("$id: got through with %.2fs of a %.1fs clock left — too tight"
                        .format(gate.openSecondsForTest - through, gate.openSecondsForTest))
            }
        }
        assertTrue("timed gates that cannot be beaten:\n  " + failures.joinToString("\n  "),
            failures.isEmpty())
    }

    /**
     * And it must never shut on top of the player.
     *
     * Stand in the doorway and let the clock run out. The gate has to wait.
     * A slab that becomes solid in the space a body occupies either squeezes
     * them out somewhere they did not choose or leaves them inside it, and
     * either way the player's own position is the thing that broke them.
     */
    @Test
    fun aGateWaitsForAPlayerStandingInIt() {
        for (id in roomsWithGates()) {
            val g = Bot.session(Stage4.DEEP4, chapter = 4)
            g.enterRoomForTest(id, 3f, 0f)
            val gate = g.room.props.filterIsInstance<TimedGate>().first()
            val sw = g.room.props.filterIsInstance<SlapSwitch>().first()
            g.enterRoomForTest(id, (sw.box.l + sw.box.r) * 0.5f, 0f)
            repeat(30) { g.update(dt, 0f, false, false, false) }
            press(g, sw)

            // Let it finish opening first — dropping a body into a slab that
            // is still solid is the test teleporting, not the game failing.
            var w = 0f
            while (w < 3f && gate.solid != null) {
                g.update(dt, 0f, false, false, false); w += dt
            }

            // Park in the doorway and wait out the whole clock, and then some.
            val doorX = (gate.box.l + gate.box.r) * 0.5f
            g.enterRoomForTest(id, doorX, 0f)
            var t = 0f
            while (t < gate.openSecondsForTest + 4f) {
                if (g.dialogueBlocking) g.tapDialogue()
                g.update(dt, 0f, false, false, false)
                t += dt
            }

            assertTrue(
                "$id: the gate closed on a player standing in it",
                gate.solid == null
            )
            assertTrue(
                "$id: standing still in the doorway moved the player %.2f m".format(
                    kotlin.math.abs(g.player.x - doorX)
                ),
                kotlin.math.abs(g.player.x - doorX) < 0.6f
            )
        }
    }

    /** And it does still close, once the doorway is clear. */
    @Test
    fun aGateClosesOnceTheDoorwayIsClear() {
        for (id in roomsWithGates()) {
            val g = Bot.session(Stage4.DEEP4, chapter = 4)
            g.enterRoomForTest(id, 3f, 0f)
            val gate = g.room.props.filterIsInstance<TimedGate>().first()
            val sw = g.room.props.filterIsInstance<SlapSwitch>().first()
            g.enterRoomForTest(id, (sw.box.l + sw.box.r) * 0.5f, 0f)
            repeat(30) { g.update(dt, 0f, false, false, false) }
            press(g, sw)

            var t = 0f
            while (t < gate.openSecondsForTest + 4f) {
                if (g.dialogueBlocking) g.tapDialogue()
                g.update(dt, 0f, false, false, false)
                t += dt
            }
            assertTrue("$id: the gate never closed again", gate.solid != null)
        }
    }

    private fun press(g: GameSession, sw: SlapSwitch) {
        var t = 0f
        while (t < 6f && !sw.used) {
            if (g.dialogueBlocking) { g.tapDialogue(); t += dt; continue }
            val onIt = g.focusProp === sw && g.focusLabel.isNotEmpty()
            g.update(dt, 0f, false, false, onIt)
            t += dt
        }
        assertTrue("could not hit the switch at all", sw.used)
    }
}
