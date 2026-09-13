package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Carriage
import com.expstudio.facilitycore.game.Chapter4
import com.expstudio.facilitycore.game.Counterweight
import com.expstudio.facilitycore.game.HeavyCrate
import com.expstudio.facilitycore.game.Prop
import com.expstudio.facilitycore.game.Stage4
import com.expstudio.facilitycore.game.TimedGate
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nothing that moves may close around the player.
 *
 * The general form of the bug that made Subfloor 4's first gate a dead end: a
 * solid that travels through space a body can be standing in. If it arrives
 * while they are there, the collision solver has to put them somewhere, and
 * every answer it has is bad — shoved through a wall, stood on top of a thing
 * they were under, or simply left inside it with no way out.
 *
 * So every moving solid in the chapter is made to travel with the player parked
 * directly in its path.
 */
class CrushTest {

    private val dt = 1f / 60f

    private fun movingPropsOf(room: String, g: com.expstudio.facilitycore.game.GameSession): List<Prop> =
        g.level.room(room).props.filter { it is Counterweight || it is Carriage || it is TimedGate }

    @Test
    fun thereAreMovingSolidsToCheck() {
        val g = Bot.session(Stage4.DEEP4, chapter = 4)
        val n = Chapter4.build().rooms.keys.sumOf { movingPropsOf(it, g).size }
        assertTrue("no moving solids found at all", n > 0)
    }

    @Test
    fun noMovingSolidEverContainsThePlayer() {
        val roomIds = Chapter4.build().rooms.keys.toList()
        val offences = ArrayList<String>()

        for (id in roomIds) {
            val probe = Bot.session(Stage4.DEEP4, chapter = 4)
            val movers = movingPropsOf(id, probe)
            if (movers.isEmpty()) continue

            for (index in movers.indices) {
                val g = Bot.session(Stage4.DEEP4, chapter = 4)
                g.enterRoomForTest(id, 3f, 0f)
                val prop = movingPropsOf(id, g)[index]

                // Put it in motion first, or the test parks the player beside
                // a thing that was never going to move and proves nothing.
                when (prop) {
                    is Counterweight -> {
                        // Load the pan, which is what brings the deck down.
                        val crate = g.room.props.filterIsInstance<HeavyCrate>().firstOrNull()
                        crate?.shift(prop.panCentre() - (crate.box.l + crate.box.r) * 0.5f)
                    }
                    is TimedGate -> prop.trigger(g)
                    else -> Unit
                }
                repeat(4) { g.update(dt, 0f, false, false, false) }

                // Park where it travels, on the floor, and stand perfectly still.
                val parkX = (prop.box.l + prop.box.r) * 0.5f
                g.enterRoomForTest(id, parkX, 0f)

                var t = 0f
                while (t < 25f) {
                    if (g.dialogueBlocking) g.tapDialogue()
                    g.update(dt, 0f, false, false, false)
                    t += dt
                    val solid = prop.solid ?: continue
                    if (solid.overlaps(g.player.bounds())) {
                        offences += ("%s: %s closed around the player at t=%.1fs (player %.2f, solid %.2f..%.2f)"
                            .format(id, prop.javaClass.simpleName, t, g.player.x, solid.l, solid.r))
                        break
                    }
                }
            }
        }
        assertTrue("moving solids that close on the player:\n  " + offences.joinToString("\n  "),
            offences.isEmpty())
    }
}
