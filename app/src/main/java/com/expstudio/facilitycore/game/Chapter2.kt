package com.expstudio.facilitycore.game

import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Palette

/**
 * Chapter 2 — Subfloor 1. Progression values are persisted, so entries may only
 * ever be appended.
 */
object Stage2 {
    const val OPENING = 0            // the roof comes in and it follows you down
    const val LIFT_FIGHT = 1         // dodge it while the car is repaired
    const val LIFT_FIXED = 2         // all three jobs done; the car falls again
    const val ARRIVAL = 3            // the doors open on Subfloor 1
    const val FIND_BATTERY = 4
    const val BATTERY_CARRIED = 5
    const val GATE_OPEN = 6
    const val MEET_PLAYERR = 7       // it takes the door off its hinges
    const val PLAYERR_CHASE = 8
    const val HOIST_ESCAPE = 9       // the grabbers take you out of its reach
    const val DROPPED = 10
    const val FEED_ROOM = 11         // cable to the station
    const val CUBE_TAKEN = 12        // the station gives up its cube
    const val SPINE_OPEN = 13
    const val SHADE_RETURN = 14      // the thing from Chapter 1, worse
    const val SHADE_CHASE = 15
    const val DEAD_END = 16          // no doors; the duct is the only way
    const val VENT_RUN = 17
    const val SMELTER_ARMED = 18     // the line is live and the smelter fires
    const val SMELTER_DOOR = 19      // get through it before the heat does
    const val ENDING = 20
    const val COMPLETE = 21

    fun checkpointFor(stage: Int): Int = when {
        stage >= SMELTER_DOOR -> SMELTER_DOOR
        stage >= DEAD_END -> DEAD_END
        stage >= SHADE_RETURN -> SHADE_RETURN
        stage >= FEED_ROOM -> FEED_ROOM
        stage >= DROPPED -> DROPPED
        stage >= MEET_PLAYERR -> MEET_PLAYERR
        stage >= ARRIVAL -> ARRIVAL
        stage >= LIFT_FIGHT -> LIFT_FIGHT
        else -> OPENING
    }

    fun objectiveFor(stage: Int): String = when (stage) {
        OPENING -> "Hold on"
        LIFT_FIGHT -> "Dodge it and get the car working — 3 jobs"
        LIFT_FIXED -> "The car is falling again. Ride it down."
        ARRIVAL -> "Subfloor 1. Find Ren."
        FIND_BATTERY -> "The doors are dead — find a battery cube"
        BATTERY_CARRIED -> "Seat the cube in the gate socket"
        GATE_OPEN -> "Through the gate"
        MEET_PLAYERR -> "Something else lives down here"
        PLAYERR_CHASE -> "RUN — find a way out of its reach"
        HOIST_ESCAPE -> "Use the pack on the hoist switch"
        DROPPED -> "It lost you. Keep moving."
        FEED_ROOM -> "Throw the feeder up to the station"
        CUBE_TAKEN -> "Take the cube from the open station"
        SPINE_OPEN -> "Seat the cube and open the spine door"
        SHADE_RETURN -> "It found you again"
        SHADE_CHASE -> "RUN"
        DEAD_END -> "No doors. The duct is the only way."
        VENT_RUN -> "Feed the smelter line — fast"
        SMELTER_ARMED -> "Get through the hatch before it does"
        SMELTER_DOOR -> "Open the hatch with the pack"
        ENDING -> "..."
        else -> "Chapter 2 complete"
    }
}

/**
 * Builds Chapter 2. Same geometry rules as Chapter 1: nothing taller than a
 * 1.4 m jump across a walking route, climbs step by at most 1.1 m, and every
 * gap too low to walk through is signposted as a crawl.
 */
object Chapter2 {

    const val CHASE_SECONDS = 30f
    const val SHADE_CHASE_SECONDS = 26f
    /** Where the Playerr takes the door off its hinges. */
    const val HALL_TRIGGER_X = 17f
    /** Where the shade catches up with you again. */
    const val SHADE_TRIGGER_X = 16f
    /** Seconds to feed the smelter line once the duct run starts. */
    const val VENT_SECONDS = 22f
    /** Extra seconds granted once the pour is live, to reach the hatch. */
    const val HATCH_SECONDS = 14f

