package com.expstudio.facilitycore.game

import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Palette

/**
 * Chapter 3 — Subfloors 2 and 3, and the end of it.
 *
 * Progression values are persisted, so entries may only ever be appended.
 */
object Stage3 {
    const val LAVA = 0               // the pour, watched through the glass
    const val DESCEND = 1            // service rooms below the smelter
    const val ARCHIVE = 2            // reach the archive
    const val ARCHIVE_PUZZLE = 3     // index the archive while it hunts you
    const val HAND = 4               // take what is in the core
    const val JUKE = 5               // get past it to the duct
    const val VENT_RUN = 6           // the long duct
    const val SEAL = 7               // bring the shutters down behind you
    const val RUN_B = 8              // the quiet stretch
    const val BIG_DOOR = 9           // not in the database
    const val DETOUR = 10            // the long way round
    const val BULKHEAD = 11          // it comes back
    const val AFTER_BULK = 12
    const val LIFT3 = 13             // down to Subfloor 3
    const val DEEP = 14              // the long descent
    const val DEEP_CHASE = 15
    const val CORE_PUZZLE = 16       // the last real puzzle
    const val SUPER_DB = 17
    const val QUIET = 18             // three rooms with nothing in them
    const val STUCK_LIFT = 19        // the car stops between floors
    const val SHAFT = 20
    const val LASER_ROOM = 21
    const val CORE_LIT = 22
    const val BOSS = 23              // both of them
    const val FINAL_RUN = 24
    const val PIT = 25
    const val ENDING = 26
    const val COMPLETE = 27

    fun checkpointFor(stage: Int): Int = when {
        stage >= PIT -> PIT
        stage >= FINAL_RUN -> FINAL_RUN
        stage >= BOSS -> BOSS
        stage >= LASER_ROOM -> LASER_ROOM
        stage >= STUCK_LIFT -> STUCK_LIFT
        stage >= SUPER_DB -> SUPER_DB
        stage >= CORE_PUZZLE -> CORE_PUZZLE
        stage >= DEEP -> DEEP
        stage >= AFTER_BULK -> AFTER_BULK
        stage >= DETOUR -> DETOUR
        stage >= RUN_B -> RUN_B
        stage >= VENT_RUN -> VENT_RUN
        stage >= HAND -> HAND
        stage >= ARCHIVE -> ARCHIVE
        else -> LAVA
    }

    fun objectiveFor(stage: Int): String = when (stage) {
        LAVA -> "Watch"
        DESCEND -> "Get down to the archive level"
        ARCHIVE -> "Find the archive"
        ARCHIVE_PUZZLE -> "Index the archive — and don't stop moving"
        HAND -> "Take what the core is holding"
        JUKE -> "Get past it to the duct"
        VENT_RUN -> "Through the duct. It is following."
        SEAL -> "Bring the shutters down behind you"
        RUN_B -> "Keep going east"
        BIG_DOOR -> "That door isn't in the database. Find another way."
        DETOUR -> "The long way round"
        BULKHEAD -> "RUN"
        AFTER_BULK -> "Keep moving"
        LIFT3 -> "Take the lift to Subfloor 3"
        DEEP -> "Subfloor 3. Find the core room."
        DEEP_CHASE -> "RUN"
        CORE_PUZZLE -> "Bring the coolant loop up"
        SUPER_DB -> "Download the master index"
        QUIET -> "..."
        STUCK_LIFT -> "The car is stuck. Open it."
        SHAFT -> "Up the shaft"
        LASER_ROOM -> "Arm every emitter"
        CORE_LIT -> "The core is lit"
        BOSS -> "Both of them. Dodge."
        FINAL_RUN -> "RUN — and pull every valve on the way"
        PIT -> "Get across"
        ENDING -> "..."
        else -> "Chapter 3 complete"
    }
}

/**
 * Builds Chapter 3.
 *
 * Roughly forty per cent of this file is hand-placed: the archive, the duct, the
 * lift, the core room, the pit. The stretches between them come out of
 * [Compose], which guarantees the traversal rules Chapter 1 and 2 follow by
 * hand — steps at or under 1.05 m, gaps at or under 2.0 m, and every low gap
 * tall enough to crawl.
 */
object Chapter3 {

    const val CHASE_SECONDS = 48f
    const val DEEP_CHASE_SECONDS = 54f
    // A clean crawl of the duct takes 23 seconds, so 26 left a player who
    // hesitated once with nothing. The tension in here is the sound behind
    // you, not a stopwatch you cannot see.
    const val VENT_SECONDS = 34f
    /**
     * How long the boss stays in the room before the floor gives out.
     *
     * Two attackers closing from opposite ends of a corridor is a squeeze, and
     * a squeeze is only fair while it is short. Thirty-eight seconds of it was
     * a wall; twenty-six is a fight.
     */
    const val BOSS_SECONDS = 26f

    /** Where the Playerr notices you in the archive. */
    const val ARCHIVE_TRIGGER_X = 9f

    // Composed chain sizes, straight out of the chapter outline.
    const val RUN_A_ROOMS = 4
    const val RUN_B_ROOMS = 11
    const val DETOUR_ROOMS = 10
    const val AFTER_ROOMS = 5
    const val DEEP_ROOMS = 30
    const val QUIET_ROOMS = 3
    const val SPRINT_ROOMS = 20

