package com.expstudio.facilitycore.game

import com.expstudio.facilitycore.core.Art
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Palette

/**
 * Chapter 3's hand-authored rooms.
 *
 * [Compose] exists for the stretches nobody should remember individually. Every
 * room in here is the other thing: somewhere with a reason to exist, a shape
 * that is its own, and usually something about it that is wrong.
 *
 * Roughly three rooms in five come from this file. The composer fills the rest.
 *
 * Every room here keeps to the same traversal rules the composer is held to —
 * steps at or under 1.05 m from whatever is beside them, gaps at or under
 * 2.0 m, crawl gaps at least 1.05 m tall with clear floor at both ends — and
 * leaves [LANDING] metres of clear floor at each doorway so an arrival never
 * lands on a crate and a gated door always has room to open.
 */
object Rooms3 {

    /** Clear floor kept at both doorways. Matches the composer's. */
    const val LANDING = 3.4f

    /**
     * Returns the authored room for an index in a run, or null to let the
     * composer fill it.
     *
     * The indices are chosen so authored and composed rooms alternate in a way
     * that never puts two composed rooms back to back in the long stretches.
     */
    fun forChain(prefix: String, index: Int, id: String): Room? = when (prefix) {
        "a" -> serviceRun(index, id)
        "b" -> eastSpur(index, id)
        "c" -> collapse(index, id)
        "d" -> sublevelWalk(index, id)
        "e" -> subfloor3(index, id)
        "q" -> empty(index, id)
        "f" -> evacuation(index, id)
        else -> null
    }

    // ---- shared shape ----------------------------------------------------

    private fun room(id: String, title: String, w: Float, h: Float = 8.5f): Room =
        Room(id, title, Box(0f, -h, w, 0f)).apply {
            shell(openLeft = true, openRight = true)
            decorBox(0f, -0.45f, w, 0.45f, Palette.WALL_LIT, Decor.Kind.STRIPE)
        }

    /** A strip light. Dead ones are how a room says how long it has been empty. */
    private fun Room.lamp(x: Float, w: Float, lit: Boolean = true, tint: Int = Palette.WARN) {
        decorBox(x, bounds.t + 0.35f, w, 0.3f, if (lit) tint else Palette.WALL_LIT, Decor.Kind.LIGHT).lit = lit
    }

    private fun Room.conduit(x: Float, w: Float, y: Float = -0.9f) {
        decorBox(x, bounds.t + (-y), w, 0.22f, Palette.TRIM, Decor.Kind.PIPE)
    }

    /** A crawl gap: a block hung with [clear] metres under it. */
    private fun Room.crawl(x: Float, w: Float, clear: Float = 1.15f) {
        solid(x, bounds.t + 1.2f, w, -(bounds.t + 1.2f) - clear)
        decorBox(x, -clear - 0.28f, w, 0.28f, Palette.TRIM, Decor.Kind.PIPE)
    }

    // ---- the service run, under the smelter ------------------------------

