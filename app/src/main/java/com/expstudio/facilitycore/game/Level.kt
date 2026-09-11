package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Camera
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.sin

/** A solid piece of level geometry. */
class Solid(val box: Box, val kind: Kind = Kind.STRUCTURE) {
    enum class Kind { STRUCTURE, PLATFORM, CRATE }
}

/** A walk-through trigger that moves the player to another room. */
class Exit(
    val zone: Box,
    val toRoom: String,
    val spawnX: Float,
    val spawnY: Float,
    /** Optional gate: the exit is inert until this door reports open. */
    val gate: Door? = null,
    val label: String = ""
)

/** Purely visual geometry; never collides. */
class Decor(val box: Box, val color: Int, val kind: Kind = Kind.PANEL) {
    enum class Kind { PANEL, PIPE, LIGHT, SIGN, GRATE, STRIPE }
    var text: String = ""
    var lit: Boolean = false
}

/**
 * Base class for everything the player can look at or use. Props own their own
 * update/draw so rooms stay data-only.
 */
abstract class Prop(val box: Box) {
    open val solid: Box? = null
    /** Shown on the contextual USE button; empty means "not usable right now". */
    open fun interactLabel(g: GameSession): String = ""
    open fun onInteract(g: GameSession) {}
    open fun update(g: GameSession, dt: Float) {}
    abstract fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession)
    /** Distance at which the USE button lights up. */
    open val reach: Float = 1.35f

    fun playerInReach(g: GameSession): Boolean =
        box.inflated(reach).overlaps(g.player.bounds())
}

/** One screen-sized area of the facility. */
class Room(
    val id: String,
    val title: String,
    val bounds: Box,
    /** Subfloor 0 rooms start dark and light up once the feeder cable is thrown. */
    val needsPower: Boolean = false
) {
    val solids = ArrayList<Solid>()
    val decor = ArrayList<Decor>()
    val props = ArrayList<Prop>()
    val exits = ArrayList<Exit>()

    fun solid(x: Float, y: Float, w: Float, h: Float, kind: Solid.Kind = Solid.Kind.STRUCTURE): Solid {
        val s = Solid(Box.of(x, y, w, h), kind)
        solids.add(s)
        return s
    }

    fun decorBox(x: Float, y: Float, w: Float, h: Float, color: Int, kind: Decor.Kind = Decor.Kind.PANEL): Decor {
        val dd = Decor(Box.of(x, y, w, h), color, kind)
        decor.add(dd)
        return dd
    }

    /**
     * Shell of a room: floor, ceiling and both side walls, each [thickness] deep
     * and drawn inward so [bounds] stays the playable rectangle.
     */
    fun shell(thickness: Float = 1f, openLeft: Boolean = false, openRight: Boolean = false) {
        val b = bounds
        solid(b.l - thickness, b.b, b.w + thickness * 2f, thickness)              // floor
        solid(b.l - thickness, b.t - thickness, b.w + thickness * 2f, thickness)  // ceiling
        if (!openLeft) solid(b.l - thickness, b.t, thickness, b.h)
        if (!openRight) solid(b.r, b.t, thickness, b.h)
    }

    fun findProp(predicate: (Prop) -> Boolean): Prop? = props.firstOrNull(predicate)
}

/** The whole chapter: rooms plus the room the player currently occupies. */
class Level(val rooms: LinkedHashMap<String, Room>) {
    fun room(id: String): Room = rooms[id] ?: rooms.values.first()
}

/**
 * A powered door. Doors are solid while closed and animate open, which is why
 * they expose their blocking box rather than living in the solid list.
 */
