package com.expstudio.facilitycore.ui

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.expstudio.facilitycore.core.Art
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.sin
import kotlin.random.Random

/**
 * The menu is not a form on a black rectangle: it is a room in the facility,
 * seen from the dark, drawn with the same materials pass the game uses.
 *
 * A corridor recedes behind failing lights, dust drifts through them, the
 * structure groans, and every so often something at the far end opens its eyes
 * and then does not. Nothing here is interactive — the controls sit on top.
 */
class MenuBackdropView(context: Context) : View(context) {

    private val backdrop = MenuBackdrop()
    private var startedAt = System.nanoTime()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        backdrop.draw(canvas, w, h, (System.nanoTime() - startedAt) / 1_000_000_000f)
        postInvalidateOnAnimation()
    }
}

/** The renderer itself, with no View around it so it can be tested. */
class MenuBackdrop {

    private val draw = Draw()
    private val rng = Random(4242)

    /** Deterministic dust field, in fractions of the view. */
    private val dustX = FloatArray(70) { rng.nextFloat() }
    private val dustY = FloatArray(70) { rng.nextFloat() }
    private val dustR = FloatArray(70) { 0.4f + rng.nextFloat() }
    private val dustS = FloatArray(70) { 0.15f + rng.nextFloat() * 0.5f }

    /**
     * When the thing at the far end is looking, in seconds into the 46 second
     * cycle. Fixed rather than random so the menu has a rhythm you can almost
     * learn, and so it can be captured for review.
     */
    private val watchAt = floatArrayOf(6.5f, 18f, 29.5f, 40f)

    fun draw(canvas: Canvas, w: Float, h: Float, time: Float) {
        drawCorridor(canvas, w, h, time)
        drawDust(canvas, w, h, time)
        drawWatcher(canvas, w, h, time)

        // Screen-edge falloff, heavier than in game — this is a held shot.
        draw.vignette(canvas, w, h, 0.55f, Palette.VOID)
        // A slow breathing darkness over everything.
        val pulse = 0.06f + 0.04f * sin(time * 0.7f)
        draw.rect(canvas, 0f, 0f, w, h, Palette.withAlpha(Palette.VOID, pulse))
    }

    /** A corridor in one-point perspective, receding into nothing. */
    private fun drawCorridor(c: Canvas, w: Float, h: Float, time: Float) {
        draw.vGradient(
            c, 0f, 0f, w, h,
            Palette.mix(Palette.BG_FAR, Palette.VOID, 0.35f),
            Palette.mix(Palette.BG_FAR, Palette.VOID, 0.75f)
        )

        val vx = w * 0.5f
        val vy = h * 0.52f
        val detail = h * 0.05f

        // Bays receding toward the vanishing point. Drawn far to near so the
        // near ones occlude correctly.
        val bays = 7
        for (i in bays - 1 downTo 0) {
            // Slow dolly forward, looping seamlessly.
            val k = ((i + (time * 0.045f) % 1f) / bays).coerceIn(0f, 1f)
            val scale = 0.10f + k * k * 1.25f
            val bw = w * scale
            val bh = h * scale
            val l = vx - bw * 0.5f
            val r = vx + bw * 0.5f
            val t = vy - bh * 0.62f
            val b = vy + bh * 0.55f
            val fade = (k * 1.25f).coerceIn(0f, 1f)

            // Floor and ceiling slabs for this bay.
            val tint = MathX.lerp(0.85f, 0.15f, fade)
            Art.plate(
                c, d = draw, l = l - bw * 0.06f, t = b, r = r + bw * 0.06f, b = b + bh * 0.10f,
                top = Palette.mix(Palette.FLOOR_EDGE, Palette.BG_FAR, tint),
                bottom = Palette.mix(Palette.FLOOR, Palette.VOID, 0.4f + tint * 0.4f),
                lip = Palette.mix(Palette.TRIM, Palette.BG_FAR, tint),
                detail = detail * scale * 6f, seed = i.toFloat(), bolts = false
            )
            Art.plate(
                c, draw, l - bw * 0.06f, t - bh * 0.09f, r + bw * 0.06f, t,
                Palette.mix(Palette.WALL_LIT, Palette.BG_FAR, tint),
                Palette.mix(Palette.WALL, Palette.VOID, 0.4f + tint * 0.4f),
                Palette.mix(Palette.TRIM, Palette.BG_FAR, tint),
                detail * scale * 6f, i + 0.5f, bolts = false
            )
            // Side ribs.
            for (side in intArrayOf(-1, 1)) {
                val rx = if (side < 0) l - bw * 0.06f else r
                draw.vGradient(
                    c, rx, t - bh * 0.09f, rx + bw * 0.06f, b + bh * 0.10f,
                    Palette.mix(Palette.WALL_LIT, Palette.BG_FAR, tint),
                    Palette.mix(Palette.WALL, Palette.VOID, 0.5f + tint * 0.35f)
                )
            }

            // The lamp in this bay, and whether it still works.
            val alive = i != 2 && i != 5
            val flicker = if (alive) 0.75f + 0.25f * sin(time * (7f + i) + i * 2.1f)
            else if (sin(time * 23f + i) > 0.86f) 0.8f else 0.05f
            val lampW = bw * 0.22f
            val ly = t + bh * 0.02f
            draw.round(
                c, vx - lampW * 0.5f, ly, vx + lampW * 0.5f, ly + bh * 0.035f, bh * 0.012f,
                Palette.withAlpha(Palette.WARN, (0.35f + 0.65f * flicker) * (1f - fade * 0.5f))
            )
            // Hot core inside the tube, so a working lamp actually looks lit.
            draw.round(
                c, vx - lampW * 0.42f, ly + bh * 0.008f, vx + lampW * 0.42f, ly + bh * 0.026f, bh * 0.008f,
                Palette.withAlpha(Palette.TEXT, 0.55f * flicker * (1f - fade * 0.6f))
            )
            draw.glow(c, vx, ly + bh * 0.03f, bw * 0.55f, Palette.WARN, 0.9f * flicker * (1f - fade * 0.55f))
            // The pool it throws on the floor below.
            draw.lightCone(
                c, vx, ly + bh * 0.03f, bw * 0.10f, bw * 0.40f, (b - ly) * 0.95f,
                Palette.WARN, 0.7f * flicker * (1f - fade * 0.5f)
            )
        }

        // Perspective lines running to the vanishing point, which is what
        // actually sells the depth — without them the bays read as picture
        // frames stacked on top of each other.
        for (corner in 0 until 4) {
            val ox = if (corner % 2 == 0) -1f else 1f
            val oy = if (corner < 2) -1f else 1f
            draw.line(
                c, vx + ox * w * 0.62f, vy + oy * h * 0.62f, vx, vy,
                Palette.withAlpha(Palette.TRIM, 0.10f), MathX.clamp(h * 0.0025f, 1f, 3f)
            )
        }

        // The dark the corridor runs into. A soft fade, never a hard disc.
        draw.glow(c, vx, vy, w * 0.20f, Palette.VOID, 2.4f)
        draw.glow(c, vx, vy, w * 0.09f, Palette.VOID, 2.4f)
    }