    private fun serviceRun(index: Int, id: String): Room? = when (index) {
        0 -> room(id, "Pour Sump", 22f).apply {
            // Directly under the smelter floor. Everything in here has run.
            lamp(4f, 3f)
            lamp(14f, 2.4f, lit = false)
            conduit(2f, 9f)
            // Slag that came through the floor and set where it landed.
            solid(7.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            solid(9.2f, -1.9f, 2.0f, 1.9f)
            solid(11.2f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
            decorBox(6.6f, -2.4f, 0.5f, 2.4f, Art.RUST, Decor.Kind.PANEL)
            decorBox(14.5f, -1.1f, 1.4f, 1.1f, Palette.VOID, Decor.Kind.GRATE)
        }

        1 -> room(id, "Filter Bank", 24f).apply {
            lamp(3f, 2.6f)
            lamp(17f, 2.6f, lit = false)
            // A row of housings you go over, then a low run you go under.
            solid(5.4f, -1.0f, 1.6f, 1.0f, Solid.Kind.CRATE)
            solid(7.4f, -2.0f, 3.2f, 0.4f, Solid.Kind.PLATFORM)
            crawl(13.0f, 3.4f)
            decorBox(5.4f, -3.6f, 5.2f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
            decorBox(18.4f, -2.2f, 1.2f, 2.2f, Palette.WALL, Decor.Kind.PANEL)
        }

        2 -> room(id, "Scrubber Room", 24f).apply {
            lamp(5f, 2.8f, lit = false)
            lamp(16f, 2.8f)
            // Two scrubber towers, one of them opened up from the inside.
            decorBox(5.0f, -7.2f, 2.2f, 5.4f, Palette.WALL, Decor.Kind.PANEL)
            decorBox(16.0f, -7.2f, 2.2f, 5.4f, Palette.WALL, Decor.Kind.PANEL)
            decorBox(16.2f, -5.0f, 1.8f, 2.6f, Palette.VOID, Decor.Kind.GRATE)
            solid(5.0f, -1.8f, 2.2f, 1.8f)
            solid(3.9f, -0.9f, 1.1f, 0.9f, Solid.Kind.CRATE)
            solid(11.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
        }

        3 -> room(id, "Stair Head", 26f).apply {
            lamp(6f, 4f)
            conduit(0f, 26f, y = -7.4f)
            // A flight of stairs with the middle of it gone.
            solid(5.0f, -1.0f, 2.0f, 1.0f)
            solid(7.0f, -2.0f, 2.0f, 2.0f)
            solid(11.2f, -2.0f, 2.2f, 2.0f)
            solid(13.4f, -1.0f, 2.0f, 1.0f)
            decorBox(9.0f, -2.1f, 2.2f, 0.14f, Palette.WARN, Decor.Kind.STRIPE)
            decorBox(19f, -4.0f, 3.4f, 0.28f, Palette.TRIM, Decor.Kind.PIPE)
        }

        else -> null
    }

    // ---- the east spur ---------------------------------------------------

    private fun eastSpur(index: Int, id: String): Room? = when (index) {
        0 -> room(id, "Spur Head", 22f).apply {
            lamp(4f, 3.2f)
            lamp(15f, 3.2f)
            conduit(1f, 20f)
            solid(9.4f, -1.0f, 2.4f, 1.0f, Solid.Kind.CRATE)
        }

        1 -> room(id, "Locker Row", 26f).apply {
            lamp(5f, 2.2f)
            lamp(12f, 2.2f, lit = false)
            lamp(19f, 2.2f)
            // Lockers, all of them open, none of them emptied in a hurry.
            var x = 4.2f
            while (x < 20f) {
                decorBox(x, -2.1f, 0.8f, 2.1f, Palette.WALL, Decor.Kind.PANEL)
                x += 1.15f
            }
            solid(12.0f, -1.0f, 1.6f, 1.0f, Solid.Kind.CRATE)
            solid(13.6f, -1.9f, 1.8f, 1.9f)
        }

        2 -> room(id, "Switch Room", 24f).apply {
            lamp(6f, 3f)
            lamp(17f, 3f, lit = false)
            // Wall after wall of breakers, every handle already thrown.
            var x = 4f
            while (x < 20f) {
                decorBox(x, -5.4f, 2.2f, 3.2f, Palette.WALL, Decor.Kind.PANEL)
                decorBox(x + 0.3f, -4.4f, 1.6f, 0.16f, Palette.BAD, Decor.Kind.STRIPE)
                x += 2.8f
            }
            solid(10.4f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
        }

        3 -> room(id, "Canteen", 28f).apply {
            lamp(7f, 6f)
            lamp(18f, 4f, lit = false)
            // Tables, one of them pushed against the door it did not hold.
            solid(6.0f, -0.95f, 3.0f, 0.4f, Solid.Kind.PLATFORM)
            solid(11.0f, -0.95f, 3.0f, 0.4f, Solid.Kind.PLATFORM)
            solid(16.4f, -1.0f, 1.6f, 1.0f, Solid.Kind.CRATE)
            solid(18.0f, -2.0f, 1.8f, 2.0f)
            decorBox(21f, -3.2f, 3.0f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        }

        4 -> room(id, "Store Aisle", 26f).apply {
            lamp(8f, 4f)
            lamp(19f, 3f, lit = false)
            // Pallets stacked into a staircase by somebody who needed height.
            solid(7.0f, -1.0f, 2.4f, 1.0f, Solid.Kind.CRATE)
            solid(9.4f, -2.0f, 2.4f, 2.0f)
            solid(11.8f, -3.0f, 2.6f, 3.0f)
            solid(14.4f, -2.0f, 2.2f, 2.0f)
            solid(16.6f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            decorBox(11.8f, -4.6f, 2.6f, 1.6f, Palette.VOID, Decor.Kind.GRATE)
        }

        5 -> room(id, "Wet Corridor", 24f).apply {
            lamp(4f, 2.4f, lit = false)
            lamp(16f, 2.4f, lit = false)
            // Something above has been leaking for a long time.
            decorBox(0f, -0.22f, 24f, 0.22f, Palette.ACCENT_DIM, Decor.Kind.STRIPE)
            crawl(7.0f, 3.0f)
            solid(14.0f, -1.05f, 2.0f, 1.05f, Solid.Kind.CRATE)
            decorBox(5.0f, -8.0f, 0.4f, 6.0f, Art.RUST, Decor.Kind.PIPE)
            decorBox(17.5f, -8.0f, 0.4f, 5.2f, Art.RUST, Decor.Kind.PIPE)
        }

        6 -> room(id, "Office Row", 26f).apply {
            lamp(5f, 3f, lit = false)
            lamp(18f, 3f, lit = false)
            // Glass partitions, all of them still intact, which is the unsettling part.
            var x = 4.5f
            while (x < 21f) {
                decorBox(x, -4.2f, 0.28f, 4.2f, Palette.TRIM, Decor.Kind.PIPE)
                decorBox(x + 0.28f, -4.2f, 3.4f, 4.2f, Palette.VOID, Decor.Kind.GRATE)
                x += 4.2f
            }
            solid(12.0f, -0.95f, 2.0f, 0.95f, Solid.Kind.CRATE)
        }

        7 -> room(id, "Cargo Lift Bay", 30f).apply {
            lamp(9f, 8f)
            // The lift itself is long gone; its shaft is a wall with a hole in it.
            decorBox(3.0f, -6.4f, 4.2f, 6.4f, Palette.VOID, Decor.Kind.GRATE)
            solid(9.0f, -1.0f, 2.6f, 1.0f, Solid.Kind.CRATE)
            solid(11.6f, -2.0f, 4.0f, 0.4f, Solid.Kind.PLATFORM)
            solid(17.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            decorBox(22f, -4.6f, 5.0f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        }

        9 -> room(id, "Cable Gallery", 26f).apply {
            lamp(6f, 3f, lit = false)
            lamp(16f, 3f)
            // Loom after loom of it, all dead, all still heavy.
            var y = -2.2f
            while (y > -6.4f) {
                decorBox(1f, y, 24f, 0.26f, Art.CABLE, Decor.Kind.PIPE)
                y -= 1.1f
            }
            solid(8.0f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
            solid(14.2f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
        }

        else -> null
    }

    // ---- the collapse ----------------------------------------------------

    private fun collapse(index: Int, id: String): Room? = when (index) {
        0 -> room(id, "Breach", 24f).apply {
            lamp(5f, 2.2f, lit = false)
            // The ceiling came down here and took the floor plan with it.
            solid(6.0f, -1.0f, 2.4f, 1.0f, Solid.Kind.CRATE)
            solid(8.4f, -2.0f, 2.2f, 2.0f)
            solid(10.6f, -3.0f, 2.6f, 3.0f)
            solid(13.2f, -2.0f, 2.2f, 2.0f)
            solid(15.4f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
            decorBox(9.0f, -8.4f, 5.4f, 5.2f, Palette.VOID, Decor.Kind.GRATE)
        }

        1 -> room(id, "Spoil Heap", 26f).apply {
            lamp(6f, 2.4f, lit = false)
            // Everything the collapse pushed ahead of it, piled against a wall.
            solid(8.0f, -1.0f, 2.6f, 1.0f, Solid.Kind.CRATE)
            solid(10.6f, -2.0f, 2.6f, 2.0f)
            solid(13.2f, -1.05f, 2.4f, 1.05f, Solid.Kind.CRATE)
            decorBox(19f, -6.8f, 0.5f, 6.8f, Art.RUST, Decor.Kind.PANEL)
            decorBox(19.2f, -6.9f, 4.2f, 0.4f, Palette.WALL, Decor.Kind.PANEL)
        }

        2 -> room(id, "Leaning Stack", 26f).apply {
            lamp(4f, 2.4f)
            lamp(18f, 2.4f, lit = false)
            // A rack that fell against the far wall and made a ramp of itself.
            solid(6.6f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
            solid(8.6f, -1.95f, 2.0f, 1.95f)
            solid(10.6f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            decorBox(14f, -5.6f, 0.5f, 5.6f, Palette.WALL_LIT, Decor.Kind.PANEL)
            decorBox(14.2f, -5.8f, 4.6f, 0.4f, Palette.WALL, Decor.Kind.PANEL)
        }

        3 -> room(id, "Split Floor", 26f).apply {
            lamp(9f, 3f, lit = false)
            // The slab sheared. One half of the room is a step up from the other.
            solid(10.0f, -1.0f, 13f, 1.0f, Solid.Kind.PLATFORM)
            decorBox(9.9f, -1.15f, 0.3f, 1.15f, Palette.WARN, Decor.Kind.STRIPE)
            decorBox(4f, -7.6f, 18f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
        }

        4 -> room(id, "Flooded Step", 28f).apply {
            lamp(8f, 3f, lit = false)
            lamp(19f, 3f, lit = false)
            decorBox(0f, -0.3f, 28f, 0.3f, Palette.ACCENT_DIM, Decor.Kind.STRIPE)
            // Stepping stones, none of them far apart, all of them wrong.
            solid(6.0f, -0.9f, 1.4f, 0.9f, Solid.Kind.CRATE)
            solid(9.0f, -0.9f, 1.4f, 0.9f, Solid.Kind.CRATE)
            solid(12.0f, -0.9f, 1.4f, 0.9f, Solid.Kind.CRATE)
            solid(15.2f, -1.0f, 1.6f, 1.0f, Solid.Kind.CRATE)
            decorBox(20f, -7.6f, 0.4f, 5.4f, Art.RUST, Decor.Kind.PIPE)
        }

        6 -> room(id, "Duct Underpass", 24f).apply {
            lamp(3.5f, 2.2f)
            lamp(18f, 2.2f, lit = false)
            // A trunk duct dropped through the ceiling. You go under it.
            crawl(8.0f, 5.2f, clear = 1.2f)
            decorBox(8.0f, -6.6f, 5.2f, 1.6f, Palette.WALL, Decor.Kind.PANEL)
            solid(16.0f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
        }

        7 -> room(id, "Conveyor", 28f).apply {
            lamp(7f, 3f)
            lamp(19f, 3f, lit = false)
            // A belt that stopped loaded, now a walkway with a gap in it.
            solid(6.0f, -1.0f, 6.0f, 0.4f, Solid.Kind.PLATFORM)
            solid(13.6f, -1.0f, 6.4f, 0.4f, Solid.Kind.PLATFORM)
            solid(4.8f, -0.9f, 1.2f, 0.9f, Solid.Kind.CRATE)
            decorBox(12.0f, -1.12f, 1.6f, 0.12f, Palette.WARN, Decor.Kind.STRIPE)
            decorBox(6.0f, -2.6f, 14f, 0.26f, Palette.TRIM, Decor.Kind.PIPE)
        }

        8 -> room(id, "Crane Floor", 30f).apply {
            lamp(10f, 7f)
            // A gantry crane, stopped mid-lift, with its load still under it.
            decorBox(2f, -7.6f, 26f, 0.5f, Palette.WALL_LIT, Decor.Kind.PANEL)
            decorBox(13f, -7.1f, 0.3f, 4.2f, Art.CABLE, Decor.Kind.PIPE)
            solid(11.8f, -2.0f, 2.6f, 2.0f)
            solid(9.4f, -1.0f, 2.4f, 1.0f, Solid.Kind.CRATE)
            solid(14.4f, -1.0f, 2.4f, 1.0f, Solid.Kind.CRATE)
            solid(20.0f, -1.05f, 2.0f, 1.05f, Solid.Kind.CRATE)
        }

        9 -> room(id, "Transfer Approach", 24f).apply {
            lamp(8f, 5f, tint = Palette.BAD)
            conduit(0f, 24f, y = -7.6f)
            solid(12.0f, -0.9f, 2.2f, 0.9f, Solid.Kind.CRATE)
        }

        else -> null
    }

    // ---- after the bulkhead ----------------------------------------------

    private fun sublevelWalk(index: Int, id: String): Room? = when (index) {
        0 -> room(id, "Shutter Side", 24f).apply {
            lamp(5f, 3f)
            lamp(16f, 3f, lit = false)
            conduit(2f, 20f)
            solid(11.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            decorBox(18f, -2.6f, 3.2f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        }

        1 -> room(id, "Wash Station", 22f).apply {
            lamp(6f, 3f, lit = false)
            decorBox(0f, -0.24f, 22f, 0.24f, Palette.ACCENT_DIM, Decor.Kind.STRIPE)
            var x = 5f
            while (x < 16f) {
                decorBox(x, -2.4f, 1.4f, 1.2f, Palette.WALL_LIT, Decor.Kind.PANEL)
                x += 2.6f
            }
            solid(17.0f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
        }

        3 -> room(id, "Muster Point", 26f).apply {
            lamp(7f, 5f, lit = false)
            lamp(17f, 3f)
            // Benches in rows, facing a door nobody reached.
            var x = 6f
            while (x < 17f) {
                solid(x, -0.85f, 2.2f, 0.35f, Solid.Kind.PLATFORM)
                x += 3.4f
            }
            decorBox(20.5f, -3.4f, 2.6f, 3.4f, Palette.WALL, Decor.Kind.PANEL)
        }

        4 -> room(id, "Lift Lobby", 22f).apply {
            lamp(8f, 6f, tint = Palette.ACCENT)
            decorBox(2f, -8.0f, 18f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
            solid(11.0f, -0.95f, 2.0f, 0.95f, Solid.Kind.CRATE)
        }

        else -> null
    }

    // ---- Subfloor 3 ------------------------------------------------------

    private fun deep(id: String, title: String, w: Float): Room = room(id, title, w, 9.5f)

    private fun subfloor3(index: Int, id: String): Room? = when (index) {
        0 -> deep(id, "Arrival Floor", 26f).apply {
            lamp(8f, 6f, tint = Palette.ACCENT)
            conduit(0f, 26f, y = -8.4f)
            solid(12.0f, -1.0f, 2.4f, 1.0f, Solid.Kind.CRATE)
            decorBox(2f, -9.0f, 22f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
        }

        2 -> deep(id, "Pump Hall", 30f).apply {
            lamp(6f, 3f, lit = false)
            lamp(20f, 3f, lit = false)
            // Four pump housings, two of them still turning over.
            var x = 5f
            while (x < 22f) {
                solid(x, -1.9f, 2.4f, 1.9f)
                solid(x - 1.0f, -0.95f, 1.0f, 0.95f, Solid.Kind.CRATE)
                decorBox(x + 0.4f, -3.4f, 1.6f, 1.5f, Palette.TRIM, Decor.Kind.PIPE)
                x += 4.6f
            }
        }

        4 -> deep(id, "Sluice", 26f).apply {
            lamp(4f, 2.2f, lit = false)
            decorBox(0f, -0.34f, 26f, 0.34f, Palette.ACCENT_DIM, Decor.Kind.STRIPE)
            crawl(9.0f, 3.2f)
            solid(15.4f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
            solid(17.2f, -2.0f, 2.0f, 2.0f)
            solid(19.2f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
        }

        6 -> deep(id, "Rack Aisle", 32f).apply {
            lamp(10f, 4f, tint = Palette.ACCENT)
            lamp(22f, 4f, lit = false)
            // Racks to the ceiling, every light on them out.
            var x = 4.5f
            while (x < 27f) {
                decorBox(x, -8.6f, 1.6f, 8.6f, Palette.WALL, Decor.Kind.PANEL)
                x += 3.2f
            }
            solid(13.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
            solid(19.6f, -1.05f, 2.0f, 1.05f, Solid.Kind.CRATE)
        }

        7 -> deep(id, "Coolant Spine", 28f).apply {
            lamp(7f, 3f, tint = Palette.ACCENT)
            lamp(20f, 3f, lit = false)
            // The pipe the whole floor exists to serve, running end to end.
            decorBox(0f, -5.4f, 28f, 0.9f, Palette.TRIM, Decor.Kind.PIPE)
            decorBox(0f, -4.4f, 28f, 0.2f, Palette.ACCENT_DIM, Decor.Kind.STRIPE)
            solid(9.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            solid(11.2f, -2.0f, 3.6f, 0.4f, Solid.Kind.PLATFORM)
            solid(17.0f, -1.05f, 2.0f, 1.05f, Solid.Kind.CRATE)
        }

        9 -> deep(id, "Catwalk Under", 28f).apply {
            lamp(9f, 5f, lit = false)
            // The catwalk overhead is intact. The floor under it is not.
            decorBox(2f, -6.4f, 24f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
            solid(8.0f, -1.0f, 1.6f, 1.0f, Solid.Kind.CRATE)
            solid(10.4f, -2.0f, 3.4f, 0.4f, Solid.Kind.PLATFORM)
            solid(15.8f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
            decorBox(12f, -6.2f, 0.3f, 2.2f, Art.CABLE, Decor.Kind.PIPE)
        }

        11 -> deep(id, "Span Approach", 24f).apply {
            lamp(5f, 3f, tint = Palette.ACCENT)
            conduit(1f, 22f)
            solid(10.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            decorBox(17f, -5.0f, 5.0f, 0.3f, Palette.TRIM, Decor.Kind.PIPE)
        }

        13 -> deep(id, "Boiler Row", 30f).apply {
            lamp(7f, 3f, lit = false)
            lamp(21f, 3f)
            var x = 5f
            while (x < 24f) {
                decorBox(x, -7.4f, 2.6f, 5.6f, Palette.WALL, Decor.Kind.PANEL)
                decorBox(x + 0.6f, -1.8f, 1.4f, 0.5f, Art.RUST, Decor.Kind.PIPE)
                x += 6.4f
            }
            solid(11.4f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
            solid(18.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
        }

        15 -> deep(id, "Collapsed Bay", 28f).apply {
            lamp(6f, 2.4f, lit = false)
            solid(7.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            solid(9.2f, -2.0f, 2.2f, 2.0f)
            solid(11.4f, -3.0f, 2.4f, 3.0f)
            solid(13.8f, -2.0f, 2.2f, 2.0f)
            solid(16.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
            decorBox(10f, -9.2f, 7f, 6f, Palette.VOID, Decor.Kind.GRATE)
        }

        17 -> deep(id, "Quiet Junction", 22f).apply {
            // Nothing in it. On a floor like this that is worse.
            lamp(9f, 3f, lit = false)
            conduit(0f, 22f, y = -8.0f)
        }

        18 -> deep(id, "Vault Door", 26f).apply {
            lamp(10f, 5f, tint = Palette.ACCENT)
            // A door too big for what is behind it, standing open.
            decorBox(9.0f, -7.4f, 3.0f, 7.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
            decorBox(12.0f, -7.4f, 0.4f, 7.4f, Palette.TRIM, Decor.Kind.PIPE)
            solid(15.0f, -1.0f, 2.2f, 1.0f, Solid.Kind.CRATE)
        }

        20 -> deep(id, "Long Gallery", 34f).apply {
            lamp(6f, 3f)
            lamp(16f, 3f, lit = false)
            lamp(26f, 3f, lit = false)
            solid(8.0f, -1.0f, 2.0f, 1.0f, Solid.Kind.CRATE)
            solid(10.2f, -2.0f, 4.0f, 0.4f, Solid.Kind.PLATFORM)
            crawl(18.0f, 3.4f)
            solid(25.0f, -1.05f, 2.2f, 1.05f, Solid.Kind.CRATE)
        }

        22 -> deep(id, "Hall Approach", 24f).apply {
            lamp(8f, 6f, tint = Palette.ACCENT)
            decorBox(2f, -9.0f, 20f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
            solid(12.4f, -1.0f, 2.4f, 1.0f, Solid.Kind.CRATE)
        }

        21 -> deep(id, "Settling Tank", 28f).apply {
            lamp(6f, 2.6f, lit = false)
            lamp(19f, 2.6f, lit = false)
            decorBox(0f, -0.34f, 28f, 0.34f, Palette.ACCENT_DIM, Decor.Kind.STRIPE)
            // A tank wall you climb over rather than round.
            solid(11.0f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
            solid(12.8f, -2.0f, 3.4f, 2.0f)
            solid(16.2f, -1.0f, 1.8f, 1.0f, Solid.Kind.CRATE)
        }

        24 -> deep(id, "Slab Yard", 30f).apply {
            lamp(11f, 4f, lit = false)
            // Poured slabs stacked to be moved, thirty years ago.
            solid(6.0f, -1.0f, 3.0f, 1.0f, Solid.Kind.CRATE)
            solid(9.4f, -2.0f, 3.0f, 2.0f)
            solid(13.0f, -1.0f, 2.6f, 1.0f, Solid.Kind.CRATE)
            solid(18.0f, -1.05f, 3.0f, 1.05f, Solid.Kind.CRATE)
            solid(21.4f, -2.05f, 2.6f, 2.05f)
        }

        25 -> deep(id, "Turbine Floor", 32f).apply {
            lamp(9f, 4f, lit = false)
            lamp(23f, 4f)
            // Casings the size of rooms, split open and stripped.
            decorBox(5f, -6.4f, 7f, 4.6f, Palette.WALL, Decor.Kind.PANEL)
            decorBox(18f, -6.4f, 7f, 4.6f, Palette.WALL, Decor.Kind.PANEL)
            solid(5.0f, -1.8f, 7.0f, 1.8f)
            solid(3.9f, -0.9f, 1.1f, 0.9f, Solid.Kind.CRATE)
            solid(12.0f, -0.9f, 1.4f, 0.9f, Solid.Kind.CRATE)
            solid(18.0f, -1.8f, 7.0f, 1.8f)
            solid(16.7f, -0.9f, 1.3f, 0.9f, Solid.Kind.CRATE)
            solid(25.0f, -0.9f, 1.4f, 0.9f, Solid.Kind.CRATE)
        }

        26 -> deep(id, "Last Straight", 32f).apply {
            // Where it picks you up again. Deliberately clear: the run needs a
            // floor it can be run on.
            lamp(6f, 4f, tint = Palette.BAD)
            lamp(18f, 4f, tint = Palette.BAD)
            lamp(27f, 4f, tint = Palette.BAD)
            conduit(0f, 32f, y = -8.6f)
            solid(15.0f, -0.9f, 2.0f, 0.9f, Solid.Kind.CRATE)
        }

        27 -> deep(id, "Run Through", 28f).apply {
            lamp(7f, 4f, tint = Palette.BAD)
            lamp(20f, 4f, tint = Palette.BAD)
            solid(13.0f, -0.9f, 2.0f, 0.9f, Solid.Kind.CRATE)
            conduit(0f, 28f, y = -8.6f)
        }

        29 -> deep(id, "Gallery Door", 26f).apply {
            lamp(9f, 6f, tint = Palette.BAD)
            solid(11.0f, -0.95f, 2.2f, 0.95f, Solid.Kind.CRATE)
            decorBox(2f, -9.0f, 22f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
        }

        else -> null
    }

    // ---- the three empty rooms -------------------------------------------

    /**
     * The quiet before the lift. All three are authored and all three are
     * nearly bare — the point of them is that after ninety rooms of obstacles
     * there is suddenly nothing to do but walk.
     */
    private fun empty(index: Int, id: String): Room? = when (index) {
        0 -> deep(id, "Empty", 20f).apply { lamp(8f, 4f, lit = false) }
        1 -> deep(id, "Empty", 22f).apply {
            lamp(10f, 3f, lit = false)
            conduit(0f, 22f, y = -8.2f)
        }
        2 -> deep(id, "Empty", 20f).apply {
            lamp(7f, 5f, tint = Palette.ACCENT)
            decorBox(2f, -9.0f, 16f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
        }
        else -> null
    }

    // ---- the evacuation --------------------------------------------------

    /**
     * Sprint rooms. Short, wide open and deliberately almost empty: the last run
     * is about distance and the valve you nearly went past, not about climbing.
     */
    private fun evacuation(index: Int, id: String): Room? = when (index) {
        0 -> room(id, "Evacuation Route", 18f).apply {
            lamp(4f, 3f, tint = Palette.BAD)
            lamp(12f, 3f, tint = Palette.BAD)
            decorBox(0f, -1.4f, 18f, 0.18f, Palette.BAD, Decor.Kind.STRIPE)
        }
        1 -> room(id, "Route — Stair Down", 20f).apply {
            lamp(8f, 4f, tint = Palette.BAD)
            solid(7.0f, -1.0f, 2.4f, 1.0f, Solid.Kind.PLATFORM)
            decorBox(0f, -1.4f, 20f, 0.18f, Palette.BAD, Decor.Kind.STRIPE)
        }

        3 -> room(id, "Route — Junction", 20f).apply {
            lamp(6f, 4f, tint = Palette.BAD)
            solid(11.0f, -0.9f, 1.8f, 0.9f, Solid.Kind.CRATE)
            decorBox(0f, -1.4f, 20f, 0.18f, Palette.BAD, Decor.Kind.STRIPE)
        }
        6 -> room(id, "Route — Under Plant", 20f).apply {
            lamp(9f, 4f, tint = Palette.BAD)
            decorBox(3f, -7.4f, 14f, 0.4f, Palette.WALL, Decor.Kind.PANEL)
            decorBox(0f, -1.4f, 20f, 0.18f, Palette.BAD, Decor.Kind.STRIPE)
        }
        9 -> room(id, "Route — Spill", 18f).apply {
            lamp(5f, 3f, tint = Palette.BAD)
            decorBox(0f, -0.28f, 18f, 0.28f, Palette.WARN, Decor.Kind.STRIPE)
            solid(9.6f, -0.85f, 1.6f, 0.85f, Solid.Kind.CRATE)
        }
        10 -> room(id, "Route — Half Light", 20f).apply {
            lamp(11f, 3f, tint = Palette.BAD)
            decorBox(0f, -1.4f, 20f, 0.18f, Palette.BAD, Decor.Kind.STRIPE)
            decorBox(4f, -7.0f, 5f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
        }

        12 -> room(id, "Route — Cross", 22f).apply {
            lamp(4f, 3f, tint = Palette.BAD)
            lamp(15f, 3f, tint = Palette.BAD)
            solid(10.0f, -0.9f, 2.0f, 0.9f, Solid.Kind.CRATE)
            decorBox(0f, -1.4f, 22f, 0.18f, Palette.BAD, Decor.Kind.STRIPE)
        }
        15 -> room(id, "Route — Narrows", 18f).apply {
            lamp(8f, 3f, tint = Palette.BAD)
            decorBox(2f, -6.4f, 14f, 0.4f, Palette.WALL_LIT, Decor.Kind.PANEL)
        }
        18 -> room(id, "Route — Last Bend", 20f).apply {
            lamp(6f, 4f, tint = Palette.BAD)
            lamp(14f, 3f, tint = Palette.BAD)
            decorBox(0f, -1.4f, 20f, 0.18f, Palette.BAD, Decor.Kind.STRIPE)
        }
        19 -> room(id, "Core Head Approach", 20f).apply {
            // The light stops being facility light and starts being the core.
            lamp(10f, 8f, tint = Palette.WARN)
            decorBox(14f, -8.0f, 6f, 8.0f, Palette.VOID, Decor.Kind.GRATE)
        }
        else -> null
    }
}
