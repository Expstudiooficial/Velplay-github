package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.ArchiveCore
import com.expstudio.facilitycore.game.ArchiveNode
import com.expstudio.facilitycore.game.Cut
import com.expstudio.facilitycore.game.Door
import com.expstudio.facilitycore.game.GameSession
import com.expstudio.facilitycore.game.HeavyCrate
import com.expstudio.facilitycore.game.LaserEmitter
import com.expstudio.facilitycore.game.Prop
import com.expstudio.facilitycore.game.ReachAnchor
import com.expstudio.facilitycore.game.SnapValve
import com.expstudio.facilitycore.game.Stage3
import com.expstudio.facilitycore.game.SuperDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Plays Chapter 3 end to end with the real physics, the real story machine and
 * nothing the player does not have: a direction, JUMP, SNEAK, USE, DODGE and
 * REACH.
 *
 * A hundred rooms is too many to hold in a head. This is the thing that knows
 * whether they all still connect.
 */
class Chapter3PlaythroughTest {

    private val dt = 1f / 60f

    @Test
    fun theChapterCanBePlayedFromTheDeckToTheCore() {
        val g = Bot.session(Stage3.LAVA, chapter = 3)
        val run = Run(g)

        run.wait(14f, "watch the pour again") { g.stage >= Stage3.DESCEND }
        run.drive(1f, 300f, "walk the service level to the archive") { g.roomId() == "archive3" }
        run.archive("index the archive while it hunts you")
        run.drive(1f, 40f, "take the hand out of the core") { g.stage >= Stage3.JUKE }
        run.drive(1f, 60f, "get past it and up into the duct") { g.roomId() == "vent3" }
        run.duct(60f, "crawl the duct before it catches up") { g.roomId() == "seal3" }
        run.drive(1f, 40f, "bring the shutters down") { g.stage >= Stage3.RUN_B }
        run.drive(1f, 900f, "work east to the west bulkhead") { g.roomId() == "bigdoor3" }
        run.drive(1f, 60f, "find the way round") { g.roomId() == "c0" }
        run.drive(1f, 900f, "work through the collapse") { g.stage >= Stage3.BULKHEAD }
        run.drive(1f, 90f, "outrun it to the shutter") { g.stage >= Stage3.AFTER_BULK }
        run.drive(1f, 400f, "reach the lift") { g.roomId() == "lift3" }
        run.drive(1f, 40f, "ride down to Subfloor 3") { g.stage >= Stage3.DEEP }
        run.drive(1f, 2400f, "cross Subfloor 3 to the coolant gallery") { g.roomId() == "corepz3" }
        run.coolant("open the loop and route the coolant")
        run.drive(1f, 60f, "reach the master index") { g.roomId() == "superdb3" }
        run.download("download the master index")
        run.drive(1f, 300f, "walk the empty rooms to the car") { g.stage >= Stage3.STUCK_LIFT }
        run.drive(1f, 40f, "tear the car doors open") { g.stage >= Stage3.SHAFT }
        run.drive(1f, 60f, "climb the riser") { g.roomId() == "core3" }
        run.emitters("arm all four emitters")
        run.boss("survive both of them")
        run.sprint("run the evacuation and pull every valve")
        run.pit("cross the pit and take the landing away")

        assertTrue("the chapter never completed", g.completed)
    }

