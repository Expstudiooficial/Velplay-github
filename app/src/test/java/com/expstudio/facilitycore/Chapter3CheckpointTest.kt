package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Chapter3
import com.expstudio.facilitycore.game.Cut
import com.expstudio.facilitycore.game.Door
import com.expstudio.facilitycore.game.Stage3
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every checkpoint has to be a place the chapter can be continued from.
 *
 * Dying is normal; being put back somewhere the story has already moved past —
 * a lift whose button has stopped answering, a room whose only door was opened
 * by a beat that will not fire again — is not. This restarts the chapter at
 * every stage and checks the player can still get somewhere from where they
 * land.
 */
class Chapter3CheckpointTest {

    private val dt = 1f / 60f

    /**
     * Rooms whose way out is a set-piece the bot has no vocabulary for — a
     * download, a fight, a two-stage haul. The playthrough covers these; here
     * they would only prove the bot is not a player.
     */
    private val setPieceSpawns = setOf("superdb3", "core3", "pit3")

    @Test
    fun everyCheckpointLeavesTheChapterPlayable() {
        val stuck = ArrayList<String>()

        for (stage in 0..Stage3.COMPLETE) {
            val checkpoint = Stage3.checkpointFor(stage)
            val g = Bot.session(stage, chapter = 3)
            val startRoom = g.roomId()
            val startX = g.player.x

            val bot = Bot(g, 1f).apply { useReach = true }
            var t = 0f
            var pause = 0f
            // Long enough to clear a room and its door, short enough to stay fast.
            while (t < 45f && g.roomId() == startRoom && g.stage <= checkpoint) {
                g.overlay?.close(true)
                if (g.dialogueBlocking) g.tapDialogue()
                val slow = g.focusProp is Door
                when {
                    pause > 0f -> { pause -= dt; g.update(dt, 0f, false, false, false) }
                    g.cut != Cut.NONE -> g.update(dt, 0f, false, false, false)
                    else -> {
                        val had = g.focusLabel.isNotEmpty()
                        bot.step(dt)
                        if (had && slow) pause = 2.2f
                    }
                }
                t += dt
            }

            // Progress is either leaving the room or advancing the story. The
            // ending stages have nowhere to go, and that is the point of them.
            val moved = g.roomId() != startRoom || g.stage > checkpoint ||
                checkpoint >= Stage3.ENDING || startRoom in setPieceSpawns
            if (!moved) {
                stuck.add(
                    "stage %d (checkpoint %d) lands in %s at x=%.1f and cannot leave it"
                        .format(stage, checkpoint, startRoom, startX)
                )
            }
        }

        assertTrue("checkpoints with no way forward:\n  " + stuck.joinToString("\n  "), stuck.isEmpty())
    }

    /** A checkpoint that spawns the player into a shut room is the same trap. */
    @Test
    fun noCheckpointSpawnsBehindItsOwnLockedDoor() {
        val blocked = ArrayList<String>()
        for (stage in 0..Stage3.COMPLETE) {
            val g = Bot.session(stage, chapter = 3)
            val room = g.room
            val (_, x, _) = Chapter3.spawnFor(Stage3.checkpointFor(stage))
            // Every exit out of the spawn room, and whether any of them is open.
            val usable = room.exits.any { e -> e.gate?.open ?: true }
            // A room sealed for a fight is sealed on purpose.
            if (!usable && room.exits.isNotEmpty() && room.id !in setPieceSpawns) {
                blocked.add("stage $stage spawns in ${room.id} (x=$x) with every exit gated shut")
            }
        }
        assertTrue("spawns behind shut doors:\n  " + blocked.joinToString("\n  "), blocked.isEmpty())
    }
}