    private const val SEED = 0x3C03L

    fun build(): Level {
        val rooms = LinkedHashMap<String, Room>()

        rooms["obs3"] = buildObservation()
        val runA = Compose.chain(rooms, "a", "Service", RUN_A_ROOMS, SEED + 11L, Compose.Flavour.SERVICE, budget = 3,
            authored = { i, id -> Rooms3.forChain("a", i, id) })
        rooms["archive3"] = buildArchive()
        rooms["juke3"] = buildJuke()
        rooms["vent3"] = buildVent()
        rooms["seal3"] = buildSeal()
        val runB = Compose.chain(rooms, "b", "East Spur", RUN_B_ROOMS, SEED + 101L, Compose.Flavour.SERVICE, budget = 4,
            authored = { i, id -> Rooms3.forChain("b", i, id) })
        rooms["bigdoor3"] = buildBigDoor()
        val detour = Compose.chain(rooms, "c", "Collapse", DETOUR_ROOMS, SEED + 211L, Compose.Flavour.WRECK, budget = 4,
            authored = { i, id -> Rooms3.forChain("c", i, id) })
        rooms["bulkA"] = buildBulkA()
        rooms["bulkB"] = buildBulkB()
        rooms["bulkC"] = buildBulkC()
        val after = Compose.chain(rooms, "d", "Sublevel Walk", AFTER_ROOMS, SEED + 307L, Compose.Flavour.WRECK, budget = 3,
            authored = { i, id -> Rooms3.forChain("d", i, id) })
        rooms["lift3"] = buildLift3()
        val deep = Compose.chain(rooms, "e", "Subfloor 3", DEEP_ROOMS, SEED + 401L, Compose.Flavour.DEEP, budget = 4,
            authored = { i, id -> Rooms3.forChain("e", i, id) })
        rooms["deepspan"] = buildDeepSpan()
        rooms["deephall"] = buildDeepHall()
        rooms["corepz3"] = buildCorePuzzle()
        rooms["superdb3"] = buildSuperDb()
        val quiet = Compose.chain(rooms, "q", "Empty", QUIET_ROOMS, SEED + 503L, Compose.Flavour.DEEP, budget = 0,
            authored = { i, id -> Rooms3.forChain("q", i, id) })
        rooms["lift4"] = buildLift4()
        rooms["shaft3"] = buildShaft()
        rooms["core3"] = buildCoreRoom()
        val sprint = Compose.chain(rooms, "f", "Evacuation", SPRINT_ROOMS, SEED + 601L, Compose.Flavour.SPRINT, budget = 2,
            authored = { i, id -> Rooms3.forChain("f", i, id) })
        rooms["pit3"] = buildPit()

        link(rooms, runA, runB, detour, after, deep, quiet, sprint)
        return Level(rooms)
    }

    private fun room(id: String, title: String, w: Float, h: Float): Room =
        Room(id, title, Box(0f, -h, w, 0f))

    // ---- hand-written rooms ---------------------------------------------

