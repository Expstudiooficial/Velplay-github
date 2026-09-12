package com.expstudio.facilitycore.game

import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Palette

/**
 * Builds the connective tissue of Chapter 3.
 *
 * Chapter 3 is about a hundred rooms long. Authoring every one by hand would
 * either take forever or produce a hundred identical corridors, so the set
 * pieces stay hand-written and the stretches between them are composed here
 * from vetted parts.
 *
 * The important property is that composition can never produce an impassable
 * room. Every slot template below is traversable in both directions using only
 * walking, one jump, the mantle, and — where marked — a crouch. Steps never
 * exceed [MAX_STEP], gaps never exceed [MAX_GAP], and anything too low to walk
 * through is at least [Player.CROUCH_HEIGHT] tall and at least a body wide.
 * `LevelIntegrityTest` re-checks all of that against the built level, and the
 * bot walks every composed room end to end.
 */
object Compose {

    /** A climb the player can make from standing. Deliberately conservative. */
    const val MAX_STEP = 1.05f

    /** A gap a running jump clears with room to spare (flat range is ~2.9 m). */
    const val MAX_GAP = 2.0f

    const val FLOOR_Y = 0f

    /** What a composed room is for. Drives which slots it is allowed to use. */
    enum class Flavour {
        /** Early Subfloor 2: intact, lit, mostly tidy. */
        SERVICE,

        /** The detour: half-collapsed, crates and spill. */
        WRECK,

        /** Subfloor 3: deep, wet, structural. */
        DEEP,

        /** The final run: stripped out, fast, nothing to climb. */
        SPRINT
    }

    /**
     * A tiny deterministic generator. Not a good PRNG — a repeatable one, which
     * is what matters: the same seed must lay out the same room every launch, or
     * a saved checkpoint would load into different geometry.
     */
    class Rng(seed: Long) {
        private var s: Long = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

        fun next(): Long {
            s = s xor (s shl 13)
            s = s xor (s ushr 7)
            s = s xor (s shl 17)
            return s
        }

        fun int(bound: Int): Int {
            if (bound <= 1) return 0
            val v = (next() ushr 1) % bound
            return v.toInt()
        }

        fun float(): Float = (next() ushr 11).toFloat() / (1L shl 53).toFloat()
        fun range(lo: Float, hi: Float): Float = lo + (hi - lo) * float()
        fun chance(p: Float): Boolean = float() < p
        fun <T> pick(items: List<T>): T = items[int(items.size)]
    }

    /** One obstacle placed into a room, occupying [width] metres of floor. */
    private enum class Slot(val width: Float) {
        /** Nothing. Rooms need air or they read as an assault course. */
        OPEN(3.2f),

        /** A crate you step onto and off again. */
        STEP(3.0f),

        /** Two crates into a short stair. */
        STAIR(4.4f),

        /** A shelf at mantle height with a drop on the far side. */
        SHELF(5.0f),

        /**
         * A pipe run low enough that you have to go under it.
         *
         * Wide for what it contains: the trailing metres are the point. Coming
         * out of a crawl you are still crouched, and a step placed right at the
         * mouth cannot be stood up to, let alone jumped.
         */
        DUCK(4.8f),

        /** A raised walkway with a section missing. Jump it, or drop and pass under. */
        TRENCH(4.6f),

        /** A waist-high barrier: jump, mantle, drop. */
        BARRIER(2.8f),

        /** A fallen column leaning on the wall. Cosmetic, plus one step. */
        DEBRIS(3.8f)
    }

    private fun slotsFor(f: Flavour): List<Slot> = when (f) {
        Flavour.SERVICE -> listOf(Slot.OPEN, Slot.OPEN, Slot.STEP, Slot.DUCK, Slot.SHELF, Slot.BARRIER)
        Flavour.WRECK -> listOf(Slot.OPEN, Slot.STAIR, Slot.DEBRIS, Slot.TRENCH, Slot.STEP, Slot.DUCK, Slot.BARRIER)
        Flavour.DEEP -> listOf(Slot.OPEN, Slot.TRENCH, Slot.SHELF, Slot.STAIR, Slot.DUCK, Slot.DEBRIS, Slot.BARRIER)
        Flavour.SPRINT -> listOf(Slot.OPEN, Slot.OPEN, Slot.OPEN, Slot.BARRIER, Slot.STEP)
    }