    private inner class Run(private val g: GameSession) {

        /** Sits still. Used for cutscenes, which take no input at all. */
        fun wait(timeout: Float, what: String, done: () -> Boolean) {
            var t = 0f
            while (t < timeout && !done()) {
                if (g.dialogueBlocking) g.tapDialogue()
                g.update(dt, 0f, false, false, false)
                t += dt
            }
            assertTrue("could not $what (stage=${g.stage})", done())
        }

        /** Walks to the rack, then stands on it and holds USE until it is done. */
        fun download(what: String) {
            val db = g.level.room("superdb3").props.filterIsInstance<SuperDatabase>().first()
            var t = 0f
            var stalled = 0f
            while (t < 90f && g.stage < Stage3.QUIET) {
                if (g.dialogueBlocking) g.tapDialogue()
                val onIt = g.focusProp === db
                val dir = if (onIt) 0f else approach(db)
                if (dir != 0f && abs(g.player.vx) < 0.5f && g.player.controlEnabled) stalled += dt
                else stalled = 0f
                val jump = stalled > 0.22f && g.player.onGround
                if (jump) stalled = 0f
                g.update(dt, dir, false, jump, onIt && g.focusLabel.isNotEmpty())
                t += dt
            }
            assertTrue("could not $what (stage=${g.stage}, taken=${db.taken})", g.stage >= Stage3.QUIET)
        }

        /** Stands on the spot and holds USE. For reads and downloads. */
        fun hold(timeout: Float, what: String, done: () -> Boolean) {
            var t = 0f
            while (t < timeout && !done()) {
                if (g.dialogueBlocking) g.tapDialogue()
                val use = g.focusLabel.isNotEmpty()
                g.update(dt, 0f, false, false, use)
                t += dt
            }
            assertTrue("could not $what (stage=${g.stage}, room=${g.roomId()})", done())
        }

        /**
         * Walks in one direction, solving what it meets: overlays, doors, the
         * hand, and a roll whenever something is mid-swing.
         */
        fun drive(dir: Float, timeout: Float, what: String, done: () -> Boolean) {
            val bot = Bot(g, dir).apply { useReach = true }
            var t = 0f
            var pause = 0f
            while (t < timeout && !done()) {
                g.overlay?.close(true)
                if (g.dialogueBlocking) g.tapDialogue()
                val slow = g.focusProp is Door
                when {
                    pause > 0f -> { pause -= dt; g.update(dt, 0f, false, false, false) }
                    g.cut != Cut.NONE -> g.update(dt, 0f, false, false, false)
                    else -> {
                        val hadLabel = g.focusLabel.isNotEmpty()
                        bot.step(dt)
                        if (hadLabel && slow) pause = 2.6f
                    }
                }
                t += dt
            }
            assertTrue(
                ("could not %s (room=%s, x=%.1f, stage=%d)")
                    .format(what, g.roomId(), g.player.x, g.stage),
                done()
            )
        }

        /** Crouch-runs the duct. Standing up in there just stops you. */
        fun duct(timeout: Float, what: String, done: () -> Boolean) {
            var t = 0f
            while (t < timeout && !done()) {
                if (g.dialogueBlocking) g.tapDialogue()
                g.update(dt, 1f, true, false, false)
                t += dt
            }
            assertTrue("could not $what (room=${g.roomId()}, x=%.1f)".format(g.player.x), done())
        }

        /**
         * The archive. Walk to whichever node the core is calling, press it, roll
         * when the Playerr commits to a swing. Three waves of it.
         */
        fun archive(what: String) {
            val core = g.level.room("archive3").props
                .filterIsInstance<ArchiveCore>().first()
            var t = 0f
            var stalled = 0f
            while (t < 900f && g.stage < Stage3.HAND) {
                if (g.dialogueBlocking) g.tapDialogue()
                val wanted = core.sequence.getOrNull(core.progress)
                val node = g.level.room("archive3").props
                    .filterIsInstance<ArchiveNode>().firstOrNull { it.index == wanted }
                // Before the core wakes up there is nothing to aim at, so walk
                // in far enough to be noticed.
                val dir = if (node == null) 1f else approach(node)
                val roll = g.monster.swing in 0.28f..0.40f && g.player.dodgeReady
                val use = g.focusProp === node && g.focusLabel.isNotEmpty()
                if (dir != 0f && abs(g.player.vx) < 0.5f && g.player.controlEnabled) stalled += dt
                else stalled = 0f
                val jump = stalled > 0.22f && g.player.onGround
                if (jump) stalled = 0f
                g.update(dt, dir, false, jump, use, roll)
                t += dt
            }
            assertTrue(
                "could not $what (stage=${g.stage}, wave=${core.wave}, health=${g.health})",
                g.stage >= Stage3.HAND
            )
        }

        /** Three valves, then the routing grid. */
        fun coolant(what: String) {
            var t = 0f
            var stalled = 0f
            while (t < 200f && g.stage < Stage3.SUPER_DB && g.roomId() == "corepz3") {
                g.overlay?.close(true)
                if (g.dialogueBlocking) g.tapDialogue()
                val valve = g.level.room("corepz3").props
                    .filterIsInstance<SnapValve>().firstOrNull { !it.done }
                val target: Prop? = valve ?: g.level.room("corepz3").props
                    .firstOrNull { it is com.expstudio.facilitycore.game.DataTerminal }
                val dir = approach(target)
                val use = g.focusProp === target && g.focusLabel.isNotEmpty()
                if (dir != 0f && abs(g.player.vx) < 0.5f && g.player.controlEnabled) stalled += dt
                else stalled = 0f
                val jump = stalled > 0.22f && g.player.onGround
                if (jump) stalled = 0f
                g.update(dt, dir, false, jump, use, false)
                t += dt
            }
            val opened = (g.level.room("corepz3").props
                .firstOrNull { it is Door && it.name == "door_corepz" } as? Door)?.open == true
            assertTrue("could not $what (stage=${g.stage})", opened)
        }

        /** Four emitters, in whatever order walking finds them. */
        fun emitters(what: String) {
            var t = 0f
            var stalled = 0f
            while (t < 200f && g.stage < Stage3.CORE_LIT) {
                if (g.dialogueBlocking) g.tapDialogue()
                val target = g.level.room("core3").props
                    .filterIsInstance<LaserEmitter>().filter { !it.armed }
                    .minByOrNull { abs(it.box.cx - g.player.x) }
                val dir = approach(target)
                val use = g.focusProp === target && g.focusLabel.isNotEmpty()
                if (dir != 0f && abs(g.player.vx) < 0.5f && g.player.controlEnabled) stalled += dt
                else stalled = 0f
                val jump = stalled > 0.22f && g.player.onGround
                if (jump) stalled = 0f
                g.update(dt, dir, false, jump, use, false)
                t += dt
            }
            assertTrue("could not $what (stage=${g.stage})", g.stage >= Stage3.CORE_LIT)
        }

        /**
         * Keep away from both of them until the floor goes.
         *
         * Backing away from the nearest one walks you into a corner and dies
         * there, so this heads for whichever stretch of floor is furthest from
         * both — which is also what a player does without being told.
         */
        fun boss(what: String) {
            var t = 0f
            while (t < 200f && g.stage < Stage3.FINAL_RUN) {
                if (g.dialogueBlocking) g.tapDialogue()
                if (g.cut != Cut.NONE) { g.update(dt, 0f, false, false, false); t += dt; continue }
                val bounds = g.room.bounds
                var bestX = g.player.x
                var bestScore = -1f
                var x = bounds.l + 2f
                while (x < bounds.r - 2f) {
                    val score = minOf(abs(x - g.monster.x), abs(x - g.monster2.x))
                    if (score > bestScore) { bestScore = score; bestX = x }
                    x += 1f
                }
                val dir = when {
                    bestX > g.player.x + 1.2f -> 1f
                    bestX < g.player.x - 1.2f -> -1f
                    else -> 0f
                }
                val near = minOf(abs(g.player.x - g.monster.x), abs(g.player.x - g.monster2.x))
                val roll = (g.monster.swing in 0.28f..0.40f || g.monster2.swing in 0.28f..0.40f) &&
                    g.player.dodgeReady && near < 4.5f
                g.update(dt, dir, false, false, false, roll)
                t += dt
            }
            assertTrue("could not $what (stage=${g.stage}, health=${g.health})", g.stage >= Stage3.FINAL_RUN)
        }

        /** The run out: east, pulling every valve that is on the way. */
        fun sprint(what: String) {
            val bot = Bot(g, 1f).apply { interactLabels = setOf("PULL") }
            var t = 0f
            while (t < 200f && g.stage < Stage3.PIT) {
                if (g.dialogueBlocking) g.tapDialogue()
                if (g.cutIsDeath()) break
                bot.step(dt)
                t += dt
            }
            assertTrue(
                "could not $what (stage=${g.stage}, room=${g.roomId()})",
                g.stage >= Stage3.PIT
            )
        }

        /** Across on the hand, then the crate back over the landing. */
        fun pit(what: String) {
            // Cross first: the bar is the only way over.
            val bot = Bot(g, 1f).apply { useReach = true }
            var t = 0f
            while (t < 60f && g.player.x < 24f) {
                if (g.dialogueBlocking) g.tapDialogue()
                bot.step(dt)
                t += dt
            }
            assertTrue("could not get across the pit (x=%.1f)".format(g.player.x), g.player.x >= 24f)

            // The hand hauls what it grabs toward the hand, so the crate is
            // brought west by standing west of it and facing back at it — at the
            // near lip of the pit, which is exactly where the landing was.
            val crate = g.level.room("pit3").props.filterIsInstance<HeavyCrate>().first()
            t = 0f
            var faced = false
            while (t < 120f && g.stage < Stage3.ENDING) {
                if (g.dialogueBlocking) g.tapDialogue()
                if (g.cut != Cut.NONE) { g.update(dt, 0f, false, false, false); t += dt; continue }
                val brace = 22.8f
                var dir = 0f
                if (!g.reach.busy) {
                    when {
                        g.player.x > brace + 0.35f -> { dir = -1f; faced = false }
                        !faced -> { dir = 1f; faced = true }   // one step east sets the facing
                        else -> dir = 0f
                    }
                }
                val fire = !g.reach.busy && g.reach.ready && faced && dir == 0f &&
                    g.reach.pick(g) === crate
                g.update(dt, dir, false, false, false, false, fire, true)
                t += dt
            }
            assertTrue(
                "could not $what (stage=${g.stage}, crate at %.1f)".format(crate.box.l),
                g.stage >= Stage3.ENDING
            )

            wait(20f, "watch them go in") { g.stage >= Stage3.COMPLETE }
        }

        /** -1, 0 or 1 toward a prop, with a dead zone so it can settle on it. */
        private fun approach(target: Prop?): Float {
            if (target == null) return 0f
            val cx = (target.box.l + target.box.r) * 0.5f
            return when {
                cx > g.player.x + 0.7f -> 1f
                cx < g.player.x - 0.7f -> -1f
                else -> 0f
            }
        }
    }