    /** Where Chapter 2's pour is replayed, then walked away from. */
    private fun buildObservation(): Room = room("obs3", "Observation Deck", 24f, 9f).apply {
        shell(openRight = true)
        decorBox(0f, -0.5f, 24f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        // The window wall. What is behind it is drawn by the smelter scene.
        decorBox(2f, -7.4f, 13f, 5.2f, Palette.VOID, Decor.Kind.GRATE)
        decorBox(2f, -7.4f, 13f, 0.26f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(2f, -2.2f, 13f, 0.26f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(16f, -6.2f, 4f, 0.4f, Palette.WARN, Decor.Kind.LIGHT).lit = true
        solid(17.5f, -1.0f, 1.6f, 1.0f, Solid.Kind.CRATE)
    }

    /**
     * The archive: a tall room with the index nodes spread across three heights,
     * so the sequence cannot be walked — it has to be climbed, while something
     * that is faster than you shares the floor.
     */
    private fun buildArchive(): Room = room("archive3", "Archive", 30f, 11f).apply {
        shell()
        decorBox(0f, -0.5f, 30f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(3f, -10.4f, 24f, 0.4f, Palette.ACCENT, Decor.Kind.LIGHT).lit = true

        // A ziggurat of stacks. Solid blocks rather than thin decks: a deck has
        // an underside, and an underside one metre above the step below it is a
        // ceiling you cannot stand under.
        solid(4.4f, -1.0f, 2.6f, 1.0f)
        solid(7.0f, -2.0f, 2.6f, 2.0f)
        solid(9.6f, -3.0f, 2.6f, 3.0f)
        solid(12.2f, -4.0f, 5.6f, 4.0f)
        solid(17.8f, -3.0f, 2.6f, 3.0f)
        solid(20.4f, -2.0f, 2.6f, 2.0f)
        solid(23.0f, -1.0f, 2.6f, 1.0f)

        // Five nodes across four heights: the sequence cannot be walked.
        props.add(ArchiveNode(Box.of(2.2f, -1.9f, 0.9f, 1.0f), 0))
        props.add(ArchiveNode(Box.of(8.0f, -2.9f, 0.9f, 1.0f), 1))
        props.add(ArchiveNode(Box.of(27.0f, -1.9f, 0.9f, 1.0f), 2))
        props.add(ArchiveNode(Box.of(14.6f, -4.9f, 0.9f, 1.0f), 3))
        props.add(ArchiveNode(Box.of(21.4f, -2.9f, 0.9f, 1.0f), 4))

        props.add(ArchiveCore(Box.of(13.0f, -7.4f, 4.0f, 2.0f)))

        val out = Door(Box.of(28.4f, -3.6f, 0.8f, 3.6f), "door_arch")
        out.locked = true
        out.manual = false
        props.add(out)

        // The way back. It stands open until the thing in here notices you, and
        // then it does not. Leaving mid-fight and returning used to strand the
        // hunter in the geometry; sealing the room is both the honest fix and
        // the better beat.
        val back = Door(Box.of(-0.2f, -3.6f, 0.8f, 3.6f), "door_arch_back")
        back.manual = false
        back.forceOpen()
        props.add(back)
    }

    /** The room where it gets between you and the door, and the duct is high. */
    private fun buildJuke(): Room = room("juke3", "Sorting Floor", 26f, 10f).apply {
        shell()
        decorBox(0f, -0.5f, 26f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(4f, -9.4f, 6f, 0.4f, Palette.WARN, Decor.Kind.LIGHT).lit = true

        solid(6.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
        solid(11.0f, -1.6f, 3.0f, 0.4f, Solid.Kind.PLATFORM)
        solid(16.5f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)

        // The duct mouth, five metres up. Nothing in the room reaches it.
        decorBox(21.5f, -7.4f, 3.4f, 1.6f, Palette.VOID, Decor.Kind.GRATE)
        props.add(ReachAnchor(Box.of(21.8f, -6.0f, 2.8f, 0.28f), "bar_vent", ReachAnchor.Kind.BAR))
        // A lip to stand on once the hand has done its work.
        solid(21.5f, -5.8f, 3.4f, 0.4f, Solid.Kind.PLATFORM)
    }

    /** The duct itself: long, low, and behind you the whole way. */
    private fun buildVent(): Room = room("vent3", "Return Duct", 46f, 2.0f).apply {
        shell()
        var x = 1.5f
        while (x < 45f) {
            decorBox(x, -1.94f, 0.9f, 0.14f, Palette.TRIM, Decor.Kind.PIPE)
            x += 3.1f
        }
        // Ribs, so 46 metres of duct still has a sense of travel.
        var g = 4f
        while (g < 44f) {
            decorBox(g, -1.9f, 0.25f, 1.9f, Palette.WALL_LIT, Decor.Kind.PANEL)
            g += 6.5f
        }
    }

    /** Where the duct lets out, and where it can be shut. */
    private fun buildSeal(): Room = room("seal3", "Duct Head", 24f, 9f).apply {
        shell()
        decorBox(0f, -0.5f, 24f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(6f, -8.4f, 8f, 0.4f, Palette.WARN, Decor.Kind.LIGHT).lit = true
        solid(2.2f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)

        // The duct comes out high; the shutters hang over its mouth.
        decorBox(0.2f, -5.6f, 2.4f, 1.6f, Palette.VOID, Decor.Kind.GRATE)
        props.add(ReachAnchor(Box.of(0.6f, -6.6f, 2.2f, 2.2f), "shutter_vent", ReachAnchor.Kind.SHUTTER))

        val out = Door(Box.of(22.2f, -3.6f, 0.8f, 3.6f), "door_seal")
        out.locked = true
        out.manual = false
        props.add(out)
    }

    /** A door the pack has never seen. The detour starts under it. */
    private fun buildBigDoor(): Room = room("bigdoor3", "West Bulkhead", 26f, 10f).apply {
        shell()
        decorBox(0f, -0.5f, 26f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(3f, -9.4f, 8f, 0.4f, Palette.WARN, Decor.Kind.LIGHT).lit = true

        // The door itself: far too big, and sealed for the whole chapter until
        // the master index exists.
        val big = Door(Box.of(23.0f, -7.4f, 1.6f, 7.4f), "door_big")
        big.locked = true
        big.manual = true
        big.lockMessage = "Nothing. It isn't in the database at all."
        props.add(big)

        // The way round: a hatch at floor level on the near side.
        solid(12f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
        decorBox(16.5f, -1.35f, 2.6f, 0.2f, Palette.WARN, Decor.Kind.STRIPE)
        solid(16.0f, -4.6f, 3.6f, 3.25f)
    }

    private fun chaseRoom(id: String, title: String, w: Float): Room = room(id, title, w, 9f).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.5f, w, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        var lx = 3f
        while (lx < w - 2f) {
            decorBox(lx, -8.4f, 2.2f, 0.32f, Palette.BAD, Decor.Kind.LIGHT).lit = true
            lx += 9f
        }
    }

    private fun buildBulkA(): Room = chaseRoom("bulkA", "Transfer A", 30f).apply {
        solid(9f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
        solid(18f, -1.05f, 2.2f, 1.05f, Solid.Kind.CRATE)
    }

    private fun buildBulkB(): Room = chaseRoom("bulkB", "Transfer B", 30f).apply {
        solid(7f, -1.0f, 1.4f, 1.0f, Solid.Kind.CRATE)
        solid(14f, -1.6f, 3.2f, 0.4f, Solid.Kind.PLATFORM)
        solid(22f, -1.0f, 1.6f, 1.0f, Solid.Kind.CRATE)
    }

    /** The far side of the chase, and the shutter that ends it. */
    private fun buildBulkC(): Room = chaseRoom("bulkC", "Transfer C", 26f).apply {
        solid(8f, -1.0f, 1.6f, 1.0f, Solid.Kind.CRATE)
        val shutter = Door(Box.of(20.0f, -4.2f, 1.0f, 4.2f), "shutter3")
        shutter.forceOpen()
        shutter.manual = false
        props.add(shutter)
    }

    private fun buildLift3(): Room = room("lift3", "Lift to Subfloor 3", 18f, 9f).apply {
        shell(openLeft = true)
        decorBox(0f, -0.5f, 18f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(11f, -8.4f, 6f, 0.4f, Palette.ACCENT, Decor.Kind.LIGHT).lit = true
        props.add(Elevator(Box.of(13.0f, -4.2f, 3.4f, 4.2f)))
    }

    /** Subfloor 3's one genuinely vertical room, crossed with the hand. */
    private fun buildDeepSpan(): Room = room("deepspan", "Shaft Crossing", 30f, 14f).apply {
        shell()
        decorBox(0f, -0.5f, 30f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)

        // Three ledges climbing away from the floor, each out of jump range of
        // the last. The bars are the only way up.
        // The first ledge is a mantle from the floor, set back far enough to
        // take a run-up at. It deliberately has nothing under it: a step crate
        // beneath a deck cannot be jumped off, because the deck is the ceiling.
        solid(5.0f, -1.9f, 4.0f, 0.4f, Solid.Kind.PLATFORM)
        solid(11.0f, -5.6f, 4.6f, 0.4f, Solid.Kind.PLATFORM)
        solid(20.5f, -8.8f, 5.0f, 0.4f, Solid.Kind.PLATFORM)
        solid(25.6f, -11.6f, 4.0f, 0.4f, Solid.Kind.PLATFORM)

        props.add(ReachAnchor(Box.of(11.4f, -5.9f, 3.8f, 0.26f), "bar_span1", ReachAnchor.Kind.BAR))
        props.add(ReachAnchor(Box.of(20.9f, -9.1f, 4.2f, 0.26f), "bar_span2", ReachAnchor.Kind.BAR))
        props.add(ReachAnchor(Box.of(25.9f, -11.9f, 3.4f, 0.26f), "bar_span3", ReachAnchor.Kind.BAR))

        decorBox(25.6f, -13.6f, 4.0f, 1.6f, Palette.VOID, Decor.Kind.GRATE)
    }

    /** A big room, kept deliberately empty, because the next one is not. */
    private fun buildDeepHall(): Room = room("deephall", "Plant Hall", 40f, 16f).apply {
        shell()
        decorBox(0f, -0.5f, 40f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        var i = 0
        while (i < 5) {
            val x = 4f + i * 7.5f
            decorBox(x, -15.2f, 1.4f, 13.6f, Palette.WALL, Decor.Kind.PANEL)
            decorBox(x - 0.3f, -2.0f, 2.0f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
            i++
        }
        solid(12f, -1.0f, 2.4f, 1.0f, Solid.Kind.CRATE)
        solid(15.2f, -2.0f, 4.0f, 0.4f, Solid.Kind.PLATFORM)
        solid(26f, -1.05f, 2.0f, 1.05f, Solid.Kind.CRATE)
        decorBox(8f, -15.4f, 24f, 0.4f, Palette.ACCENT_DIM, Decor.Kind.LIGHT).lit = true
    }

    /** The coolant room. Three valves, then the loop itself. */
    private fun buildCorePuzzle(): Room = room("corepz3", "Coolant Gallery", 32f, 11f).apply {
        shell()
        decorBox(0f, -0.5f, 32f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(4f, -10.4f, 24f, 0.4f, Palette.ACCENT, Decor.Kind.LIGHT).lit = true

        solid(7.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
        solid(9.4f, -2.0f, 4.0f, 0.4f, Solid.Kind.PLATFORM)
        solid(18.0f, -1.05f, 2.0f, 1.05f, Solid.Kind.CRATE)
        solid(20.4f, -2.05f, 4.4f, 0.4f, Solid.Kind.PLATFORM)

        props.add(SnapValve(Box.of(3.0f, -2.2f, 0.9f, 1.0f), "cv_a", "coolant"))
        props.add(SnapValve(Box.of(10.6f, -3.2f, 0.9f, 1.0f), "cv_b", "coolant"))
        props.add(SnapValve(Box.of(22.0f, -3.2f, 0.9f, 1.0f), "cv_c", "coolant"))

        props.add(DataTerminal(Box.of(27.0f, -2.9f, 1.4f, 1.7f), "term_core", DataTerminal.Kind.FLOW, "door_corepz", 991L))

        val out = Door(Box.of(30.2f, -3.6f, 0.8f, 3.6f), "door_corepz")
        out.locked = true
        out.manual = false
        props.add(out)
    }

    private fun buildSuperDb(): Room = room("superdb3", "Master Index", 24f, 10f).apply {
        shell()
        decorBox(0f, -0.5f, 24f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(5f, -9.4f, 14f, 0.4f, Palette.ACCENT, Decor.Kind.LIGHT).lit = true
        var i = 0
        while (i < 6) {
            decorBox(4f + i * 3f, -6.5f, 1.6f, 6.5f, Palette.WALL, Decor.Kind.PANEL)
            i++
        }
        props.add(SuperDatabase(Box.of(10.0f, -4.6f, 4.0f, 4.6f)))
    }

    /** The car that does not arrive. */
    private fun buildLift4(): Room = room("lift4", "Lift Car", 13f, 8f).apply {
        shell()
        decorBox(0f, -0.4f, 13f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(3.5f, -7.8f, 6f, 0.45f, Palette.WARN, Decor.Kind.LIGHT).lit = true
        // The doors, jammed. The only way out is to take them apart.
        props.add(ReachAnchor(Box.of(10.4f, -4.4f, 2.0f, 4.4f), "rip_lift", ReachAnchor.Kind.RIP))
    }

    /** A vent, then a shaft that only goes up. */
    private fun buildShaft(): Room = room("shaft3", "Riser Shaft", 16f, 20f).apply {
        shell()
        decorBox(0f, -0.5f, 16f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        // Clear of the arrival point at x = 1.8.
        solid(3.0f, -1.0f, 1.6f, 1.0f, Solid.Kind.CRATE)
        solid(3.6f, -3.4f, 3.6f, 0.4f, Solid.Kind.PLATFORM)
        solid(9.0f, -6.6f, 4.0f, 0.4f, Solid.Kind.PLATFORM)
        solid(3.0f, -10.0f, 4.2f, 0.4f, Solid.Kind.PLATFORM)
        solid(9.2f, -13.4f, 4.4f, 0.4f, Solid.Kind.PLATFORM)
        solid(2.6f, -16.6f, 4.6f, 0.4f, Solid.Kind.PLATFORM)

        props.add(ReachAnchor(Box.of(3.9f, -3.7f, 3.0f, 0.26f), "bar_s1", ReachAnchor.Kind.BAR))
        props.add(ReachAnchor(Box.of(9.3f, -6.9f, 3.4f, 0.26f), "bar_s2", ReachAnchor.Kind.BAR))
        props.add(ReachAnchor(Box.of(3.3f, -10.3f, 3.6f, 0.26f), "bar_s3", ReachAnchor.Kind.BAR))
        props.add(ReachAnchor(Box.of(9.5f, -13.7f, 3.8f, 0.26f), "bar_s4", ReachAnchor.Kind.BAR))
        props.add(ReachAnchor(Box.of(2.9f, -16.9f, 4.0f, 0.26f), "bar_s5", ReachAnchor.Kind.BAR))

        val top = Door(Box.of(7.4f, -20.0f, 1.4f, 3.4f), "door_shaft")
        top.locked = true
        top.manual = false
        props.add(top)

        var y = -2f
        while (y > -19f) {
            decorBox(0.2f, y, 0.3f, 1.4f, Palette.TRIM, Decor.Kind.PIPE)
            decorBox(15.5f, y - 0.6f, 0.3f, 1.4f, Palette.TRIM, Decor.Kind.PIPE)
            y -= 2.6f
        }
    }

    /**
     * The core room. Glass down one side with the laser gallery behind it, four
     * emitters to arm, and a well in the floor that ends up full of star.
     */
    private fun buildCoreRoom(): Room = room("core3", "Ignition Gallery", 44f, 14f).apply {
        shell()
        decorBox(0f, -0.5f, 44f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        // The observation glass.
        decorBox(3f, -12.4f, 38f, 6.0f, Palette.VOID, Decor.Kind.GRATE)
        decorBox(3f, -12.4f, 38f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(3f, -6.6f, 38f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)

        solid(9f, -1.05f, 2.0f, 1.05f, Solid.Kind.CRATE)
        solid(11.4f, -2.05f, 4.0f, 0.4f, Solid.Kind.PLATFORM)
        solid(28f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
        solid(30.4f, -2.0f, 4.2f, 0.4f, Solid.Kind.PLATFORM)

        props.add(LaserEmitter(Box.of(5.0f, -2.6f, 1.2f, 1.4f), "em_a"))
        props.add(LaserEmitter(Box.of(13.0f, -3.6f, 1.2f, 1.4f), "em_b"))
        props.add(LaserEmitter(Box.of(31.5f, -3.6f, 1.2f, 1.4f), "em_c"))
        props.add(LaserEmitter(Box.of(39.0f, -2.6f, 1.2f, 1.4f), "em_d"))

        props.add(CoreWell(Box.of(19.0f, -1.6f, 6.0f, 1.6f)))

        val out = Door(Box.of(42.2f, -3.6f, 0.8f, 3.6f), "door_core")
        out.locked = true
        out.manual = false
        props.add(out)
    }

    /**
     * The last room. A hole in the floor over the core, a jump only the hand can
     * make, and a crate to put back across it once you are on the other side.
     */
    private fun buildPit(): Room = room("pit3", "Core Head", 34f, 12f).apply {
        // No shell floor here: the hole has to be a real absence of geometry, or
        // the crate and the fall both become fiction.
        solid(-1f, -13f, 36f, 1f)                 // ceiling
        solid(-1f, -12f, 1f, 12f)                 // left wall
        solid(34f, -12f, 1f, 12f)                 // right wall
        solid(-1f, 0f, 16.5f, 1f)                 // floor, near side
        solid(22.0f, 0f, 13f, 1f)                 // floor, far side
        decorBox(0f, -0.5f, 34f, 0.5f, Palette.WALL_LIT, Decor.Kind.STRIPE)

        // The floor stops at 15.5 and resumes at 22. Nothing jumps that — and
        // the bar has to sit inside the hand's range of the near lip, or a
        // checkpoint here is a room with no way out of it.
        props.add(CoreWell(Box.of(15.5f, -0.2f, 6.5f, 11.6f)).also { it.charge = 1f })
        decorBox(14.8f, -0.9f, 0.7f, 0.9f, Palette.WARN, Decor.Kind.STRIPE)
        decorBox(21.9f, -0.9f, 0.7f, 0.9f, Palette.WARN, Decor.Kind.STRIPE)

        props.add(ReachAnchor(Box.of(20.3f, -4.0f, 4.0f, 0.28f), "bar_pit", ReachAnchor.Kind.BAR))
        solid(22.2f, -3.8f, 4.0f, 0.4f, Solid.Kind.PLATFORM)

        // Clear floor between the landing platform and the crate. A step crate
        // used to sit here, and it walled off the only side of the crate the
        // hand can be braced from.
        props.add(HeavyCrate(Box.of(29.5f, -1.7f, 1.7f, 1.7f), "crate_pit"))
    }

    // ---- wiring ----------------------------------------------------------

    /** Gates the link between two composed rooms behind a terminal. */
    private fun lock(a: Room, b: Room, name: String, kind: DataTerminal.Kind, salt: Long) {
        // The chain already joined these two. Leaving that link in place would
        // make every terminal in the chapter optional.
        a.exits.removeAll { it.toRoom == b.id }
        b.exits.removeAll { it.toRoom == a.id }
        val ab = a.bounds
        val door = Door(Box.of(ab.r - 1.0f, -3.6f, 0.8f, 3.6f), name)
        door.locked = false
        door.manual = false
        a.props.add(door)
        a.props.add(DataTerminal(Box.of(ab.r - 2.8f, -2.9f, 1.3f, 1.6f), "term_$name", kind, name, salt))
        Compose.join(a, b, door)
    }

    /** Puts a run-past valve in a sprint room. */
    private fun valve(r: Room, id: String) {
        r.props.add(SnapValve(Box.of(r.bounds.r * 0.5f, -2.4f, 0.9f, 1.0f), id, "sprint"))
    }

    private fun link(
        rooms: LinkedHashMap<String, Room>,
        runA: List<String>,
        runB: List<String>,
        detour: List<String>,
        after: List<String>,
        deep: List<String>,
        quiet: List<String>,
        sprint: List<String>
    ) {
        fun r(id: String) = rooms.getValue(id)
        fun door(roomId: String, name: String): Door? =
            rooms[roomId]?.props?.firstOrNull { it is Door && it.name == name } as? Door

        // Observation deck into the first service room.
        Compose.join(r("obs3"), r(runA.first()))
        // Two of the four service rooms carry a lock, so the stretch has shape.
        lock(r(runA[1]), r(runA[2]), "door_a1", DataTerminal.Kind.BREAKERS, 11L)

        Compose.join(r(runA.last()), r("archive3"))
        r("archive3").exits.removeAll { it.toRoom == runA.last() }
        r("archive3").exits.add(
            Exit(Box(-1.0f, -11f, 0.4f, 0f), runA.last(), 3.0f, 0f, door("archive3", "door_arch_back"))
        )
        r("archive3").exits.add(
            Exit(Box(29.4f, -11f, 30.6f, 0f), "juke3", 1.6f, 0f, door("archive3", "door_arch"))
        )
        r("juke3").exits.add(Exit(Box(-0.7f, -10f, 0.4f, 0f), "archive3", 27.6f, 0f))
        // The duct mouth, only reachable from the lip the hand puts you on.
        r("juke3").exits.add(Exit(Box(22.6f, -7.4f, 24.6f, -5.4f), "vent3", 1.6f, 0f))
        r("vent3").exits.add(Exit(Box(45.0f, -2f, 46.4f, 0f), "seal3", 1.8f, -4.2f))
        r("seal3").exits.add(
            Exit(Box(23.0f, -9f, 24.4f, 0f), runB.first(), 1.6f, 0f, door("seal3", "door_seal"))
        )
        r(runB.first()).exits.add(Exit(Box(-1.0f, -8.5f, 0.3f, 0f), "seal3", 21.0f, 0f))

        lock(r(runB[2]), r(runB[3]), "door_b2", DataTerminal.Kind.FLOW, 23L)
        lock(r(runB[5]), r(runB[6]), "door_b5", DataTerminal.Kind.WIRING, 37L)
        lock(r(runB[8]), r(runB[9]), "door_b8", DataTerminal.Kind.BREAKERS, 41L)

        Compose.join(r(runB.last()), r("bigdoor3"))
        // The detour hatch, low on the right-hand wall.
        r("bigdoor3").exits.add(Exit(Box(16.2f, -1.35f, 19.4f, 0f), detour.first(), 2.0f, 0f))
        r(detour.first()).exits.add(Exit(Box(-1.0f, -8.5f, 0.3f, 0f), "bigdoor3", 15.0f, 0f))

        lock(r(detour[1]), r(detour[2]), "door_c1", DataTerminal.Kind.FLOW, 53L)
        lock(r(detour[3]), r(detour[4]), "door_c3", DataTerminal.Kind.WIRING, 59L)
        lock(r(detour[5]), r(detour[6]), "door_c5", DataTerminal.Kind.BREAKERS, 61L)
        lock(r(detour[7]), r(detour[8]), "door_c7", DataTerminal.Kind.FLOW, 67L)

        Compose.join(r(detour.last()), r("bulkA"))
        Compose.join(r("bulkA"), r("bulkB"))
        Compose.join(r("bulkB"), r("bulkC"))
        r("bulkC").exits.add(
            Exit(Box(21.4f, -9f, 22.6f, 0f), after.first(), 1.6f, 0f, door("bulkC", "shutter3"))
        )
        r(after.first()).exits.add(Exit(Box(-1.0f, -8.5f, 0.3f, 0f), "bulkC", 19.0f, 0f))

        lock(r(after[2]), r(after[3]), "door_d2", DataTerminal.Kind.WIRING, 71L)
        Compose.join(r(after.last()), r("lift3"))

        // Subfloor 3 is reached by riding the car, not by walking out of the
        // room, so lift3 has no eastward link at all.

        lock(r(deep[3]), r(deep[4]), "door_e3", DataTerminal.Kind.FLOW, 101L)
        lock(r(deep[7]), r(deep[8]), "door_e7", DataTerminal.Kind.BREAKERS, 103L)
        // The span breaks the chain in two: past here nothing is walkable.
        Compose.join(r(deep[11]), r("deepspan"))
        r("deepspan").exits.add(Exit(Box(25.4f, -13.6f, 29.6f, -11.6f), deep[12], 2.0f, 0f))
        r(deep[12]).exits.add(Exit(Box(-1.0f, -9.5f, 0.3f, 0f), "deepspan", 4.0f, -2.4f))
        // The chain's own [11]→[12] link is replaced by the span, so remove it.
        r(deep[11]).exits.removeAll { it.toRoom == deep[12] }
        r(deep[12]).exits.removeAll { it.toRoom == deep[11] }

        lock(r(deep[14]), r(deep[15]), "door_e14", DataTerminal.Kind.WIRING, 107L)
        lock(r(deep[18]), r(deep[19]), "door_e18", DataTerminal.Kind.FLOW, 109L)

        // The hall sits between 22 and 23, the same way the span does.
        Compose.join(r(deep[22]), r("deephall"))
        Compose.join(r("deephall"), r(deep[23]))
        r(deep[22]).exits.removeAll { it.toRoom == deep[23] }
        r(deep[23]).exits.removeAll { it.toRoom == deep[22] }

        lock(r(deep[25]), r(deep[26]), "door_e25", DataTerminal.Kind.BREAKERS, 113L)
        Compose.join(r(deep.last()), r("corepz3"))
        r("corepz3").exits.add(
            Exit(Box(31.4f, -11f, 32.6f, 0f), "superdb3", 1.6f, 0f, door("corepz3", "door_corepz"))
        )
        r("superdb3").exits.add(Exit(Box(-0.7f, -10f, 0.4f, 0f), "corepz3", 29.6f, 0f))
        // The door the master index exists to open.
        val master = Door(Box.of(22.2f, -8.0f, 1.4f, 8.0f), "door_master")
        master.locked = true
        master.manual = true
        master.lockMessage = "Sealed. Nothing I am carrying touches it."
        r("superdb3").props.add(master)
        r("superdb3").exits.add(Exit(Box(23.0f, -10f, 24.4f, 0f), quiet.first(), 1.6f, 0f, master))
        r(quiet.first()).exits.add(Exit(Box(-1.0f, -9.5f, 0.3f, 0f), "superdb3", 21.0f, 0f))
        Compose.join(r(quiet.last()), r("lift4"))

        // The jammed car. The rip opens the way into the riser.
        r("lift4").exits.add(Exit(Box(12.0f, -4.4f, 13.4f, 0f), "shaft3", 1.8f, 0f))
        r("shaft3").exits.add(
            Exit(Box(7.0f, -20.0f, 9.2f, -16.6f), "core3", 2.0f, 0f, door("shaft3", "door_shaft"))
        )
        r("core3").exits.add(
            Exit(Box(43.0f, -14f, 44.4f, 0f), sprint.first(), 1.8f, 0f, door("core3", "door_core"))
        )
        r(sprint.first()).exits.add(Exit(Box(-1.0f, -8.5f, 0.3f, 0f), "core3", 41.0f, 0f))

        // Five valves spread through the sprint, far enough apart to cost time.
        valve(r(sprint[2]), "sv_1")
        valve(r(sprint[6]), "sv_2")
        valve(r(sprint[10]), "sv_3")
        valve(r(sprint[14]), "sv_4")
        valve(r(sprint[18]), "sv_5")

        Compose.join(r(sprint.last()), r("pit3"))
    }

    /** The bulkhead chase route, and the one on Subfloor 3. */
    fun bulkRoute(): List<ChaseLeg> = listOf(
        // Starts behind the doorway the player comes through, not on it: a route
        // whose first metre is the player's spawn catches them on frame one.
        ChaseLeg("bulkA", -8f, 29f, 0f),
        ChaseLeg("bulkB", 1.0f, 29f, 0f),
        ChaseLeg("bulkC", 1.0f, 20f, 0f)
    )

    fun deepRoute(rooms: Map<String, Room>): List<ChaseLeg> {
        val legs = ArrayList<ChaseLeg>()
        var i = 26
        while (i < DEEP_ROOMS) {
            val r = rooms["e$i"] ?: break
            // Same rule as the bulkhead route: the first leg begins behind the
            // door so the pursuit has somewhere to come from.
            legs.add(ChaseLeg(r.id, if (i == 26) -8f else 1.0f, r.bounds.r - 1.0f, 0f))
            i++
        }
        legs.add(ChaseLeg("corepz3", 1.0f, 30f, 0f))
        return legs
    }

    /** Reconciles a freshly built level with a saved stage. */
    fun applyStage(g: GameSession, stage: Int) {
        val rooms = g.level.rooms
        fun door(roomId: String, name: String): Door? =
            rooms[roomId]?.props?.firstOrNull { it is Door && it.name == name } as? Door

        // Everything gated by a terminal stays shut; the terminals are stateless
        // across a reload on purpose, so a checkpoint always leaves a way forward
        // that the player can actually perform.
        g.reach.unlocked = stage >= Stage3.HAND
        g.dodgeUnlocked = true

        if (stage >= Stage3.HAND) {
            (rooms["archive3"]?.props?.firstOrNull { it is ArchiveCore } as? ArchiveCore)?.let {
                it.open = true
            }
        }
        if (stage >= Stage3.JUKE) door("archive3", "door_arch")?.forceOpen()
        // Shut for the fight, open again once it is over.
        door("archive3", "door_arch_back")?.let {
            if (stage in Stage3.ARCHIVE_PUZZLE..Stage3.HAND) it.forceClose() else it.forceOpen()
        }
        if (stage >= Stage3.RUN_B) {
            door("seal3", "door_seal")?.forceOpen()
            (rooms["seal3"]?.props?.firstOrNull { it is ReachAnchor && it.id == "shutter_vent" } as? ReachAnchor)
                ?.let { it.spent = true; it.strain = 1f }
        }
        if (stage >= Stage3.AFTER_BULK) door("bulkC", "shutter3")?.forceClose()
        if (stage >= Stage3.SUPER_DB) {
            door("corepz3", "door_corepz")?.forceOpen()
            (rooms["superdb3"]?.props?.firstOrNull { it is SuperDatabase } as? SuperDatabase)?.available = true
        }
        if (stage >= Stage3.QUIET) {
            (rooms["superdb3"]?.props?.firstOrNull { it is SuperDatabase } as? SuperDatabase)?.let {
                it.available = true
                it.taken = true
            }
            door("superdb3", "door_master")?.forceOpen()
        }
        if (stage >= Stage3.SHAFT) {
            (rooms["lift4"]?.props?.firstOrNull { it is ReachAnchor && it.id == "rip_lift" } as? ReachAnchor)
                ?.let { it.spent = true; it.strain = 1f }
        }
        if (stage >= Stage3.LASER_ROOM) door("shaft3", "door_shaft")?.forceOpen()
        if (stage >= Stage3.CORE_LIT) {
            for (p in rooms["core3"]?.props.orEmpty()) {
                if (p is LaserEmitter) { p.armed = true; p.firing = true }
                if (p is CoreWell) p.charge = 1f
            }
        }
        if (stage >= Stage3.FINAL_RUN) door("core3", "door_core")?.forceOpen()

        g.monster.kind = if (stage >= Stage3.BULKHEAD && stage < Stage3.DEEP_CHASE)
            Monster.Kind.SHADE else Monster.Kind.PLAYERR
    }

    fun spawnFor(stage: Int): Triple<String, Float, Float> = when {
        stage >= Stage3.PIT -> Triple("pit3", 3.0f, 0f)
        stage >= Stage3.FINAL_RUN -> Triple("f0", 3.0f, 0f)
        stage >= Stage3.BOSS -> Triple("core3", 3.0f, 0f)
        stage >= Stage3.LASER_ROOM -> Triple("core3", 3.0f, 0f)
        stage >= Stage3.STUCK_LIFT -> Triple("lift4", 3.0f, 0f)
        stage >= Stage3.SUPER_DB -> Triple("superdb3", 3.0f, 0f)
        stage >= Stage3.CORE_PUZZLE -> Triple("corepz3", 2.5f, 0f)
        // Past the ride, not on it: the car only answers at LIFT3, so a
        // checkpoint that put the player back in the lift room left them with a
        // dead button and no way out.
        stage >= Stage3.DEEP -> Triple("e0", 3.0f, 0f)
        stage >= Stage3.LIFT3 -> Triple("lift3", 3.0f, 0f)
        stage >= Stage3.AFTER_BULK -> Triple("d0", 3.0f, 0f)
        stage >= Stage3.DETOUR -> Triple("c0", 3.0f, 0f)
        stage >= Stage3.RUN_B -> Triple("b0", 3.0f, 0f)
        stage >= Stage3.VENT_RUN -> Triple("vent3", 2.0f, 0f)
        stage >= Stage3.HAND -> Triple("archive3", 3.0f, 0f)
        stage >= Stage3.ARCHIVE -> Triple("a0", 3.0f, 0f)
        else -> Triple("obs3", 4.0f, 0f)
    }
}
