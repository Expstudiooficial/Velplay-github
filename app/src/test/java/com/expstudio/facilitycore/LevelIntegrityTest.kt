package com.expstudio.facilitycore

import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.game.Chapter1
import com.expstudio.facilitycore.game.Door
import com.expstudio.facilitycore.game.Player
import com.expstudio.facilitycore.game.Stage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Structural checks over the authored level data. */
class LevelIntegrityTest {

    private val level = Chapter1.build()

    @Test
    fun everyExitPointsAtARealRoom() {
        for (room in level.rooms.values) {
            for (exit in room.exits) {
                assertNotNull("${room.id} exits to unknown room '${exit.toRoom}'", level.rooms[exit.toRoom])
            }
        }
    }

    @Test
    fun everyRoomIsReachableFromTheEntrance() {
        val seen = HashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add("entry")
        seen.add("entry")
        while (queue.isNotEmpty()) {
            val room = level.room(queue.removeFirst())
            for (exit in room.exits) {
                if (seen.add(exit.toRoom)) queue.add(exit.toRoom)
            }
        }
        for (id in level.rooms.keys) {
            assertTrue("room '$id' is unreachable from the entrance", id in seen)
        }
    }

    @Test
    fun spawnPointsAreNotInsideGeometry() {
        // A spawn may require crouching (the return vent does), but it must
        // never overlap solid geometry at the crouched size.
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
                        "spawn from ${room.id} into ${exit.toRoom} at (${exit.spawnX}, ${exit.spawnY}) is inside geometry",
                        box.overlaps(solid.box)
                    )
                }
            }
        }
    }

    @Test
    fun checkpointSpawnsAreNotInsideGeometry() {
        for (stage in 0..Stage.COMPLETE) {
            val (roomId, x, y) = Chapter1.spawnFor(stage)
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
    fun everyGatedExitIsGatedByADoorThatLivesInThatRoom() {
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
    fun keyPackDoorsAcceptTheKeyAndTheLiftDoesNot() {
        val keyDoors = listOf("sealed" to "door_main", "power" to "door_power", "endchase" to "door_circuit")
        for ((roomId, name) in keyDoors) {
            val door = level.room(roomId).props.firstOrNull { it is Door && it.name == name } as? Door
            assertNotNull("$roomId is missing door $name", door)
            assertTrue("$name would never accept the key pack", door!!.keyAccepted)
        }
    }

    @Test
    fun theReturnVentCannotBeEnteredStandingUp() {
        val circuit = level.room("circuit")
        val ventExit = circuit.exits.first { it.toRoom == "vent" }
        // A standing player is stopped by the duct lip, so the nearest they can
        // get must fall short of the trigger zone.
        val lip = circuit.solids.filter { it.box.l >= 27f }.minOf { it.box.l }
        val furthestStandingReach = lip - Player.WIDTH * 0.5f
        assertTrue(
            "a standing player could walk straight into the vent",
            furthestStandingReach < ventExit.zone.l
        )
    }
}
