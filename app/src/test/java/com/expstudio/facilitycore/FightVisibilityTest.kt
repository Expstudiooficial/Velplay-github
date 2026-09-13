package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Monster
import com.expstudio.facilitycore.game.Stage
import com.expstudio.facilitycore.game.Stage2
import com.expstudio.facilitycore.game.Stage3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A fight the player cannot see is not a fight.
 *
 * Chapter 3's hunters were being placed on whatever solid sat highest over
 * their x — which in every room is the ceiling slab. They spent both set-pieces
 * fifteen metres up, off the top of the screen, while the swings still landed.
 * This keeps them on the floor.
 */
class FightVisibilityTest {

    private val dt = 1f / 60f

    /** How far above the player's own feet a hunter may ever be and still count. */
    private val sameFloorTolerance = 5f

    private fun runFight(stage: Int, chapter: Int, room: String, seconds: Float): List<String> {
        val g = Bot.session(stage, chapter)
        g.enterRoomForTest(room, g.room.bounds.r * 0.5f, 0f)
        val problems = ArrayList<String>()
        var t = 0f
        while (t < seconds) {
            if (g.dialogueBlocking) g.tapDialogue()
            g.update(dt, 0f, false, false, false)
            t += dt
            for ((name, m) in listOf("monster" to g.monster, "monster2" to g.monster2)) {
                if (m.mode == Monster.Mode.HIDDEN) continue
                if (m.roomId != g.roomId()) continue
                val above = g.player.y - m.y
                if (above > sameFloorTolerance) {
                    problems.add(
                        "%s in %s is %.1f m above the player (y=%.1f)".format(name, room, above, m.y)
                    )
                    return problems
                }
                if (m.y < g.room.bounds.t + 1f) {
                    problems.add("%s in %s is inside the ceiling (y=%.1f)".format(name, room, m.y))
                    return problems
                }
            }
        }
        return problems
    }

    @Test
    fun chapter3HuntersStayOnTheFloorTheyAreHuntingOn() {
        val problems = ArrayList<String>()
        problems += runFight(Stage3.ARCHIVE, 3, "archive3", 6f)
        problems += runFight(Stage3.BOSS, 3, "core3", 6f)
        assertTrue("hunters off the floor:\n  " + problems.joinToString("\n  "), problems.isEmpty())
    }

    /**
     * The archive is worth five lives, and says so.
     *
     * Three sequences, each longer than the last, learnt on a ziggurat with
     * something faster than you on the floor. At three lives the learning cost
     * a checkpoint every time.
     */
    @Test
    fun theArchiveIsFoughtWithFiveLives() {
        // From the checkpoint a death here actually returns to, and in through
        // the door, because walking in is what starts the fight.
        val g = Bot.session(Stage3.ARCHIVE, chapter = 3)
        g.enterRoomForTest("archive3", com.expstudio.facilitycore.game.Chapter3.ARCHIVE_TRIGGER_X + 0.5f, 0f)
        repeat(60) { g.update(dt, 0f, false, false, false) }
        assertEquals("the fight never started", Stage3.ARCHIVE_PUZZLE, g.stage)
        assertEquals("the archive is not a five-life fight", 5, g.maxHealth)
        assertEquals("it did not start on full", 5, g.health)
    }

    /**
     * Walking back out of the archive and in again must not drop it through
     * the floor.
     *
     * The hunter's height is read off whatever it is standing over, and the
     * search used to take the highest solid above it — which in a room with a
     * ceiling slab is the ceiling. Re-entering re-ran that search, and the
     * thing ended up under the ziggurat where nothing could reach it.
     */
    @Test
    fun leavingTheArchiveAndComingBackLeavesTheHunterOnTheFloor() {
        val g = Bot.session(Stage3.ARCHIVE, chapter = 3)
        g.enterRoomForTest("archive3", com.expstudio.facilitycore.game.Chapter3.ARCHIVE_TRIGGER_X + 0.5f, 0f)
        repeat(90) { g.update(dt, 0f, false, false, false) }
        val floor = g.monster.y
        assertTrue("it did not start on the floor (y=%.2f)".format(floor), floor > -2f)

        // Out through the back, then straight back in.
        val back = g.level.room("archive3").exits.first { it.toRoom != "juke3" }
        g.enterRoomForTest(back.toRoom, 6f, 0f)
        repeat(90) { g.update(dt, 0f, false, false, false) }
        g.enterRoomForTest("archive3", 6f, 0f)
        repeat(90) { g.update(dt, 0f, false, false, false) }

        val b = g.level.room("archive3").bounds
        assertTrue(
            "the hunter came back through the floor (y=%.2f, room %.1f..%.1f)".format(g.monster.y, b.t, b.b),
            g.monster.y <= b.b + 0.5f && g.monster.y >= b.t - 0.5f
        )
        assertTrue(
            "the hunter came back somewhere a swing could not reach from (y=%.2f)".format(g.monster.y),
            kotlin.math.abs(g.monster.y - floor) < 2.5f
        )
    }