    /**
     * Composes one room.
     *
     * [budget] is how many slots to try to fit; the room is sized to hold them
     * plus clear landings at both doorways, so the exits are never buried.
     */
    fun room(
        id: String,
        title: String,
        seed: Long,
        flavour: Flavour,
        budget: Int = 3,
        height: Float = 8.5f,
        needsPower: Boolean = false
    ): Room {
        val rng = Rng(seed)
        val pool = slotsFor(flavour)
        val chosen = ArrayList<Slot>(budget)
        var span = 0f
        var lastDuck = -9
        var i = 0
        while (i < budget) {
            var s = rng.pick(pool)
            // Never two crouch gaps back to back: crawling twice in six metres
            // feels like a bug rather than a level.
            if (s == Slot.DUCK && i - lastDuck < 2) s = Slot.OPEN
            if (s == Slot.DUCK) {
                // Clear floor on both sides of a crawl. Going in you need room
                // to drop; coming out you are still crouched and cannot stand
                // up, let alone jump, with anything overhead — so a step at
                // either mouth is a wall rather than an obstacle.
                lastDuck = i
                chosen.add(Slot.OPEN)
                chosen.add(s)
                chosen.add(Slot.OPEN)
                span += s.width + Slot.OPEN.width * 2f
                i++
            } else {
                chosen.add(s)
                span += s.width
            }
            i++
        }
        val pad = LANDING * 2f
        val width = (span + pad).coerceIn(14f, 34f)

        val r = Room(id, title, Box(0f, -height, width, 0f), needsPower)
        r.shell(openLeft = true, openRight = true)
        dress(r, rng, flavour, width, height)

        var x = LANDING
        for (s in chosen) {
            // Slots are laid left to right and clipped to the clear landing at
            // the far door, so a long slot can never overrun the exit.
            if (x + s.width > width - LANDING) break
            place(r, rng, s, x, flavour)
            x += s.width
        }
        return r
    }

    /** Clear floor kept at both doorways, so an arrival never lands on a crate. */
    private const val LANDING = 3.4f

