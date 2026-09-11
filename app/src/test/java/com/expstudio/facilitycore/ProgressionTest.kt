package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.ArchivePanel
import com.expstudio.facilitycore.game.CableCoil
import com.expstudio.facilitycore.game.Chapter1
import com.expstudio.facilitycore.game.ConnectionStation
import com.expstudio.facilitycore.game.Door
import com.expstudio.facilitycore.game.Elevator
import com.expstudio.facilitycore.game.GameSession
import com.expstudio.facilitycore.game.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every stage the game can persist must load into a world the player can still
 * finish. These are the softlock guards.
 */
class ProgressionTest {

    private fun sessionAt(stage: Int): GameSession = Bot.session(stage)

    private inline fun <reified T> GameSession.prop(room: String, predicate: (T) -> Boolean = { true }): T? =
        level.rooms[room]?.props?.filterIsInstance<T>()?.firstOrNull(predicate)

    @Test
    fun everyStageLoadsIntoAConsistentWorld() {
        for (stage in 0..Stage.COMPLETE) {
            val g = sessionAt(stage)
            val checkpoint = Stage.checkpointFor(stage)
            assertEquals("stage $stage did not roll back to its checkpoint", checkpoint, g.stage)

            val panel = g.prop<ArchivePanel>("circuit")!!
            if (panel.packRetrieved) {
                assertTrue(
                    "stage $stage took the pack back without the lift key: the lift can never open",
                    g.keyPackHasElevator
                )
            }
            if (g.keyPackHasElevator) {
                assertTrue("stage $stage knows the lift but has no pack", g.hasKeyPack && g.keyPackRepaired)
            }
            if (stage >= Stage.UNLOCK_FIRST_DOOR) {
                assertTrue("stage $stage cannot open the sealed door", g.hasKeyPack && g.keyPackRepaired)
            }
            if (g.subfloorPowered) {
                assertTrue(
                    "stage $stage is powered but the feeder station is not seated",
                    g.prop<ConnectionStation>("power") { it.id == "st_main" }!!.connected
                )
            }
        }
    }

    @Test
    fun everyOpenStationStillHasACableToFeedIt() {
        // Coils are hauled between rooms, so this only holds level-wide: what
        // matters is that no checkpoint consumes a coil a station still needs.
        for (stage in 0..Stage.COMPLETE) {
            val g = sessionAt(stage)
            val rooms = g.level.rooms.values
            val openStations = rooms.sumOf { room ->
                room.props.filterIsInstance<ConnectionStation>().count { !it.connected }
            }
            val loose = rooms.sumOf { room ->
                room.props.filterIsInstance<CableCoil>().count { !it.consumed }
            }
            assertTrue(
                "stage $stage has $loose cables left for $openStations stations still to feed",
                loose >= openStations
            )
        }
    }

    @Test
    fun theLiftOnlyOpensOnceTheArchiveHasBeenWritten() {
        val before = sessionAt(Stage.ELEVATOR_DENIED)
        assertFalse("the lift accepted the pack before the archive write", before.keyPackHasElevator)
        val lift = before.prop<Elevator>("lift")!!
        lift.onInteract(before)
        assertFalse("using the lift early opened its doors", lift.doorsOpen)

        val after = sessionAt(Stage.ELEVATOR_READY)
        assertTrue("the lift is still unknown after the archive write", after.keyPackHasElevator)
        val readyLift = after.prop<Elevator>("lift")!!
        readyLift.onInteract(after)
        assertTrue("the lift refused an authorised pack", readyLift.doorsOpen)
    }

    @Test
    fun theShutterSealsTheChaseRoomOnlyAfterSurviving() {
        val during = sessionAt(Stage.ELEVATOR_DENIED)
        val openShutter = during.prop<Door>("endchase") { it.name == "shutter" }!!
        assertTrue("the chase route was blocked before the chase", openShutter.open)

        val after = sessionAt(Stage.CHASE_SURVIVED)
        val sealedShutter = after.prop<Door>("endchase") { it.name == "shutter" }!!
        assertFalse("the shutter never came down", sealedShutter.open)
        assertTrue(
            "the bulkhead feed is not live at the post-chase checkpoint",
            after.prop<ConnectionStation>("endchase") { it.id == "st_chase" }!!.connected
        )
    }

    @Test
    fun thePackStaysCollectableIfTheRunEndsMidWrite() {
        // Quitting the instant the write finishes must not swallow the pack.
        val g = sessionAt(Stage.PANEL_UPLOADED)
        val panel = g.prop<ArchivePanel>("circuit")!!
        assertTrue("the archive write was lost", panel.done)
        assertFalse("the pack was taken for the player", panel.packRetrieved)
        assertTrue("the pack cannot be picked back up", panel.interactLabel(g) == "TAKE PACK")
        panel.onInteract(g)
        assertTrue("taking the pack did not authorise the lift", g.keyPackHasElevator)
        assertEquals(Stage.VENT_CRAWL, g.stage)
    }

    @Test
    fun theChaseBudgetLeavesRealMargin() {
        val session = Bot.session(Stage.ELEVATOR_DENIED)
        val bot = Bot(session, 1f).apply { interactLabels = setOf("GRAB", "THROW") }
        bot.runFor(120f, until = { session.stage >= Stage.CHASE_SURVIVED })
        val used = session.chaseTime
        println("chase used %.2fs of %.1fs".format(used, Chapter1.CHASE_SECONDS))
        assertTrue("the chase is unwinnable", session.stage >= Stage.CHASE_SURVIVED)
        // The budget is deliberately generous so a fumbled crawl is survivable.
        // A clean run should finish in comfortably under half of it.
        assertTrue(
            "a clean run leaves no margin at ${used}s of ${Chapter1.CHASE_SECONDS}s",
            used < Chapter1.CHASE_SECONDS * 0.6f
        )
        // But the route still has to be a real run, not a few steps.
        assertTrue("the chase route is too short to be a chase at ${used}s", used > 8f)
    }
}