    @Test
    fun chapter2LiftFightKeepsItInTheCar() {
        assertTrue(runFight(Stage2.LIFT_FIGHT, 2, "car", 5f).joinToString(), runFight(Stage2.LIFT_FIGHT, 2, "car", 5f).isEmpty())
    }

    /** Both hunters have to actually be in the room for the boss to be a boss. */
    @Test
    fun theLastFightHasBothOfThemInIt() {
        val g = Bot.session(Stage3.BOSS, chapter = 3)
        repeat(120) { g.update(dt, 0f, false, false, false) }
        assertTrue("the first hunter is not present", g.monster.mode != Monster.Mode.HIDDEN)
        assertTrue("the second hunter is not present", g.monster2.mode != Monster.Mode.HIDDEN)
        assertTrue("they are not in the player's room",
            g.monster.roomId == g.roomId() && g.monster2.roomId == g.roomId())
        assertTrue("they are the same thing twice", g.monster.kind != g.monster2.kind)
    }

    /**
     * The general rule, applied to every beat that retires a hunter.
     *
     * A monster must never stop existing while the player is looking at it. It
     * either leaves the frame first, or the frame leaves it — a full-screen
     * cutscene. Anything else reads as the game forgetting about it.
     */
    @Test
    fun noHunterIsEverSwitchedOffOnCamera() {
        val offences = ArrayList<String>()

        // Chapter 1: the thing that peers down the duct and then "leaves".
        val ch1 = Bot.session(Stage.VENT_CRAWL, 1)
        // Put him in the duct itself: the checkpoint for this stage is back at
        // the archive, and walking there is a different test's job.
        ch1.enterRoomForTest("vent", 2f, 0f)
        ch1.setStage(Stage.VENT_CRAWL)
        watchForPops("ch1 peer", ch1, 24f, offences) { g -> g.update(dt, 1f, true, false, false) }
        assertTrue("the peer beat never played", ch1.stage >= Stage.ELEVATOR_READY)

        // Chapter 2: the lift fight ending.
        val ch2 = Bot.session(Stage2.LIFT_FIGHT, 2)
        watchForPops("ch2 lift", ch2, 14f, offences) { g ->
            for (task in g.level.room("car").props
                .filterIsInstance<com.expstudio.facilitycore.game.LiftTask>()) task.done = true
            g.update(dt, 0f, false, false, false)
        }
        assertTrue("the car never dropped", ch2.stage >= Stage2.LIFT_FIXED)

        // Chapter 3: the last fight ending. Played rather than stood through —
        // standing still simply dies, and a beat that never happens proves
        // nothing at all.
        val ch3 = Bot.session(Stage3.BOSS, 3)
        watchForPops("ch3 boss", ch3, 60f, offences) { g -> dodgeBoth(g) }
        assertTrue("the last fight never ended (stage=${ch3.stage})", ch3.stage >= Stage3.FINAL_RUN)

        assertTrue("hunters switched off in plain sight:\n  " + offences.joinToString("\n  "), offences.isEmpty())
    }

    /**
     * Steps a session and records any frame where a visible hunter becomes
     * hidden while the world — rather than a full-screen scene — is on screen.
     */
    private fun watchForPops(
        label: String,
        g: com.expstudio.facilitycore.game.GameSession,
        seconds: Float,
        into: MutableList<String>,
        step: (com.expstudio.facilitycore.game.GameSession) -> Unit
    ) {
        g.camera.resize(1920, 1080)
        val wasVisible = HashMap<String, Boolean>()
        var t = 0f
        while (t < seconds) {
            for ((name, m) in listOf("monster" to g.monster, "monster2" to g.monster2)) {
                val onScreen = m.roomId == g.roomId() && g.camera.isVisible(m.bounds(), 0f)
                val visible = m.mode != Monster.Mode.HIDDEN && onScreen
                if (wasVisible[name] == true && !visible && onScreen && !fullScreenCut(g)) {
                    into.add("$label: $name vanished on camera at x=%.1f".format(m.x))
                }
                wasVisible[name] = visible
            }
            if (g.dialogueBlocking) g.tapDialogue()
            step(g)
            t += dt
        }
    }

