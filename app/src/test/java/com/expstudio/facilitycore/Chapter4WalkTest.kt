package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Chapter4
import com.expstudio.facilitycore.game.Stage4
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Walks every ordinary room in Chapter 4 from one doorway to the other.
 *
 * The chapter is about a hundred rooms and most of them are corridors. Reading
 * them is not a way to know they work; walking them is. The bot uses nothing a
 * player does not have, so a room it cannot cross is a room that is broken.
 *
 * Rooms built around a specific mechanic are listed out — they are covered by
 * the playthrough instead, which arrives in them with the right state.
 */
class Chapter4WalkTest {

    private val dt = 1f / 60f

    /** Rooms whose exit is a set-piece rather than the far wall. */
    private val setPieces = setOf(
        "core4",      // a cutscene deck over the well
        "lift5",      // leaves by the car
        "bigdoor4",   // leaves by a hatch and a door with three bolts
        "lock4a", "lock4b", "lock4c",   // the bolts themselves
        "ren4",       // leaves by a duct, once there are two of them
        "vent4",      // crouch-only
        "liftup4",    // the car out
        "g3", "g8", "g13", "g17",       // mechanism rooms: plates, decks, beams, air
        "h4", "h9", "h15", "h22", "h28", "h30", "h36", "h40"
    )

    @Test
    fun everyOrdinaryRoomCanBeWalkedEndToEnd() {
        val level = Chapter4.build()
        val failures = ArrayList<String>()

        for (room in level.rooms.values) {
            if (room.id in setPieces) continue
            // Every room is walked in a session whose stage owns it, so any doors
            // the story has already opened are open.
            val g = Bot.session(Stage4.DEEP4, chapter = 4)
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
