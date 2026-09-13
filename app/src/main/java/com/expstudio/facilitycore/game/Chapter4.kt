package com.expstudio.facilitycore.game

import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Palette

/**
 * Chapter 4 — Subfloor 4, and out.
 *
 * Progression values are persisted, so entries may only ever be appended.
 */
object Stage4 {
    const val CORE_REPLAY = 0        // the well takes them, again
    const val DESCENT = 1            // twenty rooms off the core deck
    const val LIFT5 = 2              // down to Subfloor 4
    const val DEEP4 = 3              // fifty rooms of it
    const val BIG_DOOR = 4           // the door the whole floor is built around
    const val LOCKS = 5              // three bolts, three rooms
    const val OPEN = 6               // it opens
    const val REN = 7                // he is in there
    const val TOGETHER = 8           // the duct, both of them
    const val LIFT_UP = 9            // the car
    const val ASCENT = 10            // up, and up, and then not
    const val OUT = 11
    const val COMPLETE = 12

    fun checkpointFor(stage: Int): Int = when {
        stage >= ASCENT -> ASCENT
        stage >= TOGETHER -> TOGETHER
        stage >= REN -> REN
        stage >= LOCKS -> LOCKS
        stage >= BIG_DOOR -> BIG_DOOR
        stage >= DEEP4 -> DEEP4
        stage >= LIFT5 -> LIFT5
        stage >= DESCENT -> DESCENT
        else -> CORE_REPLAY
    }

    fun objectiveFor(stage: Int): String = when (stage) {
        CORE_REPLAY -> "..."
        DESCENT -> "Get off the core deck"
        LIFT5 -> "Take the lift to Subfloor 4"
        DEEP4 -> "Subfloor 4. Keep going."
        BIG_DOOR -> "Three bolts. Three rooms."
        LOCKS -> "Release every bolt"
        OPEN -> "It's open"
        REN -> "Ren"
        TOGETHER -> "Get him out. The duct."
        LIFT_UP -> "The car. Both of you."
        ASCENT -> "Up"
        OUT -> "Out"
        else -> "Chapter 4 complete"
    }
}

/**
 * Builds Chapter 4.
 *
 * The same split as Chapter 3 — the set pieces and most corridors by hand in
 * [Rooms4], the rest composed — but the puzzles are different in kind. Chapter 4
 * has no grids to tap. Everything in it is solved by moving: weight on plates,
 * beams to time, decks that fall, air that lifts, a rail that pulls.
 */
object Chapter4 {

    const val DESCENT_ROOMS = 20
    const val DEEP_ROOMS = 50

    private const val SEED = 0x4C04L

    fun build(): Level {
        val rooms = LinkedHashMap<String, Room>()

        rooms["core4"] = buildCoreDeck()
        val descent = Compose.chain(
            rooms, "g", "Core Deck", DESCENT_ROOMS, SEED + 13L, Compose.Flavour.DEEP, budget = 3,
            authored = { i, id -> Rooms4.forChain("g", i, id) }
        )
        rooms["lift5"] = buildLift()
        val deep = Compose.chain(
            rooms, "h", "Subfloor 4", DEEP_ROOMS, SEED + 211L, Compose.Flavour.DEEP, budget = 4,
            authored = { i, id -> Rooms4.forChain("h", i, id) }
        )
        rooms["bigdoor4"] = buildBigDoor()
        rooms["lock4a"] = Rooms4.lockA()
        rooms["lock4b"] = Rooms4.lockB()
        rooms["lock4c"] = Rooms4.lockC()
        rooms["ren4"] = buildRenRoom()
        rooms["vent4"] = buildVent()
        rooms["liftup4"] = buildLiftUp()

        link(rooms, descent, deep)
        return Level(rooms)
    }

    private fun room(id: String, title: String, w: Float, h: Float): Room =
        Room(id, title, Box(0f, -h, w, 0f))

    // ---- set pieces ------------------------------------------------------

