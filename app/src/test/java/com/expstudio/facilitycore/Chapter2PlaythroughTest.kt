package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Cut
import com.expstudio.facilitycore.game.GameSession
import com.expstudio.facilitycore.game.LiftTask
import com.expstudio.facilitycore.game.Stage2
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Plays Chapter 2 from the lift fight to the pour, using the real physics, the
 * real story machine and the real controls. If a change strands the player
 * anywhere in the chapter, this fails.
 */
class Chapter2PlaythroughTest {

    private val dt = 1f / 60f

    @Test
    fun theChapterCanBePlayedFromTheCarToThePour() {
        val g = Bot.session(Stage2.LIFT_FIGHT, chapter = 2)
        val run = Run(g)

        run.fight("repair the car while it swings")
        run.drive(1f, 40f, "ride down and step out onto Subfloor 1") { g.roomId() == "landing1" }
        run.drive(1f, 60f, "find and lift a battery cube") { g.stage >= Stage2.BATTERY_CARRIED }
        run.drive(1f, 60f, "seat the cube and open the gate") { g.stage >= Stage2.GATE_OPEN }
        run.drive(1f, 40f, "walk into the holding hall") { g.roomId() == "hall" }
        run.hoist("survive the hunt and use the hoist")
        run.drive(1f, 60f, "reach the feeder gallery") { g.roomId() == "feed" }
        run.drive(1f, 90f, "seat the feeder and take the cell") { g.stage >= Stage2.CUBE_TAKEN }
        run.drive(1f, 90f, "carry the cell to the spine and open it") { g.stage >= Stage2.SPINE_OPEN }
        run.drive(1f, 90f, "outrun the shade to the sealed cell") { g.stage >= Stage2.DEAD_END }
        run.drive(1f, 60f, "crawl the duct and feed the smelter line") { g.stage >= Stage2.SMELTER_ARMED }
        run.drive(1f, 60f, "get through the hatch") { g.stage >= Stage2.ENDING }
        run.drive(0f, 30f, "watch it go into the pour") { g.stage >= Stage2.COMPLETE }

        assertTrue("the chapter never completed", g.completed)
    }

    private inner class Run(private val g: GameSession) {

        /** The lift fight: work the jobs, roll when the swing is coming. */
        fun fight(what: String) {
            var t = 0f
            var stalled = 0f
            while (t < 120f && g.stage < Stage2.LIFT_FIXED) {
                g.overlay?.close(true)
                if (g.dialogueBlocking) g.tapDialogue()
                val roll = g.monster.swing in 0.30f..0.50f && g.player.dodgeReady
                val label = g.focusLabel
                val use = label == "REPAIR" || label == "TURN"
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
            assertTrue("could not $what (stage=${g.stage}, health=${g.health})", g.stage >= Stage2.LIFT_FIXED)
        }

        /** The hunt, then standing on the hoist switch long enough to be taken. */
        fun hoist(what: String) {
            val bot = Bot(g, 1f).apply { interactLabels = setOf("HOIST", "HOLD") }
            var t = 0f
            var onTheSwitch = false
            while (t < 150f && g.stage < Stage2.DROPPED) {
                if (g.focusLabel == "HOIST") onTheSwitch = true
                when {
                    g.cut != Cut.NONE -> g.update(dt, 0f, false, false, false)
                    onTheSwitch -> g.update(dt, 0f, false, false, g.focusLabel.isNotEmpty())
                    else -> bot.step(dt)
                }
                t += dt
            }
            assertTrue("could not $what (stage=${g.stage}, room=${g.roomId()})", g.stage >= Stage2.DROPPED)
        }

        fun drive(dir: Float, timeout: Float, what: String, done: () -> Boolean) {
            val bot = Bot(g, dir)
            var t = 0f
            var pause = 0f
            while (t < timeout && !done()) {
                g.overlay?.close(true)
                if (g.dialogueBlocking) g.tapDialogue()
                val focus = g.focusProp
                // Wait out reads and scans rather than walking off them.
                val slow = focus is com.expstudio.facilitycore.game.Door ||
                    focus is com.expstudio.facilitycore.game.KeySwitch
                if (pause > 0f) {
                    pause -= dt
                    g.update(dt, 0f, false, false, false)
                } else if (g.cut != Cut.NONE) {
                    g.update(dt, 0f, false, false, false)
                } else {
                    val hadLabel = g.focusLabel.isNotEmpty()
                    bot.step(dt)
                    if (hadLabel && slow) pause = 3.0f
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
