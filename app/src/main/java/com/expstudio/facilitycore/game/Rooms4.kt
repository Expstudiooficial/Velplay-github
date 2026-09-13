package com.expstudio.facilitycore.game

import com.expstudio.facilitycore.core.Art
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Palette

/**
 * Chapter 4's hand-authored rooms, and the ten mechanisms they are built on.
 *
 * Each of the ten appears first on its own, in a room that teaches it with
 * nothing else going on, and then again later mixed with the others. None of
 * them opens a panel: the room is the puzzle, and the answer is always where
 * you put your body or what you put on top of what.
 *
 *  1. weight on plates            6. a gate on a clock
 *  2. decks that will not hold    7. a switch hit at a run
 *  3. beams on a cycle            8. a beam a crate will stop
 *  4. a column of moving air      9. counterweighted decks
 *  5. a carriage on a rail       10. a rail the hand runs along
 *
 * The traversal rules are Chapter 3's, unchanged: steps at or under 1.05 m from
 * whatever is beside them, gaps at or under 2.0 m, crawls at least 1.05 m tall
 * with clear floor at both mouths, and 3.4 m of clear floor at every doorway.
 */
object Rooms4 {

    const val LANDING = 3.4f

    fun forChain(prefix: String, index: Int, id: String): Room? =
        if (prefix == "g") descent(index, id) else if (prefix == "h") subfloor4(index, id) else null

    // ---- shared shape ----------------------------------------------------

