package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Chapter2
import com.expstudio.facilitycore.game.Chapter3
import com.expstudio.facilitycore.game.Cut
import com.expstudio.facilitycore.game.GameSession
import com.expstudio.facilitycore.game.Stage2
import com.expstudio.facilitycore.game.Stage3
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every clock in the game has to be beatable with room to spare.
 *
 * Finishing a timed section is not the bar — finishing it comfortably is. The
 * playthrough tests run on budgets of thousands of seconds, so a section that
 * can only be beaten by retrying it twenty times still passes them. Subfloor
 * 4's first gate was exactly that: a 22 metre run on a 5.2 second clock, which
 * the playthrough "passed" by hammering the switch until it slipped through the
 * frame in which the slab was still closing.
 *
 * So these measure the margin, and fail when it is thin even though the section
 * was technically completed.
 */
class TimingMarginTest {

    private val dt = 1f / 60f

    /** At least this much of the clock has to be left at the end. */
    private val healthyMargin = 0.25f

    private fun report(name: String, used: Float, budget: Float, out: MutableList<String>) {
        val left = (budget - used) / budget
        println("%-28s used %5.1fs of %5.1fs  (%.0f%% left)".format(name, used, budget, left * 100f))
        if (left < healthyMargin) {
            out += "%s: only %.1fs of a %.1fs clock left (%.0f%%)".format(name, budget - used, budget, left * 100f)
        }
    }

    @Test
    fun theDuctsAreCrawledWithTimeToSpare() {
        val thin = ArrayList<String>()

        // Chapter 2's duct is a job done inside it rather than a crawl out of
        // it: the smelter line has to be fed before the clock ends.
        run {
            val g = Bot.session(Stage2.VENT_RUN, chapter = 2)
            val bot = Bot(g, 1f).apply { useReach = true }
            val used = driveUntil(g, bot, Chapter2.VENT_SECONDS + Chapter2.HATCH_SECONDS + 10f) {
                g.stage >= Stage2.SMELTER_ARMED
            }
            report("chapter 2 smelter line", used, Chapter2.VENT_SECONDS, thin)
        }

        // Chapter 3's duct.
        run {
            val g = Bot.session(Stage3.VENT_RUN, chapter = 3)
            g.enterRoomForTest("vent3", 2f, 0f)
            val used = crawlUntil(g, Chapter3.VENT_SECONDS + 8f) { g.roomId() != "vent3" }
            report("chapter 3 duct", used, Chapter3.VENT_SECONDS, thin)
        }

        assertTrue("timed sections with no room for error:\n  " + thin.joinToString("\n  "),
            thin.isEmpty())
    }

    /** Plays east until [done], returning the seconds it took. */
    private fun driveUntil(g: GameSession, bot: Bot, timeout: Float, done: () -> Boolean): Float {
        var t = 0f
        while (t < timeout && !done() && !g.cutIsDeath()) {
            if (g.dialogueBlocking) g.tapDialogue()
            if (g.cut != Cut.NONE) { g.update(dt, 0f, false, false, false); t += dt; continue }
            bot.step(dt)
            t += dt
        }
        assertTrue("the section was never finished at all (room=${g.roomId()}, stage=${g.stage})", done())
        return t
    }

    /** Crouch-runs east until [done], returning the seconds it took. */
    private fun crawlUntil(g: GameSession, timeout: Float, done: () -> Boolean): Float {
        var t = 0f
        while (t < timeout && !done() && !g.cutIsDeath()) {
            if (g.dialogueBlocking) g.tapDialogue()
            if (g.cut != Cut.NONE) { g.update(dt, 0f, false, false, false); t += dt; continue }
            g.update(dt, 1f, true, false, false)
            t += dt
        }
        assertTrue("the duct was never crawled at all (room=${g.roomId()})", done())
        return t
    }
}
