package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Chapter3
import com.expstudio.facilitycore.game.Stage3
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Walks every ordinary room in Chapter 3 from one doorway to the other.
 *
 * The chapter is about a hundred rooms and most of them are corridors. Reading
 * them is not a way to know they work; walking them is. The bot uses nothing a
 * player does not have, so a room it cannot cross is a room that is broken.
 *
 * Rooms built around a specific mechanic are listed out — they are covered by
 * the playthrough instead, which arrives in them with the right state.
 */
class Chapter3WalkTest {

    private val dt = 1f / 60f

    /** Rooms whose exit is a set-piece rather than the far wall. */
    private val setPieces = setOf(
        "obs3",       // a cutscene deck
        "archive3",   // the index waves
        "juke3",      // needs the hand to reach the duct
        "vent3",      // crouch-only, timed
        "seal3",      // needs the hand for the shutters
        "bigdoor3",   // leaves by a floor hatch, not the far wall
        "deepspan",   // needs the hand for every ledge
        "corepz3",    // valves then a terminal
        "superdb3",   // a download and a sealed door
        "lift3",      // leaves by the car
        "lift4",      // needs the hand to rip the doors
        "shaft3",     // needs the hand to climb
        "core3",      // emitters and the fight
        "pit3",       // needs the hand twice
        "bulkC"       // its far door is the chase shutter, shut after the chase
    )

    @Test
    fun everyOrdinaryRoomCanBeWalkedEndToEnd() {
        val level = Chapter3.build()
        val failures = ArrayList<String>()

        for (room in level.rooms.values) {
            if (room.id in setPieces) continue
            // Every room is walked in a session whose stage owns it, so any doors
            // the story has already opened are open.
            val g = Bot.session(Stage3.DEEP, chapter = 3)
            g.reach.unlocked = true
            g.enterRoomForTest(room.id, room.bounds.l + 1.7f, 0f)
            val target = room.bounds.r - 1.2f

            val bot = Bot(g, 1f).apply { useReach = true }
            var t = 0f
            // Generous: a 34 m room with a crawl and two climbs is about 20 s.
            while (t < 70f && g.roomId() == room.id && g.player.x < target) {
                g.overlay?.close(true)
                if (g.dialogueBlocking) g.tapDialogue()
                bot.step(dt)
                t += dt
            }
            val crossed = g.roomId() != room.id || g.player.x >= target
            if (!crossed) {
                failures.add(
                    "%s (%s): stopped at x=%.1f of %.1f"
                        .format(room.id, room.title, g.player.x, room.bounds.r)
                )
            }
        }

        assertTrue(
            "rooms that cannot be walked:\n  " + failures.joinToString("\n  "),
            failures.isEmpty()
        )
    }
}