    /** Cuts that replace the world with a scene of their own. */
    private fun fullScreenCut(g: com.expstudio.facilitycore.game.GameSession): Boolean =
        g.cut == com.expstudio.facilitycore.game.Cut.ENDING ||
            g.cut == com.expstudio.facilitycore.game.Cut.CH2_OPENING ||
            g.cut == com.expstudio.facilitycore.game.Cut.SMELTER_END ||
            g.cut == com.expstudio.facilitycore.game.Cut.CH3_LAVA ||
            g.cut == com.expstudio.facilitycore.game.Cut.CH3_ENDING ||
            g.cut == com.expstudio.facilitycore.game.Cut.DEATH

    /** Keeps to whichever floor is furthest from both, and rolls under swings. */
    private fun dodgeBoth(g: com.expstudio.facilitycore.game.GameSession) {
        if (g.cut != com.expstudio.facilitycore.game.Cut.NONE) {
            g.update(dt, 0f, false, false, false)
            return
        }
        val b = g.room.bounds
        var bestX = g.player.x
        var bestScore = -1f
        var x = b.l + 2f
        while (x < b.r - 2f) {
            val score = minOf(kotlin.math.abs(x - g.monster.x), kotlin.math.abs(x - g.monster2.x))
            if (score > bestScore) { bestScore = score; bestX = x }
            x += 1f
        }
        val dir = when {
            bestX > g.player.x + 1.2f -> 1f
            bestX < g.player.x - 1.2f -> -1f
            else -> 0f
        }
        val near = minOf(kotlin.math.abs(g.player.x - g.monster.x), kotlin.math.abs(g.player.x - g.monster2.x))
        val roll = (g.monster.swing in 0.28f..0.40f || g.monster2.swing in 0.28f..0.40f) &&
            g.player.dodgeReady && near < 4.5f
        g.update(dt, dir, false, false, false, roll)
    }

    /**
     * The hoist has to take hold of him. It used to lift him to a fixed height
     * beside claws that could not reach the floor in the first place.
     */
    @Test
    fun theHoistActuallyGrabsThePlayer() {
        val g = Bot.session(Stage2.HOIST_ESCAPE, chapter = 2)
        g.enterRoomForTest("hoist", 11f, 0f)
        val sw = g.level.room("hoist").props
            .filterIsInstance<com.expstudio.facilitycore.game.KeySwitch>().first()
        g.monster.place("hoist", 5f, 0f, 1)
        g.monster.mode = Monster.Mode.CHASING
        g.onSwitchUsedForTest(sw)

        val claws = g.level.room("hoist").props
            .filterIsInstance<com.expstudio.facilitycore.game.Grabbers>().first()
        var lifted = 0f
        var heldByClaws = true
        var t = 0f
        while (t < 5f) {
            g.update(dt, 0f, false, false, false)
            t += dt
            if (claws.holding) {
                lifted = minOf(lifted, g.player.y)
                // Held means held: the body hangs from the claw tips, not near them.
                if (kotlin.math.abs((g.player.y - claws.tipY())) > 2.0f) heldByClaws = false
                if (kotlin.math.abs(g.player.x - claws.box.cx) > 0.6f) heldByClaws = false
            }
        }
        assertTrue("the claws never closed on him", lifted < -2.5f)
        assertTrue("he was lifted beside the claws rather than by them", heldByClaws)
    }

    /** And it has to walk away, not blink out of existence in plain sight. */
    @Test
    fun itLeavesTheHoistBayOnFootRatherThanVanishing() {
        val g = Bot.session(Stage2.HOIST_ESCAPE, chapter = 2)
        g.enterRoomForTest("hoist", 11f, 0f)
        val sw = g.level.room("hoist").props
            .filterIsInstance<com.expstudio.facilitycore.game.KeySwitch>().first()
        g.monster.place("hoist", 5f, 0f, 1)
        g.monster.mode = Monster.Mode.CHASING
        g.onSwitchUsedForTest(sw)

        var lastVisibleX = Float.NEGATIVE_INFINITY
        var t = 0f
        while (t < 6f) {
            g.update(dt, 0f, false, false, false)
            t += dt
            if (g.monster.mode != Monster.Mode.HIDDEN) lastVisibleX = g.monster.x
        }
        assertTrue("it never left", lastVisibleX > Float.NEGATIVE_INFINITY)
        assertTrue(
            "it was switched off inside the room instead of walking out of it (x=%.1f of %.1f)"
                .format(lastVisibleX, g.level.room("hoist").bounds.r),
            lastVisibleX > g.level.room("hoist").bounds.r
        )
    }
}