    /** The deck above the well, still warm. Where Chapter 3 left him standing. */
    private fun buildCoreDeck(): Room = room("core4", "Core Head", 30f, 12f).apply {
        solid(-1f, -13f, 32f, 1f)
        solid(-1f, -12f, 1f, 12f)
        solid(-1f, 0f, 15.5f, 1f)
        solid(21f, 0f, 11f, 1f)
        decorBox(0f, -0.5f, 30f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        props.add(CoreWell(Box.of(15.5f, -0.2f, 5.5f, 11.6f)).also { it.charge = 1f })
        decorBox(14.8f, -0.9f, 0.7f, 0.9f, Palette.WARN, Decor.Kind.STRIPE)
        decorBox(21.0f, -0.9f, 0.7f, 0.9f, Palette.WARN, Decor.Kind.STRIPE)
        // The crate he jammed the landing with. Still there.
        solid(22.4f, -1.7f, 1.7f, 1.7f, Solid.Kind.CRATE)
        decorBox(3f, -11.4f, 24f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
    }

    private fun buildLift(): Room = room("lift5", "Lift to Subfloor 4", 18f, 9f).apply {
        shell(openLeft = true)
        decorBox(0f, -0.5f, 18f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(11f, -8.4f, 6f, 0.4f, Palette.ACCENT, Decor.Kind.LIGHT).lit = true
        props.add(Elevator(Box.of(13.0f, -4.2f, 3.4f, 4.2f)))
    }

    /** The door the whole floor is built around, and the three bolts holding it. */
    private fun buildBigDoor(): Room = room("bigdoor4", "Containment Face", 30f, 13f).apply {
        shell()
        decorBox(0f, -0.5f, 30f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(4f, -12.4f, 22f, 0.4f, Palette.ACCENT, Decor.Kind.LIGHT).lit = true

        // The face of it: far too large, and set into the wall rather than on it.
        decorBox(19.5f, -11.5f, 10f, 11.5f, Palette.WALL, Decor.Kind.PANEL)
        val big = Door(Box.of(21.0f, -9.5f, 2.4f, 9.5f), "door_big4")
        big.locked = true
        big.manual = true
        big.lockMessage = "Three bolts. None of them here."
        props.add(big)

        // The way down to the bolt rooms.
        solid(9.0f, -4.8f, 3.6f, 3.4f)
        decorBox(9.4f, -1.35f, 2.8f, 0.2f, Palette.WARN, Decor.Kind.STRIPE)
        solid(5.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
    }

    /** Where he is. Nothing else in the room matters. */
    private fun buildRenRoom(): Room = room("ren4", "Holding", 26f, 10f).apply {
        shell()
        decorBox(0f, -0.5f, 26f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(6f, -9.4f, 14f, 0.4f, Palette.ACCENT, Decor.Kind.LIGHT).lit = true
        // Cells, all of them open, one of them not.
        var x = 3f
        while (x < 20f) {
            decorBox(x, -5.4f, 0.3f, 5.4f, Palette.TRIM, Decor.Kind.PIPE)
            x += 2.2f
        }
        props.add(Ren(Box.of(17.5f, -1.75f, 0.8f, 1.75f)))
        solid(11.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)

        // The duct out, once there are two of them to take it.
        decorBox(23.4f, -2.9f, 2.4f, 1.5f, Palette.VOID, Decor.Kind.GRATE)
    }

    private fun buildVent(): Room = room("vent4", "Return Duct", 34f, 2.0f).apply {
        shell()
        var x = 1.5f
        while (x < 33f) {
            decorBox(x, -1.94f, 0.9f, 0.14f, Palette.TRIM, Decor.Kind.PIPE)
            x += 3.1f
        }
    }

    /** The car they both get into. */
    private fun buildLiftUp(): Room = room("liftup4", "Surface Car", 20f, 9f).apply {
        shell()
        decorBox(0f, -0.5f, 20f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(6f, -8.4f, 8f, 0.5f, Palette.GOOD, Decor.Kind.LIGHT).lit = true
        props.add(Elevator(Box.of(14.0f, -4.2f, 3.4f, 4.2f)))
        solid(3.0f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
    }

    // ---- wiring ----------------------------------------------------------

    private fun link(rooms: LinkedHashMap<String, Room>, descent: List<String>, deep: List<String>) {
        fun r(id: String) = rooms.getValue(id)
        fun door(roomId: String, name: String): Door? =
            rooms[roomId]?.props?.firstOrNull { it is Door && it.name == name } as? Door

        Compose.join(r("core4"), r(descent.first()))
        Compose.join(r(descent.last()), r("lift5"))
        // The lift is ridden, not walked out of.
        Compose.join(r("lift5"), r(deep.first()))
        r("lift5").exits.removeAll { it.toRoom == deep.first() }
        r(deep.first()).exits.removeAll { it.toRoom == "lift5" }

        Compose.join(r(deep.last()), r("bigdoor4"))

        // Down the hatch to the bolts, round the three rooms, and back up.
        r("bigdoor4").exits.add(Exit(Box(9.2f, -1.35f, 12.8f, 0f), "lock4a", 2.0f, 0f))
        r("lock4a").exits.add(Exit(Box(-1.0f, -10f, 0.3f, 0f), "bigdoor4", 7.8f, 0f))
        Compose.join(r("lock4a"), r("lock4b"))
        Compose.join(r("lock4b"), r("lock4c"))
        r("lock4c").exits.add(Exit(Box(r("lock4c").bounds.r - 0.3f, -10f, r("lock4c").bounds.r + 1f, 0f),
            "bigdoor4", 14.0f, 0f))

        r("bigdoor4").exits.add(
            Exit(Box(23.4f, -13f, 24.6f, 0f), "ren4", 2.0f, 0f, door("bigdoor4", "door_big4"))
        )
        r("ren4").exits.add(Exit(Box(-1.0f, -10f, 0.3f, 0f), "bigdoor4", 20.0f, 0f))
        // The duct is only reachable once he is on his feet and following.
        r("ren4").exits.add(Exit(Box(23.4f, -2.9f, 25.8f, -1.4f), "vent4", 2.0f, 0f))
        r("vent4").exits.add(Exit(Box(33.0f, -2f, 34.4f, 0f), "liftup4", 2.0f, -3.0f))
    }

    /** Reconciles a freshly built level with a saved stage. */
    fun applyStage(g: GameSession, stage: Int) {
        val rooms = g.level.rooms
        g.reach.unlocked = true
        g.dodgeUnlocked = true

        val ren = rooms["ren4"]?.props?.firstOrNull { it is Ren } as? Ren
        ren?.following = stage >= Stage4.TOGETHER
        ren?.found = stage >= Stage4.REN

        if (stage >= Stage4.OPEN) {
            (rooms["bigdoor4"]?.props?.firstOrNull { it is Door && it.name == "door_big4" } as? Door)?.forceOpen()
        }
    }

    fun spawnFor(stage: Int): Triple<String, Float, Float> = when {
        stage >= Stage4.ASCENT -> Triple("liftup4", 6.5f, 0f)
        stage >= Stage4.TOGETHER -> Triple("ren4", 3.0f, 0f)
        stage >= Stage4.REN -> Triple("ren4", 3.0f, 0f)
        stage >= Stage4.BIG_DOOR -> Triple("bigdoor4", 3.0f, 0f)
        stage >= Stage4.DEEP4 -> Triple("h0", 3.0f, 0f)
        stage >= Stage4.LIFT5 -> Triple("lift5", 3.0f, 0f)
        stage >= Stage4.DESCENT -> Triple("g0", 3.0f, 0f)
        // Clear of the crate he jammed the landing with.
        else -> Triple("core4", 26.5f, 0f)
    }
}
