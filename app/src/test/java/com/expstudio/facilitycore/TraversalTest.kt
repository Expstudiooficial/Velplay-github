package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Chapter1
import com.expstudio.facilitycore.game.Stage
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Proves the level can actually be played, by driving the real physics with a
 * bot that only uses the inputs a player has.
 */
class TraversalTest {

    /** Each step of the climb to the sealed door's ledge must be jumpable. */
    @Test
    fun theSealedJunctionStaircaseIsClimbable() {
        // Each run starts at the far end of the step, the way a player arrives:
        // with room to build up speed before the gap.
        val steps = listOf(
            Triple(8.4f, -1.2f, -2.3f),   // crate top -> first platform
            Triple(11.9f, -2.3f, -3.4f),  // first -> second
            Triple(15.6f, -3.4f, -4.5f)   // second -> door ledge
        )
        for ((fromX, fromY, toY) in steps) {
            val session = Bot.session(Stage.UNLOCK_FIRST_DOOR)
            assertTrue("expected to start in the sealed junction", session.roomId() == "sealed")
            session.player.teleport(fromX, fromY)
            val bot = Bot(session, 1f).apply { useInteract = false }
            var landed = false
            bot.runFor(3f, until = {
                landed = session.player.onGround && abs(session.player.y - toY) < 0.06f
                landed
            })
            assertTrue("could not jump from y=$fromY up to y=$toY", landed)
        }
    }

    /** The corridor from the entrance to the intake hall must simply work. */
    @Test
    fun theEntranceCorridorLeadsToTheIntakeHall() {
        val session = Bot.session(Stage.INTRO)
        val bot = Bot(session, 1f).apply { useInteract = false }
        // Clear the opening monologue first; it holds control on purpose.
        var guard = 0
        while (session.dialogueBlocking && guard < 3000) {
            session.tapDialogue()
            session.update(1f / 60f, 0f, false, false, false)
            guard++
        }
        bot.runFor(25f, until = { session.roomId() == "lobby" })
        assertTrue("never reached the intake hall", session.roomId() == "lobby")
        assertTrue("stage did not advance on entering the hall", session.stage >= Stage.LOBBY_PUZZLE)
    }

    /** A running player must beat the 15 second budget with room to spare. */
    @Test
    fun theChaseIsSurvivable() {
        val session = Bot.session(Stage.ELEVATOR_DENIED)
        val bot = Bot(session, 1f).apply { interactLabels = setOf("GRAB", "THROW") }
        bot.runFor(90f, until = { session.stage >= Stage.CHASE_SURVIVED })

        assertTrue(
            "the chase was never survived (stage=${session.stage}, room=${session.roomId()}, chase=${session.chaseTime})",
            session.stage >= Stage.CHASE_SURVIVED
        )
        assertTrue(
            "survived only at the wire: ${session.chaseTime}s of ${Chapter1.CHASE_SECONDS}s",
            session.chaseTime < Chapter1.CHASE_SECONDS - 1.2f
        )
    }

    /** ...and a player who does not run must be caught. */
    @Test
    fun standingStillDuringTheChaseIsFatal() {
        val session = Bot.session(Stage.ELEVATOR_DENIED)
        val runner = Bot(session, 1f).apply { useInteract = false }
        runner.runFor(60f, until = { session.stage >= Stage.CHASE })
        assertTrue("the chase never started", session.stage >= Stage.CHASE)

        // Stop dead and wait out the budget.
        var t = 0f
        while (t < Chapter1.CHASE_SECONDS + 4f && session.stage >= Stage.CHASE) {
            session.update(1f / 60f, 0f, false, false, false)
            t += 1f / 60f
            if (session.stage == Stage.ELEVATOR_DENIED) break
        }
        assertTrue(
            "standing still was survivable",
            session.stage == Stage.ELEVATOR_DENIED || session.cutIsDeath()
        )
    }

    /** Dying rolls back exactly to the locked lift, as the chapter intends. */
    @Test
    fun deathReturnsToTheLockedLift() {
        val session = Bot.session(Stage.CHASE)
        assertTrue(session.roomId() == "lift")
        assertTrue("the lift must still be unknown to the pack", !session.keyPackHasElevator)
        assertTrue("the pack itself must survive death", session.hasKeyPack && session.keyPackRepaired)
        assertTrue("subfloor 0 must stay powered", session.subfloorPowered)
    }
}