    fun build(): Level {
        val rooms = LinkedHashMap<String, Room>()
        rooms["car"] = buildCar()
        rooms["landing1"] = buildLanding()
        rooms["battery"] = buildBattery()
        rooms["gate"] = buildGate()
        rooms["hall"] = buildHall()
        rooms["maze_a"] = buildMazeA()
        rooms["maze_b"] = buildMazeB()
        rooms["hoist"] = buildHoist()
        rooms["feed"] = buildFeed()
        rooms["spine2"] = buildSpine()
        rooms["shade"] = buildShade()
        rooms["runA"] = buildRunA()
        rooms["runB"] = buildRunB()
        rooms["deadend"] = buildDeadEnd()
        rooms["vent2"] = buildVent()
        rooms["smelter"] = buildSmelter()
        link(rooms)
        return Level(rooms)
    }

    private fun room(id: String, title: String, w: Float, h: Float, needsPower: Boolean = false): Room =
        Room(id, title, Box(0f, -h, w, 0f), needsPower)

    // ---- rooms -----------------------------------------------------------

    /** The lift car, fought in rather than ridden. Sealed on every side. */
    private fun buildCar(): Room = room("car", "Lift Car", 15f, 7f).apply {
        shell()
        decorBox(0f, -0.4f, 15f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(4f, -6.9f, 7f, 0.5f, Palette.ACCENT, Decor.Kind.LIGHT).lit = true
        decorBox(0f, -3.3f, 15f, 0.22f, Palette.TRIM, Decor.Kind.PIPE)

        props.add(LiftTask(Box.of(2.2f, -3.3f, 1.7f, 1.5f), "task_loom", LiftTask.Kind.LOOM, "LOOM"))
        props.add(LiftTask(Box.of(6.7f, -3.3f, 1.7f, 1.5f), "task_brk", LiftTask.Kind.BREAKERS, "BREAKERS"))
        props.add(LiftTask(Box.of(11.2f, -3.3f, 1.7f, 1.5f), "task_valve", LiftTask.Kind.VALVE, "BRAKE VALVE"))

        // Something to break the line of the floor and give the roll somewhere to go.
        solid(5.2f, -1.0f, 1.6f, 1.0f, Solid.Kind.CRATE)
        solid(8.6f, -1.0f, 1.4f, 1.0f, Solid.Kind.CRATE)

        // The car doors. Sealed until the brakes are back.
        val doors = Door(Box.of(13.9f, -3.6f, 0.8f, 3.6f), "door_car")
        doors.locked = true
        doors.manual = false
        props.add(doors)
    }

    private fun buildLanding(): Room = room("landing1", "Subfloor 1 Landing", 26f, 9f).apply {
        shell(openRight = true)
        decorBox(0f, -0.4f, 26f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(3f, -8.9f, 12f, 0.28f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(6f, -8.9f, 0.9f, 0.6f, Palette.WARN, Decor.Kind.LIGHT).lit = true
        decorBox(18f, -8.9f, 0.9f, 0.6f, Palette.WARN, Decor.Kind.LIGHT)
        props.add(Sign(Box.of(2.0f, -4.6f, 3.6f, 0.7f), "SUBFLOOR 1", Palette.ACCENT))
        props.add(Sign(Box.of(19.5f, -3.0f, 3.4f, 0.55f), "CELLS ->", Palette.TEXT_DIM))
        solid(11f, -1.1f, 2.2f, 1.1f, Solid.Kind.CRATE)
        solid(14.6f, -2.2f, 2.6f, 0.3f, Solid.Kind.PLATFORM)
    }

    private fun buildBattery(): Room = room("battery", "Cell Bay", 24f, 8.5f).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 24f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(2f, -8.4f, 18f, 0.26f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(9f, -8.4f, 0.9f, 0.6f, Palette.ACCENT, Decor.Kind.LIGHT).lit = true
        // Racking, with the live cell on the second shelf.
        solid(6f, -1.1f, 2.4f, 1.1f, Solid.Kind.CRATE)
        solid(8.4f, -2.0f, 1.6f, 2.0f, Solid.Kind.CRATE)
        solid(10.0f, -1.1f, 1.6f, 1.1f, Solid.Kind.CRATE)
        solid(14f, -1.2f, 5.0f, 0.25f, Solid.Kind.PLATFORM)
        props.add(PowerCube(Box.of(15.6f, -1.75f, 0.55f, 0.55f), "cube_a"))
        props.add(Sign(Box.of(13.6f, -2.6f, 3.2f, 0.55f), "CELL RACK", Palette.TEXT_DIM))
    }

    private fun buildGate(): Room = room("gate", "Inner Gate", 20f, 8.5f).apply {
        shell(openLeft = true)
        decorBox(0f, -0.4f, 20f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        props.add(CubeSocket(Box.of(15.2f, -2.4f, 0.9f, 0.9f), "sock_gate", "door_gate"))
        props.add(Sign(Box.of(14.0f, -3.4f, 3.4f, 0.55f), "GATE POWER", Palette.WARN))
        val door = Door(Box.of(18.8f, -3.4f, 0.8f, 3.4f), "door_gate")
        door.locked = true
        door.manual = false
        props.add(door)
        solid(8f, -1.1f, 2.0f, 1.1f, Solid.Kind.CRATE)
    }

    private fun buildHall(): Room = room("hall", "Holding Hall", 28f, 9f).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 28f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(2f, -8.9f, 22f, 0.26f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(7f, -8.9f, 0.9f, 0.6f, Palette.WARN, Decor.Kind.LIGHT).lit = true
        decorBox(20f, -8.9f, 0.9f, 0.6f, Palette.BAD, Decor.Kind.LIGHT)
        // Cell doors down the hall.
        var x = 4f
        while (x < 24f) {
            decorBox(x, -3.4f, 1.5f, 3.0f, Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.3f), Decor.Kind.PANEL)
            x += 4.5f
        }
        solid(12f, -1.1f, 2.0f, 1.1f, Solid.Kind.CRATE)
        props.add(BreakableDoor(Box.of(25.4f, -4.0f, 1.0f, 4.0f), "door_hall"))
        props.add(Sign(Box.of(2.2f, -4.8f, 3.0f, 0.55f), "HOLDING", Palette.TEXT_DIM))
    }

    private fun buildMazeA(): Room = room("maze_a", "Service Loop A", 16f, 8.5f).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 16f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        solid(4.2f, -1.1f, 1.8f, 1.1f, Solid.Kind.CRATE)
        solid(7.6f, -2.1f, 2.0f, 0.3f, Solid.Kind.PLATFORM)
        solid(11.2f, -1.1f, 1.8f, 1.1f, Solid.Kind.CRATE)
        decorBox(1f, -8.4f, 14f, 0.26f, Palette.TRIM, Decor.Kind.PIPE)
    }

    private fun buildMazeB(): Room = room("maze_b", "Service Loop B", 16f, 8.5f).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 16f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        // Collapsed rack: crawl or lose the lead.
        solid(6.4f, -8.5f, 3.0f, 7.45f)
        decorBox(6.4f, -1.32f, 3.0f, 0.27f, Palette.WARN, Decor.Kind.STRIPE)
        decorBox(6.4f, -1.05f, 3.0f, 0.10f, Palette.WARN, Decor.Kind.GRATE)
        props.add(Sign(Box.of(6.2f, -2.5f, 3.4f, 0.6f), "CRAWL SPACE", Palette.WARN))
        solid(11.4f, -1.1f, 1.7f, 1.1f, Solid.Kind.CRATE)
    }

    private fun buildHoist(): Room = room("hoist", "Hoist Bay", 22f, 9f).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 22f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(9f, -8.9f, 8f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        props.add(Grabbers(Box.of(12.4f, -8.6f, 2.4f, 5.6f)))
        props.add(KeySwitch(Box.of(16.4f, -2.6f, 1.0f, 1.0f), "sw_hoist", "HOIST"))
        props.add(Sign(Box.of(15.2f, -3.6f, 3.4f, 0.55f), "HOIST CONTROL", Palette.ACCENT))
        solid(5f, -1.1f, 1.8f, 1.1f, Solid.Kind.CRATE)

        // Sealed until the hoist has done its job: without this the whole
        // escape can be skipped by simply running through the room.
        val onward = Door(Box.of(20.6f, -3.6f, 0.8f, 3.6f), "door_hoist")
        onward.locked = true
        onward.manual = false
        props.add(onward)
    }

    private fun buildFeed(): Room = room("feed", "Feeder Gallery", 26f, 9f).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 26f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(2f, -8.9f, 20f, 0.28f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(13f, -8.9f, 0.9f, 0.6f, Palette.ACCENT, Decor.Kind.LIGHT).lit = true
        props.add(CableCoil(Box.of(5.4f, -0.7f, 0.7f, 0.7f), "cable_f"))
        props.add(ConnectionStation(Box.of(9.0f, -4.6f, 2.2f, 1.2f), "st_feed", yieldsShard = true, shardLabel = "TAKE CELL"))
        // Authored rather than spawned on the fly: a cell that only existed at
        // runtime vanished from any save taken after it was collected, leaving
        // the spine socket with nothing to fill it.
        props.add(PowerCube(Box.of(9.6f, -1.5f, 0.55f, 0.55f), "cube_b").apply { consumed = true })
        props.add(Sign(Box.of(8.2f, -5.6f, 3.8f, 0.6f), "CELL CHARGER", Palette.WARN))
        solid(14f, -1.1f, 2.0f, 1.1f, Solid.Kind.CRATE)
        solid(18f, -2.2f, 2.6f, 0.3f, Solid.Kind.PLATFORM)
    }

    private fun buildSpine(): Room = room("spine2", "Lower Spine", 20f, 8.5f).apply {
        shell(openLeft = true)
        decorBox(0f, -0.4f, 20f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        props.add(CubeSocket(Box.of(15.0f, -2.4f, 0.9f, 0.9f), "sock_spine", "door_spine"))
        val door = Door(Box.of(18.8f, -3.4f, 0.8f, 3.4f), "door_spine")
        door.locked = true
        door.manual = false
        props.add(door)
        solid(7f, -1.1f, 2.0f, 1.1f, Solid.Kind.CRATE)
        props.add(Sign(Box.of(13.8f, -3.4f, 3.4f, 0.55f), "SPINE LOCK", Palette.ACCENT))
    }

    private fun buildShade(): Room = room("shade", "Coolant Walk", 26f, 8.5f).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 26f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(1f, -8.4f, 24f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        var x = 4f
        while (x < 22f) {
            solid(x, -1.1f, 1.4f, 1.1f, Solid.Kind.CRATE)
            x += 4.6f
        }
    }

    private fun buildRunA(): Room = room("runA", "Coolant Run A", 16f, 8.5f).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 16f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        solid(4.4f, -1.1f, 1.8f, 1.1f, Solid.Kind.CRATE)
        solid(8.2f, -2.2f, 2.2f, 0.3f, Solid.Kind.PLATFORM)
        solid(12.0f, -1.1f, 1.6f, 1.1f, Solid.Kind.CRATE)
    }

    private fun buildRunB(): Room = room("runB", "Coolant Run B", 16f, 8.5f).apply {
        shell(openLeft = true, openRight = true)
        decorBox(0f, -0.4f, 16f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        solid(5.8f, -8.5f, 2.6f, 7.45f)
        decorBox(5.8f, -1.32f, 2.6f, 0.27f, Palette.WARN, Decor.Kind.STRIPE)
        decorBox(5.8f, -1.05f, 2.6f, 0.10f, Palette.WARN, Decor.Kind.GRATE)
        props.add(Sign(Box.of(5.6f, -2.5f, 3.2f, 0.6f), "CRAWL SPACE", Palette.WARN))
        solid(10.6f, -1.1f, 1.7f, 1.1f, Solid.Kind.CRATE)
    }

    /** A room with nothing on the far side but a duct mouth. */
    private fun buildDeadEnd(): Room = room("deadend", "Sealed Cell", 18f, 8.5f).apply {
        shell(openLeft = true)
        decorBox(0f, -0.4f, 18f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        props.add(Sign(Box.of(6.0f, -5.0f, 5.0f, 0.7f), "NO EXIT", Palette.BAD))
        solid(9f, -1.1f, 2.0f, 1.1f, Solid.Kind.CRATE)
        // Duct mouth: a 1.4 m slot that can only be entered crouched.
        solid(16f, -8.5f, 2f, 7.1f)
        decorBox(16f, -1.46f, 2f, 0.16f, Palette.WALL_LIT, Decor.Kind.GRATE)
        decorBox(16f, -1.72f, 2f, 0.26f, Palette.WARN, Decor.Kind.STRIPE)
        props.add(Sign(Box.of(13.4f, -2.7f, 3.4f, 0.6f), "CRAWL SPACE", Palette.WARN))
    }

    /**
     * The duct: a long crawl that opens out onto the smelter gantry, where the
     * feeder line, the hatch and the drop all live.
     */
    private fun buildVent(): Room = room("vent2", "Smelter Duct", 30f, 6.5f).apply {
        solid(-1f, 0f, 20f, 1f)              // duct floor
        solid(-1f, -6.5f, 20f, 5.3f)         // duct ceiling: a 1.2 m slot
        solid(-1f, -6.5f, 1f, 6.5f)          // sealed left shoulder
        decorBox(0f, -0.07f, 19f, 0.07f, Palette.TRIM, Decor.Kind.GRATE)

        // The gantry: full height, where the line has to be fed.
        solid(19f, 0f, 12f, 1f)
        solid(19f, -6.5f, 12f, 0.6f)
        solid(30f, -6.5f, 1f, 6.5f)
        decorBox(19f, -0.4f, 11f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)

        props.add(CableCoil(Box.of(20.4f, -0.7f, 0.7f, 0.7f), "cable_v"))
        props.add(ConnectionStation(Box.of(23.4f, -4.2f, 2.2f, 1.2f), "st_vent"))
        props.add(Sign(Box.of(22.6f, -5.2f, 3.8f, 0.6f), "SMELTER FEED", Palette.WARN))

        // The pour below, seen through the gantry grating.
        props.add(Smelter(Box.of(19.5f, 0.55f, 10f, 2.2f)))

        val hatch = Door(Box.of(28.6f, -3.4f, 0.8f, 3.4f), "door_smelt")
        hatch.locked = true
        hatch.keyAccepted = true
        hatch.lockMessage = "Locked. Pack. Now."
        props.add(hatch)
    }

    private fun buildSmelter(): Room = room("smelter", "Pour Control", 20f, 8f).apply {
        shell(openLeft = true)
        decorBox(0f, -0.4f, 20f, 0.4f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        decorBox(3f, -7.9f, 14f, 0.28f, Palette.TRIM, Decor.Kind.PIPE)
        decorBox(9f, -7.9f, 0.9f, 0.6f, Palette.ACCENT, Decor.Kind.LIGHT).lit = true
        // The observation window the fall is watched through.
        decorBox(4f, -5.4f, 11f, 3.0f, Palette.withAlpha(Smelter.LAVA_GLOW, 0.16f), Decor.Kind.PANEL)
        props.add(Sign(Box.of(6.0f, -6.2f, 6.6f, 0.7f), "POUR CONTROL", Palette.ACCENT))
        solid(15f, -1.1f, 2.0f, 1.1f, Solid.Kind.CRATE)
    }

    // ---- links -----------------------------------------------------------

    private fun door(rooms: Map<String, Room>, roomId: String, name: String): Door? =
        rooms[roomId]?.props?.firstOrNull { it is Door && it.name == name } as? Door

    private fun link(rooms: LinkedHashMap<String, Room>) {
        val car = rooms.getValue("car")
        val landing = rooms.getValue("landing1")
        val battery = rooms.getValue("battery")
        val gate = rooms.getValue("gate")
        val hall = rooms.getValue("hall")
        val mazeA = rooms.getValue("maze_a")
        val mazeB = rooms.getValue("maze_b")
        val hoist = rooms.getValue("hoist")
        val feed = rooms.getValue("feed")
        val spine = rooms.getValue("spine2")
        val shade = rooms.getValue("shade")
        val runA = rooms.getValue("runA")
        val runB = rooms.getValue("runB")
        val deadend = rooms.getValue("deadend")
        val vent = rooms.getValue("vent2")

        // The car only opens once it has been repaired; the script unseals it.
        car.exits.add(Exit(Box(14.8f, -7f, 15.6f, 0f), "landing1", 2.0f, 0f, door(rooms, "car", "door_car")))

        landing.exits.add(Exit(Box(25.7f, -9f, 26.7f, 0f), "battery", 1.2f, 0f))
        battery.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "landing1", 24.6f, 0f))
        battery.exits.add(Exit(Box(23.7f, -8.5f, 24.7f, 0f), "gate", 1.2f, 0f))
        gate.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "battery", 22.6f, 0f))
        gate.exits.add(Exit(Box(19.7f, -3.4f, 20.7f, 0f), "hall", 1.4f, 0f, door(rooms, "gate", "door_gate")))

        hall.exits.add(Exit(Box(-0.7f, -9f, 0.4f, 0f), "gate", 18.0f, 0f))
        hall.exits.add(Exit(Box(27.7f, -9f, 28.7f, 0f), "maze_a", 1.0f, 0f))
        mazeA.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "hall", 26.6f, 0f))
        mazeA.exits.add(Exit(Box(15.7f, -8.5f, 16.7f, 0f), "maze_b", 1.0f, 0f))
        mazeB.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "maze_a", 14.8f, 0f))
        mazeB.exits.add(Exit(Box(15.7f, -8.5f, 16.7f, 0f), "hoist", 1.0f, 0f))
        hoist.exits.add(Exit(Box(-0.7f, -9f, 0.4f, 0f), "maze_b", 14.8f, 0f))
        hoist.exits.add(Exit(Box(21.5f, -9f, 22.7f, 0f), "feed", 1.2f, 0f, door(rooms, "hoist", "door_hoist")))

        feed.exits.add(Exit(Box(-0.7f, -9f, 0.4f, 0f), "hoist", 20.6f, 0f))
        feed.exits.add(Exit(Box(25.7f, -9f, 26.7f, 0f), "spine2", 1.2f, 0f))
        spine.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "feed", 24.6f, 0f))
        spine.exits.add(Exit(Box(19.7f, -3.4f, 20.7f, 0f), "shade", 1.2f, 0f, door(rooms, "spine2", "door_spine")))

        shade.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "spine2", 18.0f, 0f))
        shade.exits.add(Exit(Box(25.7f, -8.5f, 26.7f, 0f), "runA", 1.0f, 0f))
        runA.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "shade", 24.6f, 0f))
        runA.exits.add(Exit(Box(15.7f, -8.5f, 16.7f, 0f), "runB", 1.0f, 0f))
        runB.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "runA", 14.8f, 0f))
        runB.exits.add(Exit(Box(15.7f, -8.5f, 16.7f, 0f), "deadend", 1.0f, 0f))
        deadend.exits.add(Exit(Box(-0.7f, -8.5f, 0.4f, 0f), "runB", 14.8f, 0f))
        // Only reachable crouched: the duct lip stops a standing body at x = 16.
        deadend.exits.add(Exit(Box(16.4f, -1.4f, 17.8f, 0f), "vent2", 2.2f, 0f))

        vent.exits.add(Exit(Box(29.4f, -3.4f, 30.6f, 0f), "smelter", 1.4f, 0f, door(rooms, "vent2", "door_smelt")))
    }

    /** Reconciles a freshly built level with a saved stage. */
    fun applyStage(g: GameSession, stage: Int) {
        val rooms = g.level.rooms

        fun d(room: String, name: String) = door(rooms, room, name)
        fun station(room: String, id: String) =
            rooms[room]?.props?.firstOrNull { it is ConnectionStation && it.id == id } as? ConnectionStation
        fun socket(room: String, id: String) =
            rooms[room]?.props?.firstOrNull { it is CubeSocket && it.id == id } as? CubeSocket

        // The pack comes into Chapter 2 already working.
        g.hasKeyPack = true
        g.keyPackRepaired = true
        g.keyPackHasElevator = true
        g.subfloorPowered = true

        if (stage > Stage2.LIFT_FIGHT) {
            rooms["car"]?.props?.filterIsInstance<LiftTask>()?.forEach { it.done = true }
        }
        if (stage > Stage2.LIFT_FIXED) d("car", "door_car")?.forceOpen()
        if (stage > Stage2.FIND_BATTERY) {
            (rooms["battery"]?.props?.firstOrNull { it is PowerCube } as? PowerCube)?.consumed = true
        }
        if (stage > Stage2.BATTERY_CARRIED) {
            socket("gate", "sock_gate")?.let { it.filled = true; it.seatTime = 1f }
            d("gate", "door_gate")?.forceOpen()
        }
        if (stage > Stage2.MEET_PLAYERR) {
            (rooms["hall"]?.props?.firstOrNull { it is BreakableDoor } as? BreakableDoor)?.let {
                it.broken = true
                it.breakTime = 1.5f
            }
        }
        if (stage > Stage2.HOIST_ESCAPE) {
            (rooms["hoist"]?.props?.firstOrNull { it is KeySwitch } as? KeySwitch)?.used = true
        }
        if (stage > Stage2.DROPPED) d("hoist", "door_hoist")?.forceOpen()
        if (stage > Stage2.FEED_ROOM) {
            station("feed", "st_feed")?.connected = true
            rooms["feed"]?.props?.filterIsInstance<CableCoil>()?.forEach { it.consumed = true }
        }
        if (stage > Stage2.CUBE_TAKEN) {
            station("feed", "st_feed")?.shardTaken = true
            // The charger has already dropped its cell, so put it back on the deck.
            (rooms["feed"]?.props?.firstOrNull { it is PowerCube && it.id == "cube_b" } as? PowerCube)
                ?.consumed = stage > Stage2.SPINE_OPEN
        }
        if (stage > Stage2.SPINE_OPEN) {
            socket("spine2", "sock_spine")?.let { it.filled = true; it.seatTime = 1f }
            d("spine2", "door_spine")?.forceOpen()
        }
        if (stage > Stage2.VENT_RUN) {
            station("vent2", "st_vent")?.connected = true
            rooms["vent2"]?.props?.filterIsInstance<CableCoil>()?.forEach { it.consumed = true }
            (rooms["vent2"]?.props?.firstOrNull { it is Smelter } as? Smelter)?.active = true
        }

        // Whose hunt this is. Without resetting it, dying during the Playerr's
        // chase and reloading could leave the wrong thing on the route.
        g.monster.kind = if (stage >= Stage2.SHADE_RETURN) Monster.Kind.SHADE
        else if (stage >= Stage2.MEET_PLAYERR) Monster.Kind.PLAYERR
        else Monster.Kind.SHADE
        g.monster.swing = 0f

        // Two pursuits, each with its own route.
        g.monster.setRoute(
            listOf(
                ChaseLeg("hall", HALL_TRIGGER_X - 6f, 27.5f, 0f),
                ChaseLeg("maze_a", 0.5f, 15.5f, 0f),
                ChaseLeg("maze_b", 0.5f, 15.5f, 0f),
                ChaseLeg("hoist", 0.5f, 14.5f, 0f)
            )
        )
    }

    /** The second pursuit's route, swapped in when the shade comes back. */
    fun shadeRoute(): List<ChaseLeg> = listOf(
        ChaseLeg("shade", SHADE_TRIGGER_X - 6f, 25.5f, 0f),
        ChaseLeg("runA", 0.5f, 15.5f, 0f),
        ChaseLeg("runB", 0.5f, 15.5f, 0f),
        ChaseLeg("deadend", 0.5f, 15.0f, 0f)
    )

    fun spawnFor(stage: Int): Triple<String, Float, Float> = when (Stage2.checkpointFor(stage)) {
        Stage2.LIFT_FIGHT -> Triple("car", 7.5f, 0f)
        Stage2.ARRIVAL -> Triple("landing1", 2.5f, 0f)
        Stage2.MEET_PLAYERR -> Triple("hall", 2.0f, 0f)
        Stage2.DROPPED -> Triple("hoist", 15.0f, 0f)
        Stage2.FEED_ROOM -> Triple("feed", 2.0f, 0f)
        Stage2.SHADE_RETURN -> Triple("shade", 2.0f, 0f)
        Stage2.DEAD_END -> Triple("deadend", 2.0f, 0f)
        Stage2.SMELTER_DOOR -> Triple("vent2", 20.5f, 0f)
        else -> Triple("car", 7.5f, 0f)
    }
}