    /** The riser has to be climbable with the hand and nothing else. */
    @Test
    fun theRiserCanBeClimbedWithTheHand() {
        val g = Bot.session(Stage3.SHAFT, chapter = 3)
        g.enterRoomForTest("shaft3", 2f, 0f)
        g.reach.unlocked = true
        val bot = Bot(g, 1f).apply { useReach = true }
        var t = 0f
        while (t < 90f && g.roomId() == "shaft3") {
            if (g.dialogueBlocking) g.tapDialogue()
            bot.step(dt)
            t += dt
        }
        assertTrue("the riser could not be climbed (y=%.1f)".format(g.player.y), g.roomId() == "core3")
    }

    /** The anchors the story depends on all have to actually fire. */
    @Test
    fun theHandSealsTheDuctShutters() {
        val g = Bot.session(Stage3.SEAL, chapter = 3)
        g.enterRoomForTest("seal3", 4f, 0f)
        g.reach.unlocked = true
        val shutter = g.level.room("seal3").props
            .filterIsInstance<ReachAnchor>().first { it.id == "shutter_vent" }
        var t = 0f
        while (t < 30f && !shutter.spent) {
            val fire = g.reach.ready && g.reach.pick(g) === shutter
            g.update(dt, 0f, false, false, false, false, fire, true)
            t += dt
        }
        assertTrue("the shutters never came down", shutter.spent)
    }
}
