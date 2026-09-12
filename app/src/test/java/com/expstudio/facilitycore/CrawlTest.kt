package com.expstudio.facilitycore

import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.game.Chapter1
import com.expstudio.facilitycore.game.Chapter2
import com.expstudio.facilitycore.game.GameSession
import com.expstudio.facilitycore.game.Player
import com.expstudio.facilitycore.game.Stage
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Guards the mechanic that stranded a real player: a gap you can only pass by
 * crouching. Running into one is not a puzzle unless the game says so, and it
 * must never be a wall.
 */
class CrawlTest {

    private val dt = 1f / 60f

    /** A gap along the floor too low to walk through but tall enough to crawl. */
    private class Gap(
        val chapter: Int,
        val roomId: String,
        val box: Box,
        val approachFromLeft: Boolean,
        val startX: Float
    )

    /**
     * Finds low openings a walking player can run into. A duct entered from
     * inside — the return vent, where the player arrives already crouched — is
     * not one of these: there is no standing approach to announce anything on.
     */
    private fun findGaps(): List<Gap> {
        val out = ArrayList<Gap>()
        for ((chapter, level) in listOf(1 to Chapter1.build(), 2 to Chapter2.build())) {
        for (room in level.rooms.values) {
            for (s in room.solids) {
                val clearance = -s.box.b // height of the opening beneath it
                if (clearance <= Player.CROUCH_HEIGHT + 0.05f) continue
                if (clearance >= Player.STAND_HEIGHT) continue
                if (s.box.w < 0.5f) continue
                // A long duct is something you are already inside, not a gap
                // you walk up to; those have their own entry gap to check.
                if (s.box.w > 6f) continue

                val fromLeft = s.box.l - 1.2f > room.bounds.l + 0.4f
                val startX = if (fromLeft) s.box.l - 1.1f else s.box.r + 1.1f
                if (startX < room.bounds.l + 0.4f || startX > room.bounds.r - 0.4f) continue
                out.add(Gap(chapter, room.id, s.box, fromLeft, startX))
            }
        }
        }
        return out
    }

    private fun sessionAt(gap: Gap): GameSession {
        val g = if (gap.chapter == 2) Bot.session(com.expstudio.facilitycore.game.Stage2.SMELTER_DOOR, chapter = 2)
        else Bot.session(Stage.PANEL_UPLOADED)
        g.enterRoomForTest(gap.roomId, gap.startX)
        return g
    }

    @Test
    fun theLevelHasCrawlGapsToCheck() {
        assertTrue("no crawl gaps found; the detector must be wrong", findGaps().isNotEmpty())
    }

    @Test
    fun everyCrawlGapAnnouncesItself() {
        for (gap in findGaps()) {
            val dir = if (gap.approachFromLeft) 1f else -1f
            val g = sessionAt(gap)
            // Skip a gap whose approach is not open standing ground.
            if (!g.player.canFit(gap.startX, 0f, Player.STAND_HEIGHT, g.solidsForTest())) continue

            var t = 0f
            var hinted = false
            while (t < 3f && !hinted) {
                g.update(dt, dir, false, false, false)
                hinted = g.crouchHint
                t += dt
            }
            assertTrue(
                "the gap in chapter ${gap.chapter} ${gap.roomId} at x=${gap.box.l}..${gap.box.r} never tells the " +
                    "player to sneak; they just stop dead against it",
                hinted
            )
        }
    }

    @Test
    fun everyCrawlGapCanActuallyBeCrawled() {
        for (gap in findGaps()) {
            val dir = if (gap.approachFromLeft) 1f else -1f
            val g = sessionAt(gap)
            if (!g.player.canFit(gap.startX, 0f, Player.STAND_HEIGHT, g.solidsForTest())) continue

            var t = 0f
            var through = false
            while (t < 8f && !through) {
                g.update(dt, dir, true, false, false)
                through = if (gap.approachFromLeft) g.player.x > gap.box.r + 0.2f
                else g.player.x < gap.box.l - 0.2f
                t += dt
                if (g.room.id != gap.roomId) { through = true } // crawled into the next room
            }
            assertTrue(
                "the gap in chapter ${gap.chapter} ${gap.roomId} at x=${gap.box.l}..${gap.box.r} cannot be crawled " +
                    "through even while holding sneak",
                through
            )
        }
    }

    /**
     * The scenario that was actually reported: sprint into the bulkhead, jump at
     * it because that is the instinct, and only then work out it is a crawl.
     * That player must still make it out alive.
     */
    @Test
    fun aPlayerWhoFumblesTheCrawlStillSurvivesTheChase() {
        val g = Bot.session(Stage.ELEVATOR_DENIED)
        var t = 0f
        var stalled = 0f
        var crouchHold = 0f
        var jumpCooldown = 0f

        while (t < 70f && g.stage < Stage.CHASE_SURVIVED) {
            g.overlay?.close(true)
            if (g.dialogueBlocking) g.tapDialogue()

            val p = g.player
            if (abs(p.vx) < 0.6f && p.controlEnabled) stalled += dt else stalled = 0f

            // React only once already stopped: jump first, crouch much later.
            jumpCooldown -= dt
            val jump = stalled > 0.25f && stalled < 1.1f && p.onGround && jumpCooldown <= 0f
            if (jump) jumpCooldown = 0.35f
            if (stalled > 1.1f) crouchHold = 0.9f
            if (crouchHold > 0f) crouchHold -= dt

            g.update(dt, 1f, crouchHold > 0f, jump, g.focusLabel.isNotEmpty())
            t += dt
        }

        assertTrue(
            "a player who fumbles the crawl dies to the chase (room=${g.room.id}, stage=${g.stage})",
            g.stage >= Stage.CHASE_SURVIVED
        )
        assertTrue(
            "fumbling left no margin at all: ${g.chaseTime}s of ${Chapter1.CHASE_SECONDS}s",
            g.chaseTime < Chapter1.CHASE_SECONDS - 3f
        )
    }
}