    private fun drawDust(c: Canvas, w: Float, h: Float, time: Float) {
        for (i in dustX.indices) {
            val rise = ((time * dustS[i] * 0.06f) + dustY[i]) % 1f
            val x = (dustX[i] + sin(time * 0.25f + i) * 0.012f) * w
            val y = (1f - rise) * h
            // Brightest in the middle of the frame, where the light is.
            val near = 1f - (kotlin.math.abs(x / w - 0.5f) * 1.7f).coerceIn(0f, 1f)
            val twinkle = 0.5f + 0.5f * sin(time * 2.1f + i * 1.7f)
            draw.circle(
                c, x, y, dustR[i] * h * 0.0018f,
                Palette.withAlpha(Palette.TEXT, 0.22f * near * twinkle)
            )
        }
    }

    /** Something at the end of the corridor, on its own schedule. */
    private fun drawWatcher(c: Canvas, w: Float, h: Float, time: Float) {
        val cycle = 46f
        val t = time % cycle
        var strength = 0f
        for (at in watchAt) {
            val local = t - (at % cycle)
            if (local in 0f..3.2f) {
                // Fades up, holds, snaps out.
                strength = when {
                    local < 0.9f -> local / 0.9f
                    local < 2.6f -> 1f
                    else -> 1f - (local - 2.6f) / 0.6f
                }.coerceIn(0f, 1f)
            }
        }
        if (strength <= 0.01f) return

        val vx = w * 0.5f
        val vy = h * 0.52f
        val gap = w * 0.013f
        // A blink partway through, because a steady stare reads as a light.
        val blink = if (sin(time * 3.1f) > 0.93f) 0.15f else 1f
        val k = strength * blink
        draw.glow(c, vx - gap, vy, w * 0.030f, Palette.MONSTER_EYE, 0.95f * k)
        draw.glow(c, vx + gap, vy, w * 0.030f, Palette.MONSTER_EYE, 0.95f * k)
        draw.circle(c, vx - gap, vy, w * 0.0035f, Palette.withAlpha(Palette.MONSTER_EYE, k))
        draw.circle(c, vx + gap, vy, w * 0.0035f, Palette.withAlpha(Palette.MONSTER_EYE, k))
        // The suggestion of a head around them.
        draw.circle(c, vx, vy + w * 0.002f, w * 0.030f, Palette.withAlpha(Palette.MONSTER, 0.55f * k))
    }
}
