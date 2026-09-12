package com.expstudio.facilitycore

import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.game.Chapter2
import com.expstudio.facilitycore.game.ConnectionStation
import com.expstudio.facilitycore.game.CubeSocket
import com.expstudio.facilitycore.game.Cut
import com.expstudio.facilitycore.game.Door
import com.expstudio.facilitycore.game.GameSession
import com.expstudio.facilitycore.game.LiftTask
import com.expstudio.facilitycore.game.Player
import com.expstudio.facilitycore.game.PowerCube
import com.expstudio.facilitycore.game.Stage2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Chapter 2 gets the same structural and playability guarantees as Chapter 1. */
class Chapter2Test {

    private val dt = 1f / 60f
    private val level = Chapter2.build()

    private fun session(stage: Int): GameSession = Bot.session(stage, chapter = 2)

    // ---- structure --------------------------------------------------------

    @Test
    fun everyExitPointsAtARealRoom() {
        for (room in level.rooms.values) {
            for (exit in room.exits) {
                assertNotNull("${room.id} exits to unknown room '${exit.toRoom}'", level.rooms[exit.toRoom])
            }
        }
    }

    @Test
    fun everyRoomIsReachableFromTheCar() {
        val seen = HashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add("car"); seen.add("car")
        while (queue.isNotEmpty()) {
            val room = level.room(queue.removeFirst())
            for (exit in room.exits) if (seen.add(exit.toRoom)) queue.add(exit.toRoom)
        }
        for (id in level.rooms.keys) assertTrue("room '$id' is unreachable", id in seen)
    }

    @Test
    fun spawnPointsAreNotInsideGeometry() {
        for (room in level.rooms.values) {
            for (exit in room.exits) {
                val target = level.room(exit.toRoom)
                val box = Box(
                    exit.spawnX - Player.WIDTH * 0.5f, exit.spawnY - Player.CROUCH_HEIGHT,
                    exit.spawnX + Player.WIDTH * 0.5f, exit.spawnY
                )
                for (solid in target.solids) {
                    assertFalse(
                        "spawn from ${room.id} into ${exit.toRoom} is inside geometry",
                        box.overlaps(solid.box)
                    )
                }
            }
        }
    }

    @Test
    fun everyCheckpointLoadsIntoAConsistentWorld() {
        for (stage in 0..Stage2.COMPLETE) {
            val g = session(stage)
            assertTrue("stage $stage lost the key pack", g.hasKeyPack && g.keyPackRepaired)
            val (roomId, _, _) = Chapter2.spawnFor(stage)
            assertNotNull("stage $stage spawns into unknown room '$roomId'", g.level.rooms[roomId])
            assertTrue("stage $stage did not roll back to its checkpoint",
                g.stage == Stage2.checkpointFor(stage))
        }
    }

    @Test
    fun theSmelterHatchIsReachable() {
        val vent = level.room("vent2")
        val exit = vent.exits.first { it.toRoom == "smelter" }
        // The right wall must not sit in front of the trigger zone.
        val wall = vent.solids.filter { it.box.l >= 29f }.minOfOrNull { it.box.l } ?: Float.MAX_VALUE
        val furthest = wall - Player.WIDTH * 0.5f
        assertTrue(
            "the hatch trigger at ${exit.zone.l} is past the furthest the player can stand ($furthest)",
            furthest + Player.WIDTH * 0.5f > exit.zone.l
        )
    }

    @Test
    fun theCarStaysSealedUntilItIsRepaired() {
        val g = session(Stage2.LIFT_FIGHT)
        val doors = g.level.room("car").props.firstOrNull { it is Door && it.name == "door_car" } as? Door
        assertNotNull("the car has no doors", doors)
        assertFalse("the car was open during the fight", doors!!.open)

        val after = session(Stage2.ARRIVAL)
        val opened = after.level.room("car").props.firstOrNull { it is Door && it.name == "door_car" } as? Door
        assertTrue("the car never opened after the repair", opened!!.open)
    }

