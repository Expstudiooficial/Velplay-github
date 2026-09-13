package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The way out.
 *
 * Four chapters of this facility have been lit by failing strip lights, molten
 * metal and things with their own eyes. This is the first scene with daylight
 * in it, and the whole shot is built to withhold that for as long as it can
 * stand to: a car going up through floor after floor, a stop that should not
 * happen, a corridor taken at a run, a door that has to be kicked, and then
 * far too much light all at once.
 *
 * Fires once when the doors go, so the sound can land with the boot.
 */
class EscapeScene {

    private var kickFired = false
    private var last = -1f

    fun consumeKick(): Boolean {
        val k = kickFired
        kickFired = false
        return k
    }

    fun draw(c: Canvas, d: Draw, w: Float, h: Float, time: Float, progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        if (p < last) kickFired = false
        last = p

        when {
            p < RIDE_END -> drawRide(c, d, w, h, time, p / RIDE_END)
            p < STALL_END -> drawStall(c, d, w, h, time, (p - RIDE_END) / (STALL_END - RIDE_END))
            p < RUN_END -> drawRun(c, d, w, h, time, (p - STALL_END) / (RUN_END - STALL_END))
            p < KICK_END -> drawKick(c, d, w, h, time, (p - RUN_END) / (KICK_END - RUN_END))
            else -> drawOut(c, d, w, h, time, (p - KICK_END) / (1f - KICK_END))
        }
    }

    // ---- the ride --------------------------------------------------------

    /** Inside the car, floors going past the grille faster and faster. */
    private fun drawRide(c: Canvas, d: Draw, w: Float, h: Float, time: Float, f: Float) {
        d.rect(c, 0f, 0f, w, h, Palette.VOID)
        val speed = MathX.lerp(2.2f, 14f, MathX.smoothStep(MathX.clamp(f * 1.3f, 0f, 1f)))

        // What is outside, seen through the car's grille: floor slabs, lights,
        // and the numbers running down.
        val shaftL = w * 0.20f
        val shaftR = w * 0.80f
        d.rect(c, shaftL, 0f, shaftR, h, Palette.mix(Palette.BG_FAR, Palette.VOID, 0.4f))
        var i = 0
        while (i < 9) {
            val cycle = ((time * speed * 0.1f + i * 0.111f) % 1f)
            val y = MathX.lerp(-h * 0.2f, h * 1.2f, cycle)
            d.rect(c, shaftL, y, shaftR, y + h * 0.045f, Palette.mix(Palette.WALL, Palette.VOID, 0.25f))
            d.rect(c, shaftL, y + h * 0.045f, shaftR, y + h * 0.055f, Palette.withAlpha(Palette.WARN, 0.5f))
            // Floor markings, counting up toward zero.
            d.textCentered(c, "-${4 - ((i + (time * speed * 0.1f).toInt()) % 5)}",
                shaftL + w * 0.055f, y + h * 0.028f, h * 0.030f,
                Palette.withAlpha(Palette.TEXT_DIM, 0.55f), true)
            i++
        }
        // Motion blur: vertical streaks that lengthen with speed.
        var s = 0
        while (s < 14) {
            val x = MathX.lerp(shaftL, shaftR, ((s * 37) % 100) / 100f)
            val len = h * 0.03f * (speed / 3f)
            val y = ((time * speed * 0.35f + s * 0.19f) % 1.3f - 0.15f) * h
            d.rect(c, x, y, x + w * 0.0025f, y + len, Palette.withAlpha(Palette.TRIM, 0.22f))
            s++
        }

        drawCarInterior(c, d, w, h, time, shake = 0.0015f * speed)
        drawPair(c, d, w, h, calm = true, time = time)
    }

