package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.ArchivePanel
import com.expstudio.facilitycore.game.ConnectionStation
import com.expstudio.facilitycore.game.Door
import com.expstudio.facilitycore.game.GameSession
import com.expstudio.facilitycore.game.Player
import com.expstudio.facilitycore.game.Stage
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plays Chapter 1 from the intake hall to the closing scene using nothing but
 * the real controls, the real physics and the real story machine. Puzzles are
 * completed straight away rather than solved, since the puzzle logic has its
 * own tests; everything else is exactly what a player does.
 *
 * This is the test that proves the chapter is finishable.
 */
class PlaythroughTest {

    private val dt = 1f / 60f

    /** Every reach check in the level, verified against a floor-standing player. */
    @Test
    fun everyStationCanBeReachedWhileHaulingACable() {
        val level = com.expstudio.facilitycore.game.Chapter1.build()
        for (room in level.rooms.values) {
            for (station in room.props.filterIsInstance<ConnectionStation>()) {
                // A cable caps movement at CARRY_SPEED, so a station that needs
                // a platforming climb to reach would be a dead end.
                val standing = com.expstudio.facilitycore.core.Box(
                    -Player.WIDTH * 0.5f, -Player.STAND_HEIGHT, Player.WIDTH * 0.5f, 0f
                )
                val reach = station.box.inflated(station.reach)
                assertTrue(
                    "station ${station.id} in ${room.id} sits above a floor-standing player's reach",
                    reach.t < standing.b && standing.t < reach.b
                )
            }
        }
    }

    @Test
    fun theChapterCanBePlayedFromTheIntakeHallToTheEnding() {
        val g = Bot.session(Stage.LOBBY_PUZZLE)
        val run = Run(g)

        run.drive(1f, 30f, "reroute the breakers", climb = false) {
            (g.level.room("lobby").props.firstOrNull { it is Door && it.name == "gate_lobby" } as Door).open
        }
        // The storage bay is along the floor; the sealed door is up the stairs.
        run.drive(1f, 40f, "reach the storage bay", climb = false) { g.roomId() == "storage" }
        run.drive(1f, 40f, "take and repair the key pack", climb = false) { g.keyPackRepaired }
        run.drive(-1f, 40f, "walk back to the junction", climb = false) { g.roomId() == "sealed" && g.player.x < 7f }
        run.drive(1f, 60f, "climb and unlock the sealed door") { g.roomId() == "hub" }
        run.drive(1f, 40f, "haul the feeder cable to the vault") { g.subfloorPowered }
        run.drive(1f, 40f, "unlock the way to the lift") { g.roomId() == "lift" }
        run.drive(1f, 90f, "survive the chase") { g.stage >= Stage.CHASE_SURVIVED }
        run.drive(1f, 40f, "unlock the data spine") { g.roomId() == "circuit" }
        // The far shard only drops after its throw animation lands, by which
        // point the player has walked past it, so the room is a sweep right
        // and then a sweep back left past the shard and the write panel.
        run.drive(1f, 60f, "seat both feeder cables") {
            g.level.room("circuit").props.filterIsInstance<ConnectionStation>().all { it.connected }
        }
        run.drive(-1f, 60f, "collect both data shards") { g.circuitAssembled }
        run.drive(-1f, 60f, "write the lift into the pack") { g.keyPackHasElevator }
        run.drive(1f, 60f, "crawl into the return vent") { g.roomId() == "vent" }
        run.drive(1f, 40f, "fall back into the lift landing") { g.stage >= Stage.ELEVATOR_READY }
        run.drive(-1f, 60f, "board the lift") { g.stage >= Stage.ENDING }
        run.drive(0f, 30f, "watch the descent") { g.stage >= Stage.COMPLETE }

        assertTrue("the chapter never completed", g.completed)
    }

    /** Drives the bot, auto-clearing dialogue and puzzle overlays. */
    private inner class Run(private val g: GameSession) {
        fun drive(dir: Float, timeout: Float, what: String, climb: Boolean = true, done: () -> Boolean) {
            val bot = Bot(g, dir).apply { this.climb = climb }
            var t = 0f
            var pause = 0f
            while (t < timeout && !done()) {
                // Puzzles have their own tests; here they just get completed.
                g.overlay?.close(true)
                if (g.dialogueBlocking) g.tapDialogue()

                // Wait out door scans and the archive write instead of walking
                // off the ledge while they run.
                val focus = g.focusProp
                val slow = focus is Door || focus is ArchivePanel
                if (pause > 0f) {
                    pause -= dt
                    g.update(dt, 0f, false, false, false)
                } else {
                    val hadLabel = g.focusLabel.isNotEmpty()
                    bot.step(dt)
                    if (hadLabel && slow) pause = 5.5f
                }
                t += dt
            }
            assertTrue(
                "could not $what (room=${g.roomId()}, x=%.1f, stage=${g.stage})".format(g.player.x),
                done()
            )
        }
    }
}
