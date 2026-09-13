package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.ChaseLeg
import com.expstudio.facilitycore.game.Monster
import com.expstudio.facilitycore.game.Stage
import com.expstudio.facilitycore.game.Stage2
import com.expstudio.facilitycore.game.Stage3
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The thing behind you has to be a thing, not a decal.
 *
 * The old pursuit read the player's position off the chase route and placed the
 * monster a fixed distance back along it. That has three tells, and every one
 * of them is checked here: it did not move when the player stood still, it
 * moved at exactly the player's speed when they ran, and it could not be
 * outrun no matter how well you played.
 */
class PursuitTest {

    private val dt = 1f / 60f

    /**
     * A live Chapter 1 chase. The stage's own checkpoint is back at the locked
     * elevator, so the pursuit is started here directly rather than walked to.
     */
    private fun chaseSession(): com.expstudio.facilitycore.game.GameSession {
        val g = Bot.session(Stage.CHASE, chapter = 1)
        g.camera.resize(1920, 1080)
        g.enterRoomForTest("archive", com.expstudio.facilitycore.game.Chapter1.ENCOUNTER_X, 0f)
        g.startChase(Stage.CHASE)
        return g
    }

    @Test
    fun itKeepsComingWhenThePlayerStandsStill() {
        val g = chaseSession()
        val startRoom = g.monster.roomId
        val startX = g.monster.x
        var t = 0f
        // Stand perfectly still. It has to arrive anyway.
        while (t < 3f) { g.update(dt, 0f, false, false, false); t += dt }
        val movedRooms = g.monster.roomId != startRoom
        val movedMetres = abs(g.monster.x - startX)
        assertTrue(
            "it did not move while the player stood still (%.2f m, room %s -> %s)"
                .format(movedMetres, startRoom, g.monster.roomId),
            // It deliberately eases once it is on top of you, so the bar is
            // "it closed real ground", not "it sprinted". Glued, this was zero.
            movedRooms || movedMetres > 4f
        )
    }

    @Test
    fun itCatchesAPlayerWhoDoesNothing() {
        val g = chaseSession()
        var t = 0f
        while (t < 30f && !g.cutIsDeath()) { g.update(dt, 0f, false, false, false); t += dt }
        assertTrue("standing still was survivable", g.cutIsDeath())
        assertTrue(
            "it took the whole clock to arrive rather than walking over",
            t < com.expstudio.facilitycore.game.Chapter1.CHASE_SECONDS - 5f
        )
    }

    @Test
    fun aPlayerWhoRunsWellPullsAway() {
        val g = chaseSession()
        val bot = Bot(g, 1f)
        var t = 0f
        var bestGap = -999f
        while (t < 12f && !g.cutIsDeath()) {
            if (g.dialogueBlocking) g.tapDialogue()
            bot.step(dt)
            t += dt
            if (g.monster.roomId == g.roomId()) bestGap = maxOf(bestGap, g.player.x - g.monster.x)
        }
        // Running well has to buy distance. Glued to the player, this was
        // always the same number.
        assertTrue("running well never opened a gap (%.1f m)".format(bestGap), bestGap > 6f)
    }

    /** Its speed is its own, and not a copy of the player's. */
    @Test
    fun itDoesNotMoveAtExactlyThePlayersSpeed()
    {
        val g = chaseSession()
        var t = 0f
        var sawSlower = false
        var sawFaster = false
        var lastM = g.monster.x
        var lastP = g.player.x
        // Alternate sprinting and stopping; a decal would match both exactly.
        while (t < 10f && !g.cutIsDeath()) {
            val dir = if ((t * 2f).toInt() % 2 == 0) 1f else 0f
            g.update(dt, dir, false, false, false)
            if (g.monster.roomId == g.roomId()) {
                val dm = g.monster.x - lastM
                val dp = g.player.x - lastP
                if (dm > dp + 0.004f) sawFaster = true
                if (dm < dp - 0.004f) sawSlower = true
            }
            lastM = g.monster.x
            lastP = g.player.x
            t += dt
        }
        assertTrue("it never moved faster than the player", sawFaster)
        assertTrue("it never moved slower than the player", sawSlower)
    }

    /** It has to get through the rooms on its route, not stop at the first wall. */
    @Test
    fun itTravelsThroughTheRoomsOnItsRoute() {
        val g = Bot.session(Stage3.DEEP, chapter = 3)
        g.camera.resize(1920, 1080)
        g.enterRoomForTest("e26", 2f, 0f)
        g.monster.setRoute(com.expstudio.facilitycore.game.Chapter3.deepRoute(g.level.rooms))
        g.startChase(Stage3.DEEP_CHASE)
        val seen = HashSet<String>()
        var t = 0f
        while (t < 40f && !g.cutIsDeath()) {
            seen.add(g.monster.roomId)
            g.update(dt, 0f, false, false, false)
            t += dt
        }
        assertTrue("it never left the room it started in (visited $seen)", seen.size >= 2)
    }

    /**
     * Chapter 2's two chases run on the same legs.
     *
     * The rework lives in the session rather than in any one chapter, so this
     * is here to prove that rather than to assume it: both of Chapter 2's
     * pursuits have to close real ground on a player who is standing still,
     * the same way Chapter 1's and Chapter 3's do.
     */
    @Test
    fun chapterTwosChasesArePursuitsToo() {
        for (which in listOf(Stage2.PLAYERR_CHASE, Stage2.SHADE_CHASE)) {
            val g = Bot.session(Stage2.MEET_PLAYERR, chapter = 2)
            g.camera.resize(1920, 1080)
            g.enterRoomForTest("hall", com.expstudio.facilitycore.game.Chapter2.HALL_TRIGGER_X, 0f)
            g.monster.kind = Monster.Kind.PLAYERR
            g.monster.setRoute(
                listOf(
                    ChaseLeg("hall", com.expstudio.facilitycore.game.Chapter2.HALL_TRIGGER_X - 6f, 27.5f, 0f),
                    ChaseLeg("maze_a", 0.5f, 15.5f, 0f),
                    ChaseLeg("maze_b", 0.5f, 15.5f, 0f),
                    ChaseLeg("hoist", 0.5f, 14.5f, 0f)
                )
            )
            g.startChase(which)
            val startRoom = g.monster.roomId
            val startX = g.monster.x
            var t = 0f
            while (t < 3f && !g.cutIsDeath()) { g.update(dt, 0f, false, false, false); t += dt }
            assertTrue(
                "chase %d did not move while the player stood still (%.2f m, %s -> %s)"
                    .format(which, abs(g.monster.x - startX), startRoom, g.monster.roomId),
                g.monster.roomId != startRoom || abs(g.monster.x - startX) > 4f
            )
        }
    }

    /** And it never ends up inside the ceiling or under the floor doing it. */
    @Test
    fun itStaysOnSurfacesItCouldActuallyWalkOn() {
        val g = chaseSession()
        var t = 0f
        while (t < 20f && !g.cutIsDeath()) {
            g.update(dt, 1f, false, false, false)
            t += dt
            if (g.monster.roomId != g.roomId()) continue
            val b = g.room.bounds
            assertTrue(
                "the pursuit left the room vertically (y=%.1f, room %.1f..%.1f)"
                    .format(g.monster.y, b.t, b.b),
                g.monster.y <= b.b + 1.5f && g.monster.y >= b.t - 0.5f
            )
        }
    }
}