    // ---- playability ------------------------------------------------------

    @Test
    fun theLiftFightCanBeWon() {
        val g = session(Stage2.LIFT_FIGHT)
        var t = 0f
        var stalled = 0f
        while (t < 90f && g.stage < Stage2.LIFT_FIXED) {
            g.overlay?.close(true)
            if (g.dialogueBlocking) g.tapDialogue()
            // Roll late in the wind-up, which is what the tell is telling you.
            val roll = g.monster.swing in 0.30f..0.50f && g.player.dodgeReady

            val label = g.focusLabel
            val use = label == "REPAIR" || label == "TURN"
            // Walk toward the nearest unfinished job, hopping the car's clutter.
            val target = g.level.room("car").props.filterIsInstance<LiftTask>()
                .filter { !it.done }.minByOrNull { abs(it.box.cx - g.player.x) }
            val dir = when {
                target == null -> 0f
                target.box.cx > g.player.x + 0.6f -> 1f
                target.box.cx < g.player.x - 0.6f -> -1f
                else -> 0f
            }
            if (dir != 0f && abs(g.player.vx) < 0.5f && g.player.controlEnabled) stalled += dt else stalled = 0f
            val jump = stalled > 0.2f && g.player.onGround
            if (jump) stalled = 0f
            g.update(dt, dir, false, jump, use, roll)
            t += dt
        }
        assertTrue(
            "the lift fight was not winnable (stage=${g.stage}, health=${g.health})",
            g.stage >= Stage2.LIFT_FIXED
        )
    }

    @Test
    fun aDodgeBeatsASwing() {
        val g = session(Stage2.LIFT_FIGHT)
        // Stand still until a swing is on its way, then roll through it.
        var t = 0f
        var rolled = false
        while (t < 12f && g.health == 3) {
            // React to the wind-up as it peaks, not the instant it starts.
            val incoming = g.monster.swing in 0.30f..0.50f
            val roll = incoming && g.player.dodgeReady
            if (roll) rolled = true
            g.update(dt, 0f, false, false, false, roll)
            t += dt
        }
        assertTrue("no swing ever arrived to dodge", rolled)
        assertTrue("rolling through the swing still cost health", g.health == 3)
    }

    @Test
    fun standingInTheSwingCostsHealth() {
        val g = session(Stage2.LIFT_FIGHT)
        var t = 0f
        while (t < 14f && g.health == 3) {
            // Never dodge; walk into it instead.
            val dir = if (g.monster.x > g.player.x) 1f else -1f
            g.update(dt, dir, false, false, false, false)
            t += dt
        }
        assertTrue("the attack never connected with a player who ignored it", g.health < 3)
    }

    @Test
    fun theHoistEscapeLiftsThePlayerClearAndPutsThemDown() {
        val g = session(Stage2.MEET_PLAYERR)
        val bot = Bot(g, 1f).apply { interactLabels = setOf("HOIST") }
        // Run the hall and the loops, but stand still on the switch: the read
        // takes a moment and walking off it cancels.
        var t = 0f
        var onTheSwitch = false
        while (t < 140f && g.stage < Stage2.DROPPED) {
            if (g.focusLabel == "HOIST") onTheSwitch = true
            when {
                g.cut != Cut.NONE -> g.update(dt, 0f, false, false, false)
                // Stand on it and keep holding until the read takes.
                onTheSwitch -> g.update(dt, 0f, false, false, g.focusLabel.isNotEmpty())
                else -> bot.step(dt)
            }
            t += dt
        }
        assertTrue(
            "the hoist escape never completed (stage=${g.stage}, room=${g.roomId()})",
            g.stage >= Stage2.DROPPED
        )
        assertTrue("the player was left hanging", g.cut == Cut.NONE)
        assertTrue("the player was not put back on the deck", g.player.onGround)
    }

