package com.expstudio.facilitycore.game

import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Palette

/**
 * Builds Chapter 1. Rooms use local coordinates with the floor at y = 0 and the
 * ceiling at negative y, which keeps every literal in the layout readable.
 *
 * Two invariants the geometry is tuned against:
 *  - a jump clears 1.40 m of height and about 2.8 m of gap, so climbs step by
 *    at most 1.1 m and platforms are never further apart than 1.6 m;
 *  - the chase route is ~50 m of travel, which a sprinting player covers in
 *    about 13 s of the 15 s budget.
 *
 * The level is always built fresh and then reconciled to the saved stage by
 * [applyStage], so loading a checkpoint and reaching it by playing produce
 * identical world state.
 */
object Chapter1 {

    const val CHASE_SECONDS = 15f
    /** Where the monster notices the player in the archive. */
    const val ENCOUNTER_X = 20f
    /** Distance into the return vent at which the ceiling comes apart. */
    const val VENT_SWIPE_X = 12f

    fun build(): Level {
        val rooms = LinkedHashMap<String, Room>()
        rooms["entry"] = buildEntry()
        rooms["lobby"] = buildLobby()
        rooms["sealed"] = buildSealed()
        rooms["storage"] = buildStorage()
        rooms["hub"] = buildHub()
        rooms["power"] = buildPower()
        rooms["lift"] = buildLift()
        rooms["archive"] = buildArchive()
        rooms["maze1"] = buildMaze1()
        rooms["maze2"] = buildMaze2()
        rooms["endchase"] = buildEndChase()
        rooms["circuit"] = buildCircuit()
        rooms["vent"] = buildVent()
        linkRooms(rooms)
        return Level(rooms)
    }

    private fun room(id: String, title: String, w: Float, h: Float, needsPower: Boolean = false): Room =
        Room(id, title, Box(0f, -h, w, 0f), needsPower)

    // ---- rooms ----------------------------------------------------------