    private fun place(r: Room, rng: Rng, s: Slot, x: Float, flavour: Flavour) {
        when (s) {
            Slot.OPEN -> {
                if (rng.chance(0.4f)) {
                    r.decorBox(x + 0.6f, -0.36f, rng.range(1.6f, 3.0f), 0.24f, Palette.TRIM, Decor.Kind.PIPE)
                }
            }
            Slot.STEP -> {
                val h = rng.range(0.8f, MAX_STEP)
                r.solid(x + 0.8f, -h, rng.range(1.2f, 1.8f), h, Solid.Kind.CRATE)
            }
            Slot.STAIR -> {
                val h1 = rng.range(0.7f, 0.95f)
                val h2 = h1 + rng.range(0.6f, MAX_STEP)
                r.solid(x + 0.5f, -h1, 1.4f, h1, Solid.Kind.CRATE)
                r.solid(x + 1.9f, -h2, 1.5f, h2, Solid.Kind.CRATE)
            }
            Slot.SHELF -> {
                // A deck with two honest routes: mantle onto it, or duck straight
                // under. There is deliberately nothing on the floor beneath it —
                // a step crate in the crawl route makes the crawl impossible,
                // because clearing a crate needs a standing body.
                val h = rng.range(1.85f, 2.05f)
                r.solid(x + 0.9f, -h, rng.range(2.8f, 3.6f), 0.4f, Solid.Kind.PLATFORM)
                r.decorBox(x + 0.9f, -h - 0.16f, 0.5f, 0.16f, Palette.WARN, Decor.Kind.STRIPE)
            }
            Slot.DUCK -> {
                // A crouch gap: the underside sits above CROUCH_HEIGHT with slack,
                // and the pipe is drawn thick so it reads from a distance.
                val clear = 1.10f
                r.solid(x + 0.4f, -4.2f, 2.6f, 4.2f - clear)
                r.decorBox(x + 0.4f, -clear - 0.3f, 2.6f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
            }
            Slot.TRENCH -> {
                val w = rng.range(1.5f, MAX_GAP)
                val depth = 1.0f
                val x = x + 0.8f  // lead-in, so the walkway never starts on a doorway
                // Deliberately two routes: clear the gap along the top, or ignore
                // the walkway entirely and walk the floor beneath it.
                r.solid(x, -depth, 1.4f, depth, Solid.Kind.PLATFORM)
                r.solid(x + 1.4f + w, -depth, 1.6f, depth, Solid.Kind.PLATFORM)
                r.decorBox(x + 1.4f, -0.12f, w, 0.12f, Palette.WARN, Decor.Kind.STRIPE)
            }
            Slot.BARRIER -> {
                val h = rng.range(1.15f, 1.45f)
                r.solid(x + 0.9f, -h, 0.5f, h)
                r.decorBox(x + 0.9f, -h - 0.14f, 0.5f, 0.14f, Palette.WARN, Decor.Kind.STRIPE)
            }
            Slot.DEBRIS -> {
                val h = rng.range(0.85f, MAX_STEP)
                r.solid(x + 0.6f, -h, 2.0f, h, Solid.Kind.CRATE)
                r.decorBox(x + 0.4f, -3.2f, 0.4f, 3.2f - h, Palette.WALL_LIT, Decor.Kind.PANEL)
            }
        }
    }

    /**
     * Everything that is only there to be looked at. Lights are what actually
     * distinguish the flavours: how many, how far apart, and how many of them
     * still work.
     */
    private fun dress(r: Room, rng: Rng, flavour: Flavour, width: Float, height: Float) {
        val (spacing, deadChance) = when (flavour) {
            Flavour.SERVICE -> 6.5f to 0.18f
            Flavour.WRECK -> 9.0f to 0.55f
            Flavour.DEEP -> 10.5f to 0.62f
            Flavour.SPRINT -> 7.5f to 0.30f
        }
        var lx = rng.range(2f, 4f)
        while (lx < width - 1.5f) {
            val lit = !rng.chance(deadChance)
            r.decorBox(lx, -height + 0.35f, rng.range(1.6f, 2.8f), 0.28f,
                if (lit) Palette.WARN else Palette.WALL_LIT, Decor.Kind.LIGHT).lit = lit
            lx += spacing * rng.range(0.75f, 1.3f)
        }

        // A structural band at head height gives the eye a horizon to read the
        // room's depth against.
        r.decorBox(0f, -height * 0.62f, width, 0.18f, Palette.WALL_LIT, Decor.Kind.PANEL)

        // Conduit along the ceiling, broken into runs so it never looks printed on.
        var px = rng.range(0f, 3f)
        while (px < width) {
            val runW = rng.range(3f, 7f)
            r.decorBox(px, -height + 0.9f, minOf(runW, width - px), 0.22f, Palette.TRIM, Decor.Kind.PIPE)
            px += runW + rng.range(0.6f, 2.4f)
        }

        if (flavour == Flavour.WRECK || flavour == Flavour.DEEP) {
            var gx = rng.range(1f, 5f)
            while (gx < width - 2f) {
                r.decorBox(gx, -1.2f, rng.range(0.8f, 1.6f), 1.2f, Palette.VOID, Decor.Kind.GRATE)
                gx += rng.range(7f, 14f)
            }
        }
    }

    /**
     * Chains composed rooms into a corridor and returns the ids in order.
     *
     * Links are plain floor-level exits, placed just outside each room's shell
     * so walking off the end of one room arrives on the landing of the next.
     */
    fun chain(
        rooms: LinkedHashMap<String, Room>,
        prefix: String,
        title: String,
        count: Int,
        seed: Long,
        flavour: Flavour,
        budget: Int = 3,
        /**
         * Lets the chapter hand-place any index in the run. Composition is for
         * the stretches nobody should remember individually; everything with a
         * character of its own is authored and arrives through here.
         */
        authored: (Int, String) -> Room? = { _, _ -> null }
    ): List<String> {
        val ids = ArrayList<String>(count)
        var i = 0
        while (i < count) {
            val id = "$prefix$i"
            rooms[id] = authored(i, id) ?: room(id, "$title ${i + 1}/$count", seed + i * 7919L, flavour,
                budget = budget, height = if (flavour == Flavour.DEEP) 9.5f else 8.5f)
            ids.add(id)
            i++
        }
        linkChain(rooms, ids)
        return ids
    }

    /** Joins an ordered list of rooms front to back. */
    fun linkChain(rooms: LinkedHashMap<String, Room>, ids: List<String>) {
        var i = 0
        while (i < ids.size - 1) {
            join(rooms.getValue(ids[i]), rooms.getValue(ids[i + 1]))
            i++
        }
    }

    /** Two-way floor-level link: [a]'s right edge to [b]'s left edge. */
    fun join(a: Room, b: Room, gate: Door? = null) {
        val ab = a.bounds
        val bb = b.bounds
        a.exits.add(Exit(Box(ab.r - 0.3f, ab.t, ab.r + 1.0f, 0f), b.id, LANDING * 0.5f, 0f, gate))
        b.exits.add(Exit(Box(bb.l - 1.0f, bb.t, bb.l + 0.3f, 0f), a.id, ab.r - LANDING * 0.5f, 0f))
    }
}