class Door(
    box: Box,
    val name: String,
    /** How wide the slab is; the visual retracts into the frame as it opens. */
    var open: Boolean = false
) : Prop(box) {

    var openAmount: Float = if (open) 1f else 0f
        private set
    var locked: Boolean = true
    /** Set when the door should never be usable by hand (story shutters). */
    var manual: Boolean = true
    var lockMessage: String = "Locked. I need to unlock this."
    /** Raised by the story once the key pack holds this door's key. */
    var keyAccepted: Boolean = false
    var requiresPower: Boolean = false
    var scanTime: Float = 0f
    var scanning: Boolean = false

    override val solid: Box?
        get() = if (openAmount > 0.85f) null else box

    override val reach: Float = 1.6f

    fun forceOpen() { open = true; openAmount = 1f; locked = false }
    fun forceClose() { open = false; openAmount = 0f }

    override fun interactLabel(g: GameSession): String {
        if (!manual) return ""
        if (open) return ""
        return if (locked) "OPEN" else "OPEN"
    }

    override fun onInteract(g: GameSession) {
        if (open || scanning) return
        if (!locked) { beginOpen(g); return }
        if (!g.hasKeyPack) { g.say(lockMessage); g.sfx(com.expstudio.facilitycore.audio.Sfx.Id.DENY); return }
        if (!g.keyPackRepaired) { g.say("The pack is dead. Its wiring is torn."); g.sfx(com.expstudio.facilitycore.audio.Sfx.Id.DENY); return }
        if (requiresPower && !g.subfloorPowered) {
            g.say("No power on this side. The lock won't even read.")
            g.sfx(com.expstudio.facilitycore.audio.Sfx.Id.DENY)
            return
        }
        if (!keyAccepted) {
            g.say("Not in the database. I guess I have to add it.")
            g.sfx(com.expstudio.facilitycore.audio.Sfx.Id.DENY)
            return
        }
        scanning = true
        scanTime = 0f
        g.sfx(com.expstudio.facilitycore.audio.Sfx.Id.CHIME)
    }

    private fun beginOpen(g: GameSession) {
        open = true
        g.sfx(com.expstudio.facilitycore.audio.Sfx.Id.UNLOCK)
        g.onDoorOpened(this)
    }

    override fun update(g: GameSession, dt: Float) {
        if (scanning) {
            scanTime += dt
            if (scanTime >= SCAN_SECONDS) {
                scanning = false
                locked = false
                beginOpen(g)
            }
        }
        val target = if (open) 1f else 0f
        openAmount = MathX.moveToward(openAmount, target, dt * 0.9f)
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l)
        val r = cam.sx(box.r)
        val t = cam.sy(box.t)
        val b = cam.sy(box.b)
        // Frame.
        d.rect(c, l - cam.s(0.12f), t - cam.s(0.12f), r + cam.s(0.12f), b, Palette.WALL_LIT)
        val slabH = (b - t) * (1f - openAmount)
        if (slabH > 1f) {
            d.rect(c, l, t, r, t + slabH, Palette.mix(Palette.FLOOR_EDGE, Palette.TRIM, 0.5f))
            d.rect(c, l, t + slabH - cam.s(0.12f), r, t + slabH, Palette.TRIM)
            // Hazard chevrons read instantly as "this is a door".
            var i = 0
            while (i < 4) {
                val yy = t + slabH * (0.2f + i * 0.2f)
                if (yy < t + slabH - cam.s(0.1f)) {
                    d.rect(c, l + cam.s(0.12f), yy, r - cam.s(0.12f), yy + cam.s(0.08f),
                        Palette.withAlpha(if (locked) Palette.BAD else Palette.GOOD, 0.55f))
                }
                i++
            }
        }
        // Status lamp.
        val lampX = l - cam.s(0.32f)
        val lampY = t + cam.s(0.55f)
        val color = when {
            open -> Palette.GOOD
            scanning -> Palette.ACCENT
            locked -> Palette.BAD
            else -> Palette.WARN
        }
        val pulse = if (scanning) 0.55f + 0.45f * sin(g.time * 22f) else 1f
        d.glow(c, lampX, lampY, cam.s(0.30f), color, 0.8f * pulse)
        d.circle(c, lampX, lampY, cam.s(0.11f), Palette.withAlpha(color, pulse))

        if (scanning) {
            val p = (scanTime / SCAN_SECONDS).coerceIn(0f, 1f)
            val barW = cam.s(1.5f)
            val bx = (l + r) * 0.5f - barW * 0.5f
            val by = t - cam.s(0.55f)
            d.round(c, bx, by, bx + barW, by + cam.s(0.16f), cam.s(0.08f), Palette.withAlpha(Palette.VOID, 0.8f))
            d.round(c, bx, by, bx + barW * p, by + cam.s(0.16f), cam.s(0.08f), Palette.ACCENT)
            d.textCentered(c, "READING KEY", (l + r) * 0.5f, by - cam.s(0.28f), cam.s(0.26f), Palette.ACCENT, true)
        }
    }

    companion object { const val SCAN_SECONDS = 1.9f }
}
