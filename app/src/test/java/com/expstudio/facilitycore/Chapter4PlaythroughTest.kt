package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Cut
import com.expstudio.facilitycore.game.Door
import com.expstudio.facilitycore.game.GameSession
import com.expstudio.facilitycore.game.HeavyCrate
import com.expstudio.facilitycore.game.Prop
import com.expstudio.facilitycore.game.Ren
import com.expstudio.facilitycore.game.SlapSwitch
import com.expstudio.facilitycore.game.Stage4
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Plays Chapter 4 from the core deck to the daylight.
 *
 * Chapter 4's puzzles are mechanical rather than modal, so this cannot close an
 * overlay and move on the way the Chapter 3 run does — it has to actually stand
 * on plates, haul crates onto beams and ride columns of air. Anything it cannot
 * do, a player cannot do either.
 */
class Chapter4PlaythroughTest {

    private val dt = 1f / 60f

    /** How far below the player the ledge has to be before stepping off the column. */
    private val RIDE_CLEAR = 4f

    /** Comfortably inside the arm's reach, with room for the hand's own offset. */
    private val HAUL_SPAN = 6.5f

    @Test
    fun theChapterCanBePlayedFromTheDeckToTheDaylight() {
        val g = Bot.session(Stage4.CORE_REPLAY, chapter = 4)
        val run = Run(g)

        run.wait(15f, "watch the well take them") { g.stage >= Stage4.DESCENT }
        run.drive(1500f, "work down the core deck to the lift") { g.roomId() == "lift5" }
        run.drive(40f, "ride down to Subfloor 4") { g.stage >= Stage4.DEEP4 }
        run.drive(4000f, "cross Subfloor 4 to the containment face") { g.roomId() == "bigdoor4" }
        run.drive(150f, "drop into the bolt rooms") { g.roomId() == "lock4a" }
        run.bolts("release all three bolts")
        run.drive(300f, "get back to the face") { g.roomId() == "bigdoor4" }
        run.drive(200f, "through the door") { g.roomId() == "ren4" }
        run.findRen("find Ren")
        run.drive(250f, "take the duct out together") { g.roomId() == "liftup4" }
        run.drive(40f, "get in the car") { g.stage >= Stage4.ASCENT }
        run.wait(40f, "ride it up and out") { g.stage >= Stage4.COMPLETE }

        assertTrue("the chapter never completed", g.completed)
    }