    @Test
    fun theCellFromTheChargerOpensTheSpine() {
        val g = session(Stage2.FEED_ROOM)
        val bot = Bot(g, 1f).apply { interactLabels = setOf("GRAB", "THROW", "TAKE CELL", "TAKE CUBE", "INSERT") }
        bot.runFor(120f, until = { g.stage >= Stage2.SPINE_OPEN })
        assertTrue("the spine never opened (stage=${g.stage}, room=${g.roomId()})", g.stage >= Stage2.SPINE_OPEN)
        val socket = g.level.room("spine2").props.firstOrNull { it is CubeSocket } as? CubeSocket
        assertTrue("the spine socket is empty", socket!!.filled)
    }

    @Test
    fun theDuctRunEndsAtTheSmelterHatch() {
        val g = session(Stage2.DEAD_END)
        val bot = Bot(g, 1f).apply { interactLabels = setOf("GRAB", "THROW", "OPEN") }
        bot.runFor(140f, until = { g.stage >= Stage2.ENDING })
        assertTrue(
            "the smelter run was not survivable (stage=${g.stage}, room=${g.roomId()})",
            g.stage >= Stage2.ENDING
        )
        val station = g.level.room("vent2").props
            .firstOrNull { it is ConnectionStation } as? ConnectionStation
        assertTrue("the smelter feed was never seated", station!!.connected)
    }

    @Test
    fun dyingInTheFightDoesNotReplayTheOpening() {
        val g = session(Stage2.LIFT_FIGHT)
        var t = 0f
        while (t < 30f && g.health > 0) {
            val dir = if (g.monster.x > g.player.x) 1f else -1f
            g.update(dt, dir, false, false, false, false)
            t += dt
        }
        assertTrue("the fight never killed a player who ignored it", g.health <= 0)
        // Ride out the death and the respawn.
        var settle = 0f
        while (settle < 6f && g.cut != Cut.NONE) {
            g.update(dt, 0f, false, false, false)
            settle += dt
        }
        assertTrue(
            "death sent the player back through the opening cutscene (cut=${g.cut}, stage=${g.stage})",
            g.cut != Cut.CH2_OPENING && g.stage >= Stage2.LIFT_FIGHT
        )
        assertTrue("the retry did not restore health", g.health == 3)
        assertTrue("the retry did not put the player back in the car", g.roomId() == "car")
    }

    @Test
    fun theRightThingIsHuntingAtEveryCheckpoint() {
        // The Playerr hunts the holding wing; the shade takes over on the walk.
        val playerr = session(Stage2.PLAYERR_CHASE)
        assertTrue(
            "the wrong monster is on the Playerr's route",
            playerr.monster.kind == com.expstudio.facilitycore.game.Monster.Kind.PLAYERR
        )
        val shade = session(Stage2.SHADE_CHASE)
        assertTrue(
            "the wrong monster is on the shade's route",
            shade.monster.kind == com.expstudio.facilitycore.game.Monster.Kind.SHADE
        )
    }

    @Test
    fun everyCubeHasASocketToGoIn() {
        // A cube consumed with no socket left to fill would be a dead end.
        for (stage in 0..Stage2.COMPLETE) {
            val g = session(stage)
            val openSockets = g.level.rooms.values.sumOf { room ->
                room.props.filterIsInstance<CubeSocket>().count { !it.filled }
            }
            val loose = g.level.rooms.values.sumOf { room ->
                room.props.filterIsInstance<PowerCube>().count { !it.consumed }
            }
            // One cube comes out of the charger, so the count can be short by one
            // exactly while that station is still unopened.
            val chargerPending = g.level.rooms.values.any { room ->
                room.props.filterIsInstance<ConnectionStation>().any { it.yieldsShard && !it.shardTaken }
            }
            assertTrue(
                "stage $stage has $loose cubes for $openSockets sockets",
                loose + (if (chargerPending) 1 else 0) >= openSockets
            )
        }
    }
}