    /** The car frame the whole ride is seen from inside. */
    private fun drawCarInterior(c: Canvas, d: Draw, w: Float, h: Float, time: Float, shake: Float) {
        val j = sin(time * 31f) * w * shake
        // Side walls and the grille bars.
        d.rect(c, 0f, 0f, w * 0.20f + j, h, Palette.mix(Palette.WALL, Palette.VOID, 0.15f))
        d.rect(c, w * 0.80f + j, 0f, w, h, Palette.mix(Palette.WALL, Palette.VOID, 0.15f))
        var i = 0
        while (i < 7) {
            val x = MathX.lerp(w * 0.20f, w * 0.80f, i / 6f) + j
            d.rect(c, x - w * 0.006f, 0f, x + w * 0.006f, h, Palette.mix(Palette.TRIM, Palette.VOID, 0.35f))
            i++
        }
        // Ceiling, floor, and the one working lamp.
        d.rect(c, 0f, 0f, w, h * 0.14f, Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.3f))
        d.rect(c, 0f, h * 0.78f, w, h, Palette.mix(Palette.FLOOR, Palette.VOID, 0.35f))
        d.rect(c, w * 0.36f, h * 0.11f, w * 0.64f, h * 0.135f, Palette.withAlpha(Palette.WARN, 0.8f))
        d.glow(c, w * 0.5f, h * 0.14f, w * 0.34f, Palette.WARN, 0.42f)
    }

    /** Both of them, stood in the car. The first time either is not alone. */
    private fun drawPair(c: Canvas, d: Draw, w: Float, h: Float, calm: Boolean, time: Float) {
        val feet = h * 0.80f
        val hPx = h * 0.30f
        drawFigure(c, d, w * 0.43f, feet, hPx, Palette.PLAYER, Palette.PLAYER_SHADE, Palette.ACCENT, 0f)
        drawFigure(c, d, w * 0.57f, feet, hPx,
            Palette.mix(Palette.PLAYER, Palette.ACCENT_DIM, 0.30f),
            Palette.mix(Palette.PLAYER_SHADE, Palette.ACCENT_DIM, 0.35f),
            Palette.ACCENT, 0f)
        if (calm) {
            // Slight sway, because nothing is happening and that is the point.
            val s = sin(time * 1.1f) * h * 0.003f
            d.rect(c, w * 0.43f - w * 0.001f, feet + s, w * 0.43f + w * 0.001f, feet, Palette.VOID)
        }
    }

    private fun drawFigure(
        c: Canvas, d: Draw, cx: Float, feet: Float, hPx: Float,
        body: Int, shade: Int, visor: Int, swing: Float
    ) {
        d.ellipse(c, cx, feet, hPx * 0.24f, hPx * 0.06f, Palette.withAlpha(Palette.VOID, 0.5f))
        d.line(c, cx, feet - hPx * 0.46f, cx + swing, feet, shade, hPx * 0.10f)
        d.line(c, cx, feet - hPx * 0.46f, cx - swing, feet, shade, hPx * 0.10f)
        d.round(c, cx - hPx * 0.13f, feet - hPx * 0.84f, cx + hPx * 0.13f, feet - hPx * 0.42f, hPx * 0.05f, body)
        d.line(c, cx - hPx * 0.11f, feet - hPx * 0.78f, cx - hPx * 0.16f + swing * 0.4f, feet - hPx * 0.36f, shade, hPx * 0.07f)
        d.line(c, cx + hPx * 0.11f, feet - hPx * 0.78f, cx + hPx * 0.16f - swing * 0.4f, feet - hPx * 0.36f, shade, hPx * 0.07f)
        d.circle(c, cx, feet - hPx * 0.92f, hPx * 0.125f, body)
        d.rect(c, cx - hPx * 0.09f, feet - hPx * 0.95f, cx + hPx * 0.05f, feet - hPx * 0.90f,
            Palette.withAlpha(visor, 0.9f))
    }

    // ---- the stop --------------------------------------------------------

    /** It stops. The lamp goes. Nobody says anything for a moment. */
    private fun drawStall(c: Canvas, d: Draw, w: Float, h: Float, time: Float, f: Float) {
        d.rect(c, 0f, 0f, w, h, Palette.VOID)
        val shaftL = w * 0.20f
        val shaftR = w * 0.80f
        d.rect(c, shaftL, 0f, shaftR, h, Palette.mix(Palette.BG_FAR, Palette.VOID, 0.55f))
        // One slab, stationary, half across the grille. It has stopped between floors.
        d.rect(c, shaftL, h * 0.42f, shaftR, h * 0.50f, Palette.mix(Palette.WALL, Palette.VOID, 0.2f))
        d.rect(c, shaftL, h * 0.50f, shaftR, h * 0.515f, Palette.withAlpha(Palette.WARN, 0.35f))

        val flick = if (f < 0.45f) (1f - f / 0.45f) * (0.5f + 0.5f * sin(time * 37f)) else 0f
        drawCarInterior(c, d, w, h, time, shake = 0.0004f)
        d.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.VOID, 0.55f * (1f - flick)))
        drawPair(c, d, w, h, calm = true, time = time)

        if (f > 0.35f) {
            // The other way out: the hatch, and a hand going up to it.
            val a = MathX.clamp((f - 0.35f) / 0.25f, 0f, 1f)
            d.rect(c, w * 0.44f, h * 0.10f, w * 0.56f, h * 0.145f, Palette.withAlpha(Palette.ACCENT, 0.75f * a))
            d.line(c, w * 0.43f, h * 0.52f, w * 0.48f, h * 0.16f,
                Palette.withAlpha(Palette.PLAYER_SHADE, 0.9f * a), h * 0.012f)
        }
    }

    // ---- the run ---------------------------------------------------------

    /** A corridor going past at speed. They are not walking any more. */
    private fun drawRun(c: Canvas, d: Draw, w: Float, h: Float, time: Float, f: Float) {
        d.rect(c, 0f, 0f, w, h, Palette.VOID)
        val floorY = h * 0.80f
        d.rect(c, 0f, floorY, w, h, Palette.mix(Palette.FLOOR, Palette.VOID, 0.4f))
        d.rect(c, 0f, floorY, w, floorY + h * 0.01f, Palette.TRIM)
        d.rect(c, 0f, 0f, w, h * 0.13f, Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.35f))

        // Walls streaming past. Speed is sold by how fast these move.
        var i = 0
        while (i < 10) {
            val cyc = ((time * 1.9f + i * 0.1f) % 1f)
            val x = MathX.lerp(w * 1.15f, -w * 0.2f, cyc)
            d.rect(c, x, h * 0.13f, x + w * 0.035f, floorY, Palette.mix(Palette.WALL, Palette.VOID, 0.2f))
            if (i % 3 == 0) {
                d.rect(c, x, h * 0.15f, x + w * 0.035f, h * 0.175f, Palette.withAlpha(Palette.WARN, 0.75f))
                d.glow(c, x + w * 0.017f, h * 0.17f, w * 0.07f, Palette.WARN, 0.35f)
            }
            i++
        }
        // Doorways, so the corridor has somewhere to be going.
        var k = 0
        while (k < 3) {
            val cyc = ((time * 0.95f + k * 0.333f) % 1f)
            val x = MathX.lerp(w * 1.3f, -w * 0.35f, cyc)
            d.rect(c, x, floorY - h * 0.30f, x + w * 0.10f, floorY, Palette.withAlpha(Palette.VOID, 0.75f))
            k++
        }

        val swing = sin(time * 17f) * h * 0.05f
        drawFigure(c, d, w * 0.40f, floorY, h * 0.30f, Palette.PLAYER, Palette.PLAYER_SHADE, Palette.ACCENT, swing)
        drawFigure(c, d, w * 0.26f, floorY, h * 0.30f,
            Palette.mix(Palette.PLAYER, Palette.ACCENT_DIM, 0.30f),
            Palette.mix(Palette.PLAYER_SHADE, Palette.ACCENT_DIM, 0.35f),
            Palette.ACCENT, -swing)

        // The end of it, growing.
        val doorW = w * MathX.lerp(0.05f, 0.26f, MathX.smoothStep(f))
        val doorH = h * MathX.lerp(0.14f, 0.62f, MathX.smoothStep(f))
        d.rect(c, w * 0.80f - doorW * 0.5f, floorY - doorH, w * 0.80f + doorW * 0.5f, floorY,
            Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.1f))
        d.rect(c, w * 0.80f - w * 0.003f, floorY - doorH, w * 0.80f + w * 0.003f, floorY,
            Palette.withAlpha(Palette.TEXT, 0.25f + 0.4f * f))
    }

    // ---- the door --------------------------------------------------------

    /** The boot, the hinge, and the first daylight in four chapters. */
    private fun drawKick(c: Canvas, d: Draw, w: Float, h: Float, time: Float, f: Float) {
        d.rect(c, 0f, 0f, w, h, Palette.VOID)
        val floorY = h * 0.82f
        d.rect(c, 0f, floorY, w, h, Palette.mix(Palette.FLOOR, Palette.VOID, 0.45f))
        d.rect(c, 0f, 0f, w, h * 0.12f, Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.4f))

        val open = MathX.smoothStep(MathX.clamp((f - 0.28f) / 0.5f, 0f, 1f))
        if (f > 0.28f && !kickFired) kickFired = true

        // The doors, swinging out. Light comes through the widening seam.
        val cx = w * 0.60f
        val halfW = w * 0.15f
        val topY = floorY - h * 0.60f
        val swing = open * halfW * 1.45f
        d.rect(c, cx - halfW - swing * 0.22f, topY, cx - swing, floorY,
            Palette.mix(Palette.WALL, Palette.VOID, 0.12f))
        d.rect(c, cx + swing, topY, cx + halfW + swing * 0.22f, floorY,
            Palette.mix(Palette.WALL, Palette.VOID, 0.12f))

        // Daylight. Held back until the last possible moment and then far too much.
        val light = open
        d.rect(c, cx - swing, topY, cx + swing, floorY, Palette.withAlpha(Palette.TEXT, 0.92f * light))
        d.glow(c, cx, floorY - h * 0.3f, w * (0.10f + 0.75f * light), Palette.TEXT, 1.15f * light)
        // Dust, suddenly visible in it.
        var i = 0
        while (i < 30 && light > 0.2f) {
            val a = i * 0.41f
            val r = w * (0.02f + 0.20f * ((sin(i * 7.3f) + 1f) * 0.5f)) * (0.4f + light)
            d.circle(c, cx + cos(a) * r * 1.4f, floorY - h * 0.28f + sin(a) * r,
                h * 0.0022f, Palette.withAlpha(Palette.TEXT, 0.5f * light))
            i++
        }

        // The kick itself: one of them planted, the other's leg out.
        val swingLeg = if (f < 0.34f) MathX.lerp(0f, h * 0.11f, f / 0.34f) else h * 0.11f * (1f - (f - 0.34f))
        drawFigure(c, d, w * 0.42f, floorY, h * 0.30f, Palette.PLAYER, Palette.PLAYER_SHADE,
            Palette.ACCENT, swingLeg)
        drawFigure(c, d, w * 0.28f, floorY, h * 0.30f,
            Palette.mix(Palette.PLAYER, Palette.ACCENT_DIM, 0.30f),
            Palette.mix(Palette.PLAYER_SHADE, Palette.ACCENT_DIM, 0.35f),
            Palette.ACCENT, 0f)
    }

    // ---- outside ---------------------------------------------------------

    private fun drawOut(c: Canvas, d: Draw, w: Float, h: Float, time: Float, f: Float) {
        // A white-out that resolves into open air. No facility anywhere in it.
        d.rect(c, 0f, 0f, w, h, Palette.TEXT)
        val settle = MathX.smoothStep(MathX.clamp(f / 0.45f, 0f, 1f))
        val skyTop = Palette.mix(Palette.TEXT, 0xFF9FC6E8.toInt(), settle)
        val skyLow = Palette.mix(Palette.TEXT, 0xFFE8D9BC.toInt(), settle)
        d.vGradient(c, 0f, 0f, w, h * 0.78f, skyTop, skyLow)
        val groundY = h * 0.78f
        d.rect(c, 0f, groundY, w, h, Palette.mix(Palette.TEXT, 0xFF5B6154.toInt(), settle))

        // The facility behind them, small, and closed.
        if (settle > 0.3f) {
            val a = (settle - 0.3f) / 0.7f
            val bl = w * 0.60f
            d.rect(c, bl, groundY - h * 0.16f, bl + w * 0.22f, groundY,
                Palette.withAlpha(Palette.mix(0xFF6B6F66.toInt(), Palette.VOID, 0.25f), a))
            d.rect(c, bl + w * 0.09f, groundY - h * 0.075f, bl + w * 0.13f, groundY,
                Palette.withAlpha(Palette.VOID, 0.65f * a))
        }

        // The two of them, walking away, small in the frame.
        val walk = MathX.clamp((f - 0.25f) / 0.75f, 0f, 1f)
        val x = MathX.lerp(w * 0.52f, w * 0.30f, walk)
        val hPx = h * 0.155f
        val swing = sin(time * 5f) * hPx * 0.12f
        drawFigure(c, d, x, groundY + h * 0.02f, hPx,
            Palette.mix(Palette.PLAYER, Palette.VOID, 0.15f),
            Palette.mix(Palette.PLAYER_SHADE, Palette.VOID, 0.2f), Palette.ACCENT, swing)
        drawFigure(c, d, x - w * 0.035f, groundY + h * 0.02f, hPx,
            Palette.mix(Palette.PLAYER, Palette.ACCENT_DIM, 0.35f),
            Palette.mix(Palette.PLAYER_SHADE, Palette.ACCENT_DIM, 0.4f), Palette.ACCENT, -swing)

        if (f > 0.55f) {
            val a = MathX.clamp((f - 0.55f) / 0.2f, 0f, 1f)
            d.textCentered(c, "FACILITY CORE", w * 0.5f, h * 0.30f, h * 0.085f,
                Palette.withAlpha(Palette.VOID, 0.88f * a), true)
            d.textCentered(c, "Chapter 4 — and out", w * 0.5f, h * 0.375f, h * 0.032f,
                Palette.withAlpha(Palette.VOID, 0.6f * a))
        }
        if (f > 0.80f) {
            val hint = 0.45f + 0.35f * sin(time * 3f)
            d.textCentered(c, "tap to return to the menu", w * 0.5f, h * 0.60f, h * 0.032f,
                Palette.withAlpha(Palette.VOID, hint * 0.8f), true)
        }
    }

    private companion object {
        const val RIDE_END = 0.34f
        const val STALL_END = 0.50f
        const val RUN_END = 0.70f
        const val KICK_END = 0.84f
    }
}