    private inner class Run(private val g: GameSession) {

        /** Rooms whose mechanism has been satisfied at least once. */
        private val solved = HashSet<String>()

        /** Set while the answer to the room is a column of air. */
        private var riding = false

        fun wait(timeout: Float, what: String, done: () -> Boolean) {
            var t = 0f
            while (t < timeout && !done()) {
                if (g.dialogueBlocking) g.tapDialogue()
                g.update(dt, 0f, false, false, false)
                t += dt
            }
            assertTrue("could not $what (stage=${g.stage})", done())
        }

        /**
         * Walks east, solving what it meets. Chapter 4 needs more of the
         * vocabulary than Chapter 3 did: it hits switches on sight, rolls
         * through beams, and hauls any crate it can reach onto whatever is in
         * front of it.
         */
        fun drive(timeout: Float, what: String, done: () -> Boolean) {
            val bot = Bot(g, 1f).apply { useReach = true }
            var t = 0f
            var pause = 0f
            while (t < timeout && !done()) {
                if (g.dialogueBlocking) g.tapDialogue()
                val slow = g.focusProp is Door
                when {
                    pause > 0f -> { pause -= dt; g.update(dt, 0f, false, false, false) }
                    g.cut != Cut.NONE -> g.update(dt, 0f, false, false, false)
                    solveMechanisms(bot) -> Unit
                    else -> {
                        bot.dir = 1f
                        val had = g.focusLabel.isNotEmpty()
                        bot.step(dt)
                        if (had && slow) pause = 2.4f
                    }
                }
                t += dt
            }
            assertTrue(
                "could not %s (room=%s, x=%.1f, stage=%d)".format(what, g.roomId(), g.player.x, g.stage),
                done()
            )
        }

        /**
         * Works whatever mechanism this room has, returning false when there is
         * nothing left to do. Chapter 4's rooms cannot be walked past, so this
         * is the vocabulary a player needs and therefore the vocabulary the
         * test needs: hit the switch, ride the column, haul the crate, stand on
         * the plate.
         */
        fun solveMechanisms(bot: Bot): Boolean {
            // A room is solved once, not continuously. The plate you stand on
            // to open the way out un-presses the moment you step off it, and
            // the door it opened does not close again — so without this the
            // solver would turn round and walk back onto the plate for ever.
            //
            // The door is the authority, not the memory: dying rebuilds the
            // level, and a room we remember solving can be shut again when we
            // come back through it.
            if (g.room.id in solved) {
                val was = g.room.props
                    .firstOrNull { it is Door && it.name == "door_${g.room.id}" } as? Door
                if (was == null || was.open) return false
                solved.remove(g.room.id)
            }

            val plates = g.room.props.filterIsInstance<com.expstudio.facilitycore.game.PressurePlate>()
            val beams = g.room.props.filterIsInstance<com.expstudio.facilitycore.game.BlockBeam>()
            val crates = g.room.props.filterIsInstance<HeavyCrate>()
            val switches = g.room.props.filterIsInstance<SlapSwitch>()
            val drafts = g.room.props.filterIsInstance<com.expstudio.facilitycore.game.Updraft>()
            if (plates.isEmpty() && beams.isEmpty()) return false
            if (plates.all { it.pressed } && beams.all { it.blocked }) {
                solved.add(g.room.id)
                return false
            }

            // Anything that needs switching on, first.
            switches.firstOrNull { !it.used }?.let { approachAndUse(it); return true }

            // A beam with a crate to stop it.
            val beam = beams.firstOrNull { !it.blocked }
            if (beam != null && crates.isNotEmpty()) { haul(crates.first(), centre(beam)); return true }

            // Crates hold every plate but one; the body holds the last.
            //
            // Deliberately decided by which plates have a crate on them rather
            // than by which are currently pressed: standing on the last plate
            // makes it pressed, which would otherwise make the solver think the
            // job had moved on and walk straight back off it.
            fun hasCrate(p: Prop) = crates.any { abs(centre(it) - centre(p)) < 1.3f }
            val uncovered = plates.filter { !hasCrate(it) }
            if (uncovered.size > 1) {
                val loose = crates.filter { c -> plates.none { abs(centre(c) - centre(it)) < 1.3f } }
                if (loose.isNotEmpty()) {
                    val plate = uncovered.minByOrNull { abs(centre(it) - centre(loose.first())) }!!
                    haul(loose.first(), centre(plate))
                    return true
                }
            }

            val target = uncovered.firstOrNull { !it.pressed }
                ?: plates.firstOrNull { !it.pressed }
                ?: return false

            // A counterweighted deck: weight in the pan brings it down, and
            // taking the weight back out from on top of it is what sends you
            // up. Backwards, which is the puzzle.
            val deck = g.room.props
                .filterIsInstance<com.expstudio.facilitycore.game.Counterweight>().firstOrNull()
            if (deck != null && crates.isNotEmpty() && target.box.t < g.player.y - 1.5f) {
                val crate = crates.first()
                val aboard = g.player.x > deck.box.l + 0.4f && g.player.x < deck.box.r - 0.4f &&
                    abs(g.player.y - deck.box.t) < 0.6f
                when {
                    !deck.loaded && !aboard -> { haul(crate, deck.panCentre()); return true }
                    !aboard -> {
                        // Crossing to the deck is walking, and the crate is in
                        // the way of it. Let the bot hop what it meets.
                        if (abs(centre(deck) - g.player.x) > 1.3f) {
                            bot.dir = if (centre(deck) > g.player.x) 1f else -1f
                            bot.step(dt)
                        } else standOn(deck)
                        return true
                    }
                    deck.loaded -> { haul(crate, deck.box.l + 2.6f); return true }
                    deck.box.t > target.box.t + 0.9f -> {
                        g.update(dt, 0f, false, false, false)
                        return true
                    }
                }
            }

            // Above the player: ride whatever lifts, then walk onto it.
            //
            // Riding is a mode rather than a moment. Step out of the column the
            // instant the ledge is nominally above you and you are still level
            // with its underside, with nothing but the drop back to the floor —
            // so stay in the air until the ledge is a clear four metres below,
            // and let the fall carry you across the gap onto it.
            val draft = drafts.firstOrNull { it.running }
            if (draft != null && target.box.t < g.player.y - 1.5f) riding = true
            // Step off when the ledge is a clear drop below — or, in a shaft
            // too short for that, once the column has carried you as high as
            // it goes and is only holding you against the ceiling.
            val topped = draft != null && g.player.y <= draft.box.t + 1.0f
            if (riding && (draft == null || topped || g.player.y < target.box.t - RIDE_CLEAR)) {
                riding = false
            }
            if (riding && draft != null) {
                val inColumn = g.player.x > draft.box.l + 0.4f && g.player.x < draft.box.r - 0.4f
                if (!inColumn && g.player.onGround) {
                    // Getting to the column is walking, and things stand in the
                    // way of walking. Let the bot hop them.
                    bot.dir = if (centre(draft) > g.player.x) 1f else -1f
                    bot.step(dt)
                } else {
                    val dir = if (inColumn) 0f else if (centre(draft) > g.player.x) 1f else -1f
                    g.update(dt, dir, false, false, false)
                }
                return true
            }
            bot.dir = if (centre(target) > g.player.x + 0.4f) 1f else if (centre(target) < g.player.x - 0.4f) -1f else 0f
            if (bot.dir == 0f) { standOn(target); return true }
            bot.step(dt)
            return true
        }

        /** The three bolt rooms, each solved by moving rather than tapping. */
        private val boltBot = Bot(g, 1f).apply { useReach = true }

        fun bolts(what: String) {
            var t = 0f
            while (t < 600f && g.stage < Stage4.OPEN) {
                if (g.dialogueBlocking) g.tapDialogue()
                if (g.cut != Cut.NONE) { g.update(dt, 0f, false, false, false); t += dt; continue }
                if (!solveMechanisms(boltBot)) { boltBot.dir = 1f; boltBot.step(dt) }
                t += dt
            }
            assertTrue("could not $what (stage=${g.stage}, room=${g.roomId()})", g.stage >= Stage4.OPEN)
        }

        private fun centre(p: Prop) = (p.box.l + p.box.r) * 0.5f

        private fun standOn(p: Prop) {
            val dir = when {
                centre(p) > g.player.x + 0.35f -> 1f
                centre(p) < g.player.x - 0.35f -> -1f
                else -> 0f
            }
            val jump = dir != 0f && abs(g.player.vx) < 0.4f && g.player.onGround
            g.update(dt, dir, false, jump, false)
        }

        private fun approachAndUse(p: Prop) {
            val onIt = g.focusProp === p && g.focusLabel.isNotEmpty()
            val dir = if (onIt) 0f else when {
                centre(p) > g.player.x + 0.6f -> 1f
                centre(p) < g.player.x - 0.6f -> -1f
                else -> 0f
            }
            val jump = !onIt && dir != 0f && abs(g.player.vx) < 0.4f && g.player.onGround
            g.update(dt, dir, false, jump, onIt)
        }

        /**
         * Drags a crate toward [toX]. The hand hauls what it grabs toward the
         * player, so the player has to stand on the far side of the crate from
         * where it needs to go and face back at it.
         */
        private fun haul(crate: HeavyCrate, toX: Float) {
            val cx = centre(crate)
            val fromRight = toX < cx
            // Brace beyond the crate on the side it has to travel toward — but
            // never further from it than the arm actually reaches, or the haul
            // is a player standing still waiting for a grip they cannot make.
            // A long haul is therefore several short ones, which is how a
            // player does it too.
            val brace = if (fromRight) maxOf(toX - 1.4f, cx - HAUL_SPAN)
                        else minOf(toX + 1.4f, cx + HAUL_SPAN)
            var dir = 0f
            var fire = false
            if (!g.reach.busy) {
                dir = when {
                    g.player.x > brace + 0.4f -> -1f
                    g.player.x < brace - 0.4f -> 1f
                    else -> 0f
                }
                // One step toward the crate sets the facing the haul needs.
                if (dir == 0f && ((crate.box.l > g.player.x) != (g.player.facing > 0))) {
                    dir = if (crate.box.l > g.player.x) 1f else -1f
                } else if (dir == 0f) {
                    fire = g.reach.ready && g.reach.pick(g) === crate
                }
            }
            val jump = dir != 0f && abs(g.player.vx) < 0.4f && g.player.onGround && !g.reach.busy
            g.update(dt, dir, false, jump, false, false, fire, true)
        }

        fun findRen(what: String) {
            val ren = g.level.room("ren4").props.filterIsInstance<Ren>().first()
            var t = 0f
            while (t < 90f && g.stage < Stage4.TOGETHER) {
                if (g.dialogueBlocking) g.tapDialogue()
                approachAndUse(ren)
                t += dt
            }
            assertTrue("could not $what (stage=${g.stage})", g.stage >= Stage4.TOGETHER)
            assertTrue("he is not following", ren.following)
        }
    }
}