    private fun room(id: String, title: String, w: Float, h: Float = 9.5f): Room =
        Room(id, title, Box(0f, -h, w, 0f)).apply {
            shell(openLeft = true, openRight = true)
            decorBox(0f, -0.45f, w, 0.45f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        }

    private fun Room.lamp(x: Float, w: Float, lit: Boolean = true, tint: Int = Palette.WARN) {
        decorBox(x, bounds.t + 0.35f, w, 0.3f, if (lit) tint else Palette.WALL_LIT, Decor.Kind.LIGHT).lit = lit
    }

    private fun Room.crawl(x: Float, w: Float, clear: Float = 1.15f) {
        solid(x, bounds.t + 1.2f, w, -(bounds.t + 1.2f) - clear)
        decorBox(x, -clear - 0.28f, w, 0.28f, Palette.TRIM, Decor.Kind.PIPE)
    }

    /** A door held shut until a mechanism group in the same room is satisfied. */
    private fun Room.mechDoor(name: String): Door {
        val d = Door(Box.of(bounds.r - 1.0f, -3.6f, 0.8f, 3.6f), name)
        d.locked = false
        d.manual = false
        props.add(d)
        return d
    }

    // ---- the twenty rooms off the core deck ------------------------------

    private fun descent(index: Int, id: String): Room? = when (index) {
        0 -> room(id, "Deck Stair", 24f).apply {
            lamp(6f, 4f)
            solid(6.0f, -1.0f, 2.2f, 1.0f)
            solid(8.2f, -2.0f, 2.2f, 2.0f)
            solid(10.4f, -3.0f, 2.4f, 3.0f)
            solid(12.8f, -2.0f, 2.2f, 2.0f)
            solid(15.0f, -1.0f, 2.2f, 1.0f)
        }

        // 1. Weight. Two plates, one body: the crate has to hold the other.
        3 -> room(id, "Ballast Room", 26f).apply {
            lamp(8f, 5f, tint = Palette.ACCENT)
            props.add(PressurePlate(Box.of(7.0f, -0.16f, 2.0f, 0.16f), "pp_a", "g3"))
            props.add(PressurePlate(Box.of(15.0f, -0.16f, 2.0f, 0.16f), "pp_b", "g3"))
            props.add(HeavyCrate(Box.of(11.0f, -1.6f, 1.6f, 1.6f), "crate_g3"))
            decorBox(7.0f, -5.0f, 2.0f, 0.24f, Palette.TRIM, Decor.Kind.PIPE)
            decorBox(15.0f, -5.0f, 2.0f, 0.24f, Palette.TRIM, Decor.Kind.PIPE)
            mechDoor("door_g3")
        }

        5 -> room(id, "Slag Walk", 24f).apply {
            lamp(5f, 3f, lit = false)
            lamp(17f, 3f)
            solid(8.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            solid(10.2f, -1.9f, 2.0f, 1.9f)
            decorBox(14f, -6.4f, 0.5f, 6.4f, Art.RUST, Decor.Kind.PANEL)
        }

        // 2. Decks that will not hold. An ascent about not stopping.
        8 -> room(id, "Failing Gantry", 28f, 13f).apply {
            lamp(9f, 6f, lit = false)
            solid(4.0f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
            props.add(CrumblePlatform(Box.of(6.4f, -2.3f, 2.6f, 0.4f), "cp1"))
            props.add(CrumblePlatform(Box.of(10.0f, -3.6f, 2.6f, 0.4f), "cp2"))
            props.add(CrumblePlatform(Box.of(13.6f, -4.9f, 2.6f, 0.4f), "cp3"))
            props.add(CrumblePlatform(Box.of(17.2f, -6.2f, 2.6f, 0.4f), "cp4"))
            solid(20.8f, -7.3f, 4.4f, 0.4f, Solid.Kind.PLATFORM)
            // The climb has to be worth making: the release is up there, and the
            // floor below only ever takes you back to the bottom of it.
            props.add(PressurePlate(Box.of(22.0f, -7.46f, 2.0f, 0.16f), "pp_g8", "g8"))
            mechDoor("door_g8")
        }

        10 -> room(id, "Conduit Bank", 26f).apply {
            lamp(7f, 3f)
            var y = -2.4f
            while (y > -7f) { decorBox(1f, y, 24f, 0.26f, Art.CABLE, Decor.Kind.PIPE); y -= 1.2f }
            solid(9.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
            crawl(15.0f, 3.0f)
        }

        // 3. Beams on a cycle. Timed, not removed.
        13 -> room(id, "Cut Line", 30f).apply {
            lamp(10f, 6f, tint = Palette.BAD)
            props.add(SweepBeam(Box.of(6.0f, -1.9f, 5.0f, 0.5f), "sb1", 3.0f, 0.42f, 0f))
            props.add(SweepBeam(Box.of(13.0f, -1.9f, 5.0f, 0.5f), "sb2", 3.0f, 0.42f, 1.5f))
            props.add(SweepBeam(Box.of(20.0f, -1.9f, 5.0f, 0.5f), "sb3", 3.0f, 0.42f, 0.75f))
            // Low cover between them, so the answer is to move in rhythm.
            solid(11.4f, -0.9f, 1.2f, 0.9f, Solid.Kind.CRATE)
            solid(18.4f, -0.9f, 1.2f, 0.9f, Solid.Kind.CRATE)
        }

        15 -> room(id, "Sump Walk", 24f).apply {
            lamp(6f, 3f, lit = false)
            decorBox(0f, -0.3f, 24f, 0.3f, Palette.ACCENT_DIM, Decor.Kind.STRIPE)
            solid(10.0f, -1.0f, 2.4f, 1.0f, Solid.Kind.CRATE)
        }

        // 4. Moving air, on a switch you have to go back for.
        17 -> room(id, "Riser Vent", 26f, 14f).apply {
            lamp(6f, 4f, tint = Palette.ACCENT)
            props.add(SlapSwitch(Box.of(4.0f, -2.4f, 1.0f, 1.0f), "sw_fan", "FAN"))
            props.add(Updraft(Box.of(12.0f, -12.5f, 4.0f, 12.5f), "fan1"))
            solid(17.0f, -6.6f, 5.0f, 0.4f, Solid.Kind.PLATFORM)
            solid(9.0f, -1.0f, 1.6f, 1.0f, Solid.Kind.CRATE)
            props.add(PressurePlate(Box.of(18.5f, -6.76f, 2.0f, 0.16f), "pp_g17", "g17"))
            mechDoor("door_g17")
        }

        19 -> room(id, "Lift Lobby", 22f).apply {
            lamp(9f, 6f, tint = Palette.ACCENT)
            solid(12.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
            decorBox(2f, -9.0f, 18f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
        }

        else -> null
    }

    // ---- the fifty rooms of Subfloor 4 -----------------------------------

    private fun subfloor4(index: Int, id: String): Room? = when (index) {
        0 -> room(id, "Arrival", 24f).apply {
            lamp(8f, 6f, tint = Palette.ACCENT)
            decorBox(2f, -9.0f, 20f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
            solid(12.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
        }

        2 -> room(id, "Dry Store", 26f).apply {
            lamp(7f, 3f)
            lamp(18f, 3f, lit = false)
            solid(7.0f, -1.0f, 2.4f, 1.0f, Solid.Kind.CRATE)
            solid(9.4f, -2.0f, 2.4f, 2.0f)
            solid(11.8f, -1.0f, 2.4f, 1.0f, Solid.Kind.CRATE)
        }

        // 5. A carriage on a rail.
        4 -> room(id, "Transfer Rail", 32f, 11f).apply {
            lamp(10f, 5f, tint = Palette.ACCENT)
            // A stair up to the boarding deck, a wall nothing walks through, and
            // a carriage that goes over the top of it. The rail is the route,
            // not a shortcut past a floor you could have used anyway.
            solid(5.0f, -1.0f, 1.2f, 1.0f, Solid.Kind.CRATE)
            solid(6.2f, -2.0f, 1.2f, 2.0f)
            solid(7.4f, -3.0f, 1.0f, 3.0f)
            solid(8.4f, -4.0f, 2.6f, 0.4f, Solid.Kind.PLATFORM)
            solid(14.0f, -3.6f, 1.4f, 3.6f)
            props.add(Carriage(Box.of(11.2f, -4.05f, 3.2f, 0.45f), "car1", 11.2f, 19.4f, 3.2f))
            solid(22.8f, -4.0f, 3.4f, 0.4f, Solid.Kind.PLATFORM)
            decorBox(11.0f, -4.5f, 12f, 0.16f, Palette.TRIM, Decor.Kind.PIPE)
        }

        6 -> room(id, "Pump Row", 28f).apply {
            lamp(6f, 3f, lit = false)
            var x = 5f
            while (x < 22f) {
                solid(x, -1.9f, 2.2f, 1.9f)
                solid(x - 1.0f, -0.95f, 1.0f, 0.95f, Solid.Kind.CRATE)
                x += 5.4f
            }
        }

        // 6. A gate on a clock, with the switch a long way from it.
        9 -> room(id, "Timed Face", 30f).apply {
            lamp(8f, 5f, tint = Palette.WARN)
            props.add(SlapSwitch(Box.of(3.2f, -2.4f, 1.0f, 1.0f), "sw_gate1", "START"))
            solid(9.0f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
            solid(14.0f, -1.6f, 3.0f, 0.4f, Solid.Kind.PLATFORM)
            solid(19.5f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
            props.add(TimedGate(Box.of(26.0f, -3.8f, 0.9f, 3.8f), "gate_h9", 5.2f))
        }

        12 -> room(id, "Long Hall", 34f, 12f).apply {
            lamp(8f, 4f)
            lamp(24f, 4f, lit = false)
            var i = 0
            while (i < 4) {
                decorBox(5f + i * 7.5f, -11.2f, 1.4f, 9.6f, Palette.WALL, Decor.Kind.PANEL)
                i++
            }
            solid(13f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            solid(22f, -1.05f, 2.0f, 1.05f, Solid.Kind.CRATE)
        }

        // 7. A beam a crate will stop.
        15 -> room(id, "Interlock", 28f).apply {
            lamp(9f, 5f, tint = Palette.ACCENT)
            // A curtain at chest height and one thing in the room tall enough
            // to break it. The hand drags along the floor rather than lifting,
            // so the crate has to be pulled into the line from the far side —
            // which means walking through the curtain first.
            props.add(BlockBeam(Box.of(9.0f, -1.55f, 7.0f, 0.35f), "bb1", "h15"))
            props.add(HeavyCrate(Box.of(3.4f, -1.7f, 1.7f, 1.7f), "crate_h15"))
            solid(19.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            mechDoor("door_h15")
        }

        18 -> room(id, "Spill", 26f).apply {
            lamp(7f, 3f, lit = false)
            decorBox(0f, -0.3f, 26f, 0.3f, Palette.ACCENT_DIM, Decor.Kind.STRIPE)
            crawl(11.0f, 3.2f)
            solid(18.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
        }

        // 8. Counterweighted decks.
        22 -> room(id, "Counterweights", 30f, 13f).apply {
            lamp(10f, 5f, tint = Palette.ACCENT)
            // The deck hangs level with the gantry and only comes down while
            // there is weight in its pan — so the crate goes in, you get on,
            // and then you take the crate back out from up there.
            props.add(Counterweight(Box.of(16.5f, -5.6f, 3.4f, 0.45f), "cw_a", 5.6f,
                Box(21.0f, -1.9f, 23.4f, 0f)))
            props.add(HeavyCrate(Box.of(5.0f, -1.7f, 1.7f, 1.7f), "crate_h22"))
            solid(20.4f, -5.6f, 6.1f, 0.4f, Solid.Kind.PLATFORM)
            props.add(PressurePlate(Box.of(22.0f, -5.76f, 2.0f, 0.16f), "pp_h22", "h22"))
            mechDoor("door_h22")
        }

        25 -> room(id, "Rack Aisle", 30f).apply {
            lamp(9f, 4f, lit = false)
            var x = 4.5f
            while (x < 25f) { decorBox(x, -8.6f, 1.6f, 8.6f, Palette.WALL, Decor.Kind.PANEL); x += 3.2f }
            solid(13.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
        }

        28 -> room(id, "Cut Line II", 30f).apply {
            lamp(10f, 6f, tint = Palette.BAD)
            props.add(SweepBeam(Box.of(5.0f, -1.9f, 4.4f, 0.5f), "sb4", 2.4f, 0.45f, 0f))
            props.add(SweepBeam(Box.of(11.5f, -3.4f, 4.4f, 0.5f), "sb5", 2.4f, 0.45f, 1.2f))
            props.add(SweepBeam(Box.of(18.0f, -1.9f, 4.4f, 0.5f), "sb6", 2.4f, 0.45f, 0.6f))
            solid(11.0f, -1.0f, 5.4f, 1.0f, Solid.Kind.PLATFORM)
            solid(9.6f, -0.9f, 1.4f, 0.9f, Solid.Kind.CRATE)
        }

        // 9. A rail the hand runs along.
        30 -> room(id, "Span", 32f, 12f).apply {
            lamp(8f, 4f, tint = Palette.ACCENT)
            solid(2.0f, -2.4f, 5.0f, 0.4f, Solid.Kind.PLATFORM)
            solid(3.2f, -1.0f, 1.2f, 1.0f, Solid.Kind.CRATE)
            solid(15.0f, -4.2f, 1.4f, 4.2f)
            props.add(PullRail(Box.of(9.0f, -5.4f, 3.0f, 0.3f), "rail1", 22.5f))
            solid(20.5f, -5.2f, 5.0f, 0.4f, Solid.Kind.PLATFORM)
            decorBox(7.5f, -5.7f, 16f, 0.16f, Palette.TRIM, Decor.Kind.PIPE)
        }

        33 -> room(id, "Quiet Run", 24f).apply {
            lamp(11f, 3f, lit = false)
            solid(12.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
        }

        36 -> room(id, "Air Column", 26f, 14f).apply {
            lamp(6f, 4f, tint = Palette.ACCENT)
            props.add(SlapSwitch(Box.of(3.6f, -2.4f, 1.0f, 1.0f), "sw_fan2", "FAN"))
            props.add(Updraft(Box.of(11.0f, -12.5f, 4.0f, 12.5f), "fan2"))
            solid(16.1f, -6.2f, 5.8f, 0.4f, Solid.Kind.PLATFORM)
            props.add(PressurePlate(Box.of(18.0f, -6.36f, 2.0f, 0.16f), "pp_h36", "h36"))
            mechDoor("door_h36")
        }

        // 10. Everything at once, one room before the door.
        40 -> room(id, "Assembly", 34f, 13f).apply {
            lamp(11f, 6f, tint = Palette.ACCENT)
            props.add(PressurePlate(Box.of(5.0f, -0.16f, 2.0f, 0.16f), "pp_c", "h40"))
            props.add(PressurePlate(Box.of(27.0f, -0.16f, 2.0f, 0.16f), "pp_d", "h40"))
            props.add(HeavyCrate(Box.of(9.0f, -1.7f, 1.7f, 1.7f), "crate_h40"))
            props.add(SweepBeam(Box.of(13.0f, -1.9f, 6.0f, 0.5f), "sb8", 3.2f, 0.40f, 0f))
            props.add(CrumblePlatform(Box.of(20.0f, -2.4f, 2.6f, 0.4f), "cp5"))
            mechDoor("door_h40")
        }

        43 -> room(id, "Vault Approach", 26f).apply {
            lamp(10f, 5f, tint = Palette.ACCENT)
            solid(12.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            decorBox(2f, -9.0f, 22f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
        }

        46 -> room(id, "Last Hall", 30f, 12f).apply {
            lamp(8f, 4f)
            lamp(22f, 4f)
            solid(10f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            solid(19f, -1.05f, 2.0f, 1.05f, Solid.Kind.CRATE)
            decorBox(3f, -11.2f, 24f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
        }

        49 -> room(id, "Face Approach", 24f).apply {
            lamp(9f, 6f, tint = Palette.ACCENT)
            decorBox(2f, -9.0f, 20f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
        }

        else -> null
    }

    // ---- the three bolts -------------------------------------------------

    /**
     * Bolt one: weight, in a room with only one crate and three plates.
     *
     * The third plate is the player's; the trick is working out that it has to
     * be the last one, because standing on it is the only thing you cannot
     * leave to do something else.
     */
    fun lockA(): Room = room("lock4a", "Bolt — Ballast", 30f, 11f).apply {
        lamp(9f, 5f, tint = Palette.ACCENT)
        props.add(PressurePlate(Box.of(5.0f, -0.16f, 2.0f, 0.16f), "la1", "lock_a"))
        props.add(PressurePlate(Box.of(13.0f, -0.16f, 2.0f, 0.16f), "la2", "lock_a"))
        props.add(PressurePlate(Box.of(21.0f, -0.16f, 2.0f, 0.16f), "la3", "lock_a"))
        props.add(HeavyCrate(Box.of(9.0f, -1.7f, 1.7f, 1.7f), "crate_la1"))
        props.add(HeavyCrate(Box.of(17.0f, -1.7f, 1.7f, 1.7f), "crate_la2"))
        decorBox(4f, -10.4f, 22f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
    }

    /** Bolt two: the beam has to be stopped while the gate is still open. */
    fun lockB(): Room = room("lock4b", "Bolt — Interlock", 32f, 11f).apply {
        lamp(10f, 5f, tint = Palette.WARN)
        props.add(SlapSwitch(Box.of(3.4f, -2.4f, 1.0f, 1.0f), "sw_lockb", "START"))
        props.add(TimedGate(Box.of(15.0f, -3.8f, 0.9f, 3.8f), "gate_lockb", 6.5f))
        // Chest height, so the crate breaks it standing on the floor — the hand
        // drags along the ground and will not lift anything onto anything.
        props.add(BlockBeam(Box.of(18.0f, -1.55f, 8.0f, 0.35f), "lb1", "lock_b"))
        props.add(HeavyCrate(Box.of(26.6f, -1.7f, 1.7f, 1.7f), "crate_lb"))
        solid(8.0f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
    }

    /** Bolt three: up a failing gantry, through beams, on moving air. */
    fun lockC(): Room = room("lock4c", "Bolt — Riser", 34f, 15f).apply {
        lamp(9f, 5f, tint = Palette.BAD)
        props.add(SlapSwitch(Box.of(3.4f, -2.4f, 1.0f, 1.0f), "sw_lockc", "FAN"))
        // No beam in the column. A cycle you cannot dodge because you are being
        // carried through it is not a puzzle, it is a toll.
        props.add(Updraft(Box.of(8.0f, -13.5f, 4.0f, 13.5f), "fan_lockc"))
        solid(12.6f, -9.4f, 4.4f, 0.4f, Solid.Kind.PLATFORM)
        props.add(CrumblePlatform(Box.of(17.6f, -9.4f, 2.6f, 0.4f), "cp_lc1"))
        props.add(CrumblePlatform(Box.of(21.2f, -9.4f, 2.6f, 0.4f), "cp_lc2"))
        solid(24.8f, -9.4f, 5.0f, 0.4f, Solid.Kind.PLATFORM)
        props.add(PressurePlate(Box.of(26.0f, -9.56f, 2.0f, 0.16f), "lc1", "lock_c"))
        // The way back down, so a failed run is not a reload — and climbable
        // from the floor too, so dropping off it is not a one-way trip either.
        solid(29.0f, -2.6f, 4.0f, 0.4f, Solid.Kind.PLATFORM)
        solid(30.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
    }
}