    private fun buildEntry(): Room = room("entry", "Intake Corridor", 26f, 7f).apply {
        shell()
        decorBox(0f, -0.4f, 26f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        // Collapsed entry doors behind the player: there is no going back.
        solid(1.4f, -7f, 0.6f, 7f)
        decorBox(1.0f, -6.0f, 1.4f, 4.2f, Palette.WALL_LIT, Decor.Kind.PANEL)
        props.add(Sign(Box.of(3.2f, -4.6f, 3.4f, 0.7f), "FACILITY CORE", Palette.ACCENT))
        props.add(Sign(Box.of(3.6f, -3.7f, 2.6f, 0.5f), "SUBFLOOR ACCESS", Palette.TEXT_DIM))
        solid(9f, -0.9f, 1.6f, 0.9f, Solid.Kind.CRATE)
        solid(10.8f, -1.5f, 1.1f, 1.5f, Solid.Kind.CRATE)
        decorBox(16f, -6.6f, 2.6f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(20f, -6.9f, 5.5f, 0.25f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(6f, -6.9f, 0.9f, 0.6f, Palette.WARN, Decor.Kind.LIGHT).lit = true
        decorBox(18f, -6.9f, 0.9f, 0.6f, Palette.WARN, Decor.Kind.LIGHT)
    }

    private fun buildLobby(): Room = room("lobby", "Intake Hall", 32f, 8.5f).apply {
        shell(openLeft = true)
        decorBox(0f, -0.4f, 32f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        props.add(BreakerPanel(Box.of(6f, -3.1f, 1.6f, 1.9f), "gate_lobby"))
        props.add(Sign(Box.of(5.2f, -4.0f, 3.2f, 0.55f), "INTAKE BREAKERS", Palette.WARN))

        // Optional scenery to climb; the way on is along the floor.
        solid(13f, -1.1f, 2.2f, 1.1f, Solid.Kind.CRATE)
        solid(16.2f, -2.1f, 1.8f, 0.3f, Solid.Kind.PLATFORM)
        solid(19.6f, -3.1f, 3.4f, 0.3f, Solid.Kind.PLATFORM)
        decorBox(2f, -8.4f, 10f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(9f, -8.4f, 0.9f, 0.6f, Palette.WARN, Decor.Kind.LIGHT).lit = true
        decorBox(22f, -8.4f, 0.9f, 0.6f, Palette.WARN, Decor.Kind.LIGHT)
        props.add(Sign(Box.of(26.4f, -4.2f, 3.0f, 0.55f), "JUNCTION ->", Palette.TEXT_DIM))

        val gate = Door(Box.of(30f, -3.2f, 0.7f, 3.2f), "gate_lobby")
        gate.locked = true
        gate.manual = false // driven by the breaker puzzle, never by hand
        props.add(gate)
    }

    private fun buildSealed(): Room = room("sealed", "Sealed Junction", 26f, 9f).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 26f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        // Climb to the sealed door's ledge: four steps of 1.1 m.
        solid(8f, -1.2f, 2.0f, 1.2f, Solid.Kind.CRATE)
        solid(11.5f, -2.3f, 2.4f, 0.3f, Solid.Kind.PLATFORM)
        solid(15.2f, -3.4f, 2.4f, 0.3f, Solid.Kind.PLATFORM)
        solid(19.0f, -4.5f, 7.5f, 0.3f, Solid.Kind.PLATFORM)

        props.add(Sign(Box.of(21.0f, -5.6f, 3.6f, 0.6f), "SUBFLOOR 0 ->", Palette.ACCENT))
        props.add(Sign(Box.of(21.4f, -1.6f, 3.4f, 0.55f), "STORAGE BAY ->", Palette.TEXT_DIM))

        val main = Door(Box.of(24.6f, -7.9f, 0.8f, 3.4f), "door_main")
        main.locked = true
        main.keyAccepted = true
        main.lockMessage = "Locked. I need to unlock this."
        props.add(main)

        decorBox(3f, -8.9f, 8f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(12f, -8.9f, 0.9f, 0.6f, Palette.WARN, Decor.Kind.LIGHT).lit = true
    }

    private fun buildStorage(): Room = room("storage", "Storage Bay", 22f, 7.5f).apply {
        shell(openLeft = true)
        decorBox(0f, -0.4f, 22f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        // Stacked flush and stepped down on both sides. A 1.9 m face reachable
        // only from one direction would strand the player in the bay: nothing
        // taller than a 1.4 m jump may ever sit across a walking route.
        solid(5f, -1.0f, 2.4f, 1.0f, Solid.Kind.CRATE)
        solid(7.4f, -1.9f, 1.6f, 1.9f, Solid.Kind.CRATE)
        solid(9.0f, -1.0f, 1.4f, 1.0f, Solid.Kind.CRATE)
        solid(12f, -1.05f, 5.2f, 0.2f, Solid.Kind.PLATFORM) // workbench top
        decorBox(12f, -0.85f, 5.2f, 0.85f, Palette.WALL, Decor.Kind.PANEL)
        decorBox(1.5f, -7.4f, 6f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(16f, -7.4f, 0.9f, 0.6f, Palette.WARN, Decor.Kind.LIGHT).lit = true

        props.add(KeyPackItem(Box.of(14.1f, -1.55f, 0.9f, 0.5f)))
        props.add(Sign(Box.of(11.8f, -2.6f, 3.0f, 0.5f), "TECH BENCH", Palette.TEXT_DIM))
    }

    private fun buildHub(): Room = room("hub", "Feeder Hub", 28f, 8f, needsPower = true).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 28f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        solid(9f, -1.2f, 2.6f, 1.2f, Solid.Kind.CRATE)
        solid(13.5f, -2.3f, 3.0f, 0.3f, Solid.Kind.PLATFORM)
        decorBox(2f, -7.9f, 12f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(6f, -7.9f, 0.9f, 0.6f, Palette.WARN, Decor.Kind.LIGHT)
        decorBox(20f, -7.9f, 0.9f, 0.6f, Palette.WARN, Decor.Kind.LIGHT)
        props.add(Sign(Box.of(3.0f, -3.2f, 3.6f, 0.55f), "SUBFLOOR 0 — DEAD", Palette.BAD))
        props.add(CableCoil(Box.of(18.3f, -0.7f, 0.7f, 0.7f), "cable_main"))
    }

    private fun buildPower(): Room = room("power", "Connection Vault", 26f, 10f, needsPower = true).apply {
        shell(openLeft = true)
        decorBox(0f, -0.4f, 26f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        solid(7f, -1.2f, 2.4f, 1.2f, Solid.Kind.CRATE)
        solid(11.5f, -2.3f, 3.4f, 0.3f, Solid.Kind.PLATFORM)
        decorBox(1.5f, -9.9f, 10f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(13f, -9.9f, 11f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)

        // Mounted high on purpose: the cable has to be thrown, not carried up.
        props.add(ConnectionStation(Box.of(15.4f, -4.6f, 2.2f, 1.2f), "st_main"))
        props.add(Sign(Box.of(14.4f, -5.6f, 4.0f, 0.6f), "FEEDER MAIN", Palette.WARN))

        val door = Door(Box.of(24.9f, -3.4f, 0.8f, 3.4f), "door_power")
        door.locked = true
        door.keyAccepted = true
        door.requiresPower = true
        door.lockMessage = "Locked. The pack should handle this."
        props.add(door)
    }

    private fun buildLift(): Room = room("lift", "Lift Landing", 22f, 9.5f, needsPower = true).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 22f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        props.add(Elevator(Box.of(2.2f, -4.1f, 2.6f, 4.1f)))
        props.add(Sign(Box.of(7.4f, -5.4f, 3.0f, 0.6f), "ARCHIVE ->", Palette.TEXT_DIM))
        solid(12f, -1.2f, 2.2f, 1.2f, Solid.Kind.CRATE)
        decorBox(9f, -9.4f, 12f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        // The duct the player later falls out of.
        decorBox(15.2f, -9.45f, 2.6f, 0.45f, Palette.WALL_LIT, Decor.Kind.GRATE)
    }

    private fun buildArchive(): Room = room("archive", "Archive Floor", 30f, 8.5f, needsPower = true).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 30f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        // Server rows: cover, silhouette, and something to vault.
        var x = 5f
        while (x < 25f) {
            solid(x, -1.2f, 1.4f, 1.2f, Solid.Kind.CRATE)
            decorBox(x + 0.2f, -1.05f, 1.0f, 0.85f, Palette.withAlpha(Palette.ACCENT, 0.10f), Decor.Kind.PANEL)
            x += 4.2f
        }
        decorBox(2f, -8.4f, 26f, 0.28f, Palette.TRIM, Decor.Kind.PIPE)
        props.add(Sign(Box.of(2.4f, -4.4f, 3.0f, 0.55f), "ARCHIVE", Palette.ACCENT))
    }

    private fun buildMaze1(): Room = room("maze1", "Service Run A", 14f, 8.5f, needsPower = true).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 14f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        solid(4.4f, -1.1f, 2.0f, 1.1f, Solid.Kind.CRATE)
        solid(8.6f, -1.1f, 1.6f, 1.1f, Solid.Kind.CRATE)
        solid(10.9f, -2.1f, 1.8f, 0.3f, Solid.Kind.PLATFORM)
        decorBox(1f, -8.4f, 12f, 0.28f, Palette.TRIM, Decor.Kind.PIPE)
    }

    private fun buildMaze2(): Room = room("maze2", "Service Run B", 14f, 8.5f, needsPower = true).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 14f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        // Collapsed bulkhead leaving a 1.05 m slot: sneak or die.
        solid(6.2f, -8.5f, 3.0f, 7.45f)
        decorBox(6.2f, -1.27f, 3.0f, 0.22f, Palette.BAD, Decor.Kind.STRIPE)
        solid(10.6f, -1.1f, 1.6f, 1.1f, Solid.Kind.CRATE)
        solid(12.4f, -2.1f, 1.6f, 0.3f, Solid.Kind.PLATFORM)
        decorBox(1f, -8.4f, 4.5f, 0.28f, Palette.TRIM, Decor.Kind.PIPE)
    }

    private fun buildEndChase(): Room = room("endchase", "Pump Room", 20f, 8.5f, needsPower = true).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 20f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        props.add(CableCoil(Box.of(11.0f, -0.7f, 0.7f, 0.7f), "cable_chase"))
        props.add(ConnectionStation(Box.of(13.2f, -3.9f, 2.0f, 1.1f), "st_chase"))
        props.add(Sign(Box.of(12.7f, -4.9f, 3.0f, 0.55f), "BULKHEAD FEED", Palette.WARN))

        // Shutter that slams shut behind the player once the feed is live.
        val shutter = Door(Box.of(8.4f, -8.5f, 0.9f, 8.5f), "shutter")
        shutter.manual = false
        shutter.forceOpen()
        props.add(shutter)

        val exit = Door(Box.of(18.8f, -3.4f, 0.8f, 3.4f), "door_circuit")
        exit.locked = true
        exit.keyAccepted = true
        exit.lockMessage = "Locked. The pack should handle this."
        props.add(exit)
    }

    private fun buildCircuit(): Room = room("circuit", "Data Spine", 30f, 9f, needsPower = true).apply {
        shell(openLeft = true)
        decorBox(0f, -0.4f, 30f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        solid(9f, -1.2f, 2.2f, 1.2f, Solid.Kind.CRATE)
        solid(19f, -1.2f, 2.2f, 1.2f, Solid.Kind.CRATE)

        props.add(CableCoil(Box.of(4.6f, -0.7f, 0.7f, 0.7f), "cable_c1"))
        props.add(CableCoil(Box.of(24.6f, -0.7f, 0.7f, 0.7f), "cable_c2"))
        props.add(ConnectionStation(Box.of(6.8f, -4.9f, 2.0f, 1.1f), "st_c1", yieldsShard = true))
        props.add(ConnectionStation(Box.of(21.4f, -4.9f, 2.0f, 1.1f), "st_c2", yieldsShard = true))
        props.add(ArchivePanel(Box.of(13.6f, -3.6f, 2.8f, 2.4f)))
        props.add(Sign(Box.of(13.4f, -4.5f, 3.2f, 0.6f), "ARCHIVE WRITE", Palette.ACCENT))

        // Duct mouth: the 1.4 m slot can only be entered crouched.
        solid(28f, -9f, 2f, 7.6f)
        decorBox(28f, -1.46f, 2f, 0.16f, Palette.WALL_LIT, Decor.Kind.GRATE)
    }

    private fun buildVent(): Room = room("vent", "Return Vent", 20f, 3.2f, needsPower = true).apply {
        solid(-1f, 0f, 22f, 1f)          // floor
        solid(-1f, -3.2f, 22f, 2.1f)     // ceiling slab leaves a 1.1 m slot
        solid(-1f, -3.2f, 1f, 3.2f)      // sealed left shoulder
        solid(20f, -3.2f, 1f, 3.2f)      // sealed right shoulder
        decorBox(0f, -0.07f, 20f, 0.07f, Palette.withAlpha(Palette.TRIM, 0.5f), Decor.Kind.GRATE)
    }

    // ---- links ----------------------------------------------------------

    private fun door(rooms: Map<String, Room>, roomId: String, name: String): Door? =
        rooms[roomId]?.props?.firstOrNull { it is Door && it.name == name } as? Door

    private fun linkRooms(rooms: LinkedHashMap<String, Room>) {
        val entry = rooms.getValue("entry")
        val lobby = rooms.getValue("lobby")
        val sealed = rooms.getValue("sealed")
        val storage = rooms.getValue("storage")
        val hub = rooms.getValue("hub")
        val power = rooms.getValue("power")
        val lift = rooms.getValue("lift")
        val archive = rooms.getValue("archive")
        val maze1 = rooms.getValue("maze1")
        val maze2 = rooms.getValue("maze2")
        val endchase = rooms.getValue("endchase")
        val circuit = rooms.getValue("circuit")
        val vent = rooms.getValue("vent")

        entry.exits.add(Exit(Box(25.3f, -7f, 26.6f, 0f), "lobby", 1.4f, 0f))
        lobby.exits.add(Exit(Box(-0.6f, -8.5f, 0.5f, 0f), "entry", 24.8f, 0f))
        lobby.exits.add(
            Exit(Box(30.9f, -3.2f, 32.3f, 0f), "sealed", 1.4f, 0f, door(rooms, "lobby", "gate_lobby"))
        )

        sealed.exits.add(Exit(Box(-0.6f, -9f, 0.5f, 0f), "lobby", 30.3f, 0f))
        // Floor level continues to the storage bay; the sealed door is up on the ledge.
        sealed.exits.add(Exit(Box(25.9f, -1.9f, 26.9f, 0f), "storage", 1.6f, 0f))
        sealed.exits.add(
            Exit(Box(25.9f, -7.9f, 26.9f, -4.5f), "hub", 1.4f, 0f, door(rooms, "sealed", "door_main"))
        )
        storage.exits.add(Exit(Box(-0.6f, -7.5f, 0.5f, 0f), "sealed", 24.2f, 0f))

        hub.exits.add(Exit(Box(-0.6f, -8f, 0.5f, 0f), "sealed", 24.2f, 0f))
        hub.exits.add(Exit(Box(27.7f, -8f, 28.7f, 0f), "power", 1.4f, 0f))
        power.exits.add(Exit(Box(-0.6f, -10f, 0.5f, 0f), "hub", 26.8f, 0f))
        power.exits.add(Exit(Box(25.8f, -3.4f, 26.6f, 0f), "lift", 8.5f, 0f, door(rooms, "power", "door_power")))

        lift.exits.add(Exit(Box(-0.6f, -9.5f, 0.5f, 0f), "power", 24.0f, 0f))
        lift.exits.add(Exit(Box(21.7f, -9.5f, 22.7f, 0f), "archive", 1.4f, 0f))
        archive.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "lift", 20.6f, 0f))
        archive.exits.add(Exit(Box(29.7f, -8.5f, 30.7f, 0f), "maze1", 1.0f, 0f))
        maze1.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "archive", 28.8f, 0f))
        maze1.exits.add(Exit(Box(13.7f, -8.5f, 14.7f, 0f), "maze2", 1.0f, 0f))
        maze2.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "maze1", 12.8f, 0f))
        maze2.exits.add(Exit(Box(13.7f, -8.5f, 14.7f, 0f), "endchase", 1.0f, 0f))
        endchase.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "maze2", 12.8f, 0f))
        endchase.exits.add(
            Exit(Box(19.7f, -3.4f, 20.7f, 0f), "circuit", 1.4f, 0f, door(rooms, "endchase", "door_circuit"))
        )
        circuit.exits.add(Exit(Box(-0.7f, -9f, 0.4f, 0f), "endchase", 17.6f, 0f))
        // Only reachable crouched: standing bodies are stopped by the duct lip at x = 28.
        circuit.exits.add(Exit(Box(28.4f, -1.4f, 29.8f, 0f), "vent", 2.2f, 0f))
        vent.exits.add(Exit(Box(0.4f, -1.1f, 1.5f, 0f), "circuit", 26.6f, 0f))
    }

    /**
     * Reconciles a freshly built level with a saved stage so resuming a
     * checkpoint yields exactly the world the player left.
     */
    fun applyStage(g: GameSession, stage: Int) {
        val rooms = g.level.rooms

        fun d(room: String, name: String) = door(rooms, room, name)
        fun station(room: String, id: String) =
            rooms[room]?.props?.firstOrNull { it is ConnectionStation && it.id == id } as? ConnectionStation

        if (stage > Stage.LOBBY_PUZZLE) {
            (rooms["lobby"]?.props?.firstOrNull { it is BreakerPanel } as? BreakerPanel)?.solved = true
            d("lobby", "gate_lobby")?.forceOpen()
        }
        if (stage > Stage.FIND_KEYPACK) {
            (rooms["storage"]?.props?.firstOrNull { it is KeyPackItem } as? KeyPackItem)?.taken = true
            g.hasKeyPack = true
        }
        if (stage > Stage.REPAIR_KEYPACK) g.keyPackRepaired = true
        if (stage > Stage.UNLOCK_FIRST_DOOR) d("sealed", "door_main")?.forceOpen()
        if (stage > Stage.POWER_SUBFLOOR) {
            g.subfloorPowered = true
            station("power", "st_main")?.connected = true
            rooms["hub"]?.props?.filterIsInstance<CableCoil>()?.forEach { it.consumed = true }
        }
        if (stage > Stage.REACH_ELEVATOR) d("power", "door_power")?.forceOpen()
        if (stage > Stage.CHASE) {
            station("endchase", "st_chase")?.connected = true
            rooms["endchase"]?.props?.filterIsInstance<CableCoil>()?.forEach { it.consumed = true }
            d("endchase", "shutter")?.forceClose()
        }
        if (stage > Stage.CHASE_SURVIVED) d("endchase", "door_circuit")?.forceOpen()
        if (stage > Stage.CIRCUIT_ROOM) {
            station("circuit", "st_c1")?.let { it.connected = true; it.shardTaken = true }
            station("circuit", "st_c2")?.let { it.connected = true; it.shardTaken = true }
            rooms["circuit"]?.props?.filterIsInstance<CableCoil>()?.forEach { it.consumed = true }
            g.shards = 2
            g.circuitAssembled = true
        }
        val panel = rooms["circuit"]?.props?.firstOrNull { it is ArchivePanel } as? ArchivePanel
        if (stage > Stage.CIRCUIT_ASSEMBLED) panel?.done = true
        // Stopping exactly at PANEL_UPLOADED means the write finished but the
        // pack is still sitting in the slot, so it must stay collectable.
        if (stage > Stage.PANEL_UPLOADED) {
            panel?.packRetrieved = true
            g.keyPackHasElevator = true
        }

        // The monster's route never changes; only its state does. It starts six
        // metres behind the player's encounter position and arrives at the
        // bulkhead feed exactly as the 15 second budget runs out.
        g.monster.setRoute(
            listOf(
                ChaseLeg("archive", ENCOUNTER_X - 6f, 29.5f, 0f),
                ChaseLeg("maze1", 0.5f, 13.5f, 0f),
                ChaseLeg("maze2", 0.5f, 13.5f, 0f),
                ChaseLeg("endchase", 0.5f, 12.5f, 0f)
            )
        )
    }

    /** Where the player stands when a stage is loaded from a save. */
    fun spawnFor(stage: Int): Triple<String, Float, Float> = when (Stage.checkpointFor(stage)) {
        Stage.LOBBY_PUZZLE -> Triple("lobby", 2.5f, 0f)
        Stage.UNLOCK_FIRST_DOOR -> Triple("sealed", 3.0f, 0f)
        Stage.POWER_SUBFLOOR -> Triple("power", 3.0f, 0f)
        Stage.ELEVATOR_DENIED -> Triple("lift", 8.5f, 0f)
        Stage.CHASE_SURVIVED -> Triple("endchase", 12.0f, 0f)
        Stage.PANEL_UPLOADED -> Triple("circuit", 14.0f, 0f)
        Stage.ELEVATOR_READY -> Triple("lift", 8.0f, 0f)
        else -> Triple("entry", 3.4f, 0f)
    }
}
