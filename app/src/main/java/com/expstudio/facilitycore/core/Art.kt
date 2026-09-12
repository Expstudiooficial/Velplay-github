package com.expstudio.facilitycore.core

import android.graphics.Canvas
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Material-level drawing: the difference between a rectangle and a piece of
 * industrial plate. Everything here is procedural and deterministic — a panel
 * derives its bolts, weld seams and rust from its own coordinates, so the
 * facility looks hand-dressed without a single art asset.
 */
object Art {

    /** Stable pseudo-random in 0..1 from a pair of coordinates. */
    private fun hash(a: Float, b: Float): Float {
        val n = sin(a * 127.1f + b * 311.7f) * 43758.547f
        return n - kotlin.math.floor(n)
    }

    /**
     * A plate of facility metal: gradient body, lit top lip, weld seams, corner
     * bolts, and rust bleeding down from the top edge.
     */
    fun plate(
        c: Canvas,
        d: Draw,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        top: Int,
        bottom: Int,
        lip: Int,
        detail: Float,
        seed: Float = 0f,
        bolts: Boolean = true,
        occlusionPx: Float = 0f
    ) {
        if (r - l < 1f || b - t < 1f) return
        d.vGradient(c, l, t, r, b, top, bottom)

        val lipPx = MathX.clamp(detail * 0.06f, 1.5f, 5f)
        // Lit top edge and its soft falloff into the body.
        d.rect(c, l, t, r, t + lipPx, lip)
        d.vGradient(
            c, l, t + lipPx, r, t + lipPx + detail * 0.10f,
            Palette.withAlpha(lip, 0.30f), Palette.withAlpha(lip, 0f)
        )
        // Shaded underside.
        d.vGradient(
            c, l, b - detail * 0.10f, r, b,
            Palette.withAlpha(Palette.VOID, 0f), Palette.withAlpha(Palette.VOID, 0.45f)
        )
        // Side bevels.
        d.rect(c, l, t, l + lipPx * 0.7f, b, Palette.withAlpha(lip, 0.16f))
        d.rect(c, r - lipPx * 0.7f, t, r, b, Palette.withAlpha(Palette.VOID, 0.22f))

        if (detail < 26f) return

        // Weld seams across the plate, spaced by roughly a metre and a half.
        val seamStep = detail * 1.55f
        if (r - l > seamStep * 1.4f) {
            var x = l + seamStep
            while (x < r - lipPx) {
                d.rect(c, x - 1f, t + lipPx, x + 1f, b, Palette.withAlpha(Palette.VOID, 0.32f))
                d.rect(c, x + 1f, t + lipPx, x + 2.5f, b, Palette.withAlpha(lip, 0.13f))
                x += seamStep
            }
        }

        if (bolts && detail >= 34f) {
            val inset = detail * 0.15f
            val boltR = max(1.5f, detail * 0.032f)
            drawBolt(c, d, l + inset, t + inset, boltR, lip)
            drawBolt(c, d, r - inset, t + inset, boltR, lip)
            if (b - t > detail * 0.7f) {
                drawBolt(c, d, l + inset, b - inset, boltR, lip)
                drawBolt(c, d, r - inset, b - inset, boltR, lip)
            }
        }

        // Rust weeping from the top lip. Deterministic per plate.
        if (detail >= 30f) {
            val streaks = 3
            for (i in 0 until streaks) {
                val f = hash(l + seed + i * 7.3f, t + i * 3.1f)
                if (f < 0.45f) continue
                val sx = MathX.lerp(l + lipPx, r - lipPx, hash(l + i * 13f, t + seed))
                val len = (b - t) * (0.25f + f * 0.5f)
                val w = max(1.5f, detail * 0.02f * (0.6f + f))
                d.vGradient(
                    c, sx - w, t + lipPx, sx + w, t + lipPx + len,
                    Palette.withAlpha(RUST, 0.22f * f), Palette.withAlpha(RUST, 0f)
                )
            }
        }

        if (occlusionPx > 0.5f) {
            d.vGradient(
                c, l - occlusionPx * 0.6f, b - occlusionPx, r + occlusionPx * 0.6f, b + occlusionPx,
                Palette.withAlpha(Palette.VOID, 0f), Palette.withAlpha(Palette.VOID, 0.5f)
            )
        }
    }

    private fun drawBolt(c: Canvas, d: Draw, cx: Float, cy: Float, radius: Float, lip: Int) {
        d.circle(c, cx, cy + radius * 0.35f, radius, Palette.withAlpha(Palette.VOID, 0.55f))
        d.circle(c, cx, cy, radius, Palette.withAlpha(lip, 0.85f))
        d.circle(c, cx - radius * 0.28f, cy - radius * 0.28f, radius * 0.42f, Palette.withAlpha(Palette.TEXT, 0.35f))
    }

    /** A shipping crate: banded steel, corner brackets, a stencil, and dents. */
    fun crate(c: Canvas, d: Draw, l: Float, t: Float, r: Float, b: Float, detail: Float, seed: Float) {
        plate(
            c, d, l, t, r, b,
            Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.34f),
            Palette.mix(Palette.WALL, Palette.VOID, 0.42f),
            Palette.mix(Palette.TRIM, Palette.TEXT_DIM, 0.40f),
            detail, seed, bolts = false, occlusionPx = detail * 0.28f
        )
        val lipPx = MathX.clamp(detail * 0.06f, 1.5f, 5f)
        val h = b - t

        // Two horizontal ribs.
        for (f in floatArrayOf(0.32f, 0.68f)) {
            val y = t + h * f
            d.rect(c, l, y, r, y + lipPx * 1.4f, Palette.withAlpha(Palette.VOID, 0.34f))
            d.rect(c, l, y + lipPx * 1.4f, r, y + lipPx * 2.1f, Palette.withAlpha(Palette.TRIM, 0.26f))
        }
        if (detail < 30f) return

        // Corner brackets.
        val arm = MathX.min((r - l) * 0.24f, h * 0.3f)
        val thick = max(2f, detail * 0.035f)
        val bracket = Palette.withAlpha(Palette.TRIM, 0.55f)
        d.rect(c, l, t, l + arm, t + thick, bracket)
        d.rect(c, l, t, l + thick, t + arm, bracket)
        d.rect(c, r - arm, t, r, t + thick, bracket)
        d.rect(c, r - thick, t, r, t + arm, bracket)

        if (detail < 40f) return
        // Hazard stencil on the larger face.
        if (r - l > detail * 1.2f && h > detail * 0.8f) {
            val cx = (l + r) * 0.5f
            val cy = t + h * 0.5f
            val s = MathX.min((r - l) * 0.16f, h * 0.2f)
            d.poly(
                c,
                floatArrayOf(cx, cy - s, cx + s * 0.9f, cy + s * 0.7f, cx - s * 0.9f, cy + s * 0.7f),
                Palette.withAlpha(Palette.WARN, 0.20f)
            )
            d.rect(c, cx - s * 0.08f, cy - s * 0.4f, cx + s * 0.08f, cy + s * 0.18f, Palette.withAlpha(Palette.VOID, 0.35f))
            d.circle(c, cx, cy + s * 0.38f, s * 0.09f, Palette.withAlpha(Palette.VOID, 0.35f))
        }
    }

    /** Floor plate with tread, a wear strip, and a grimy contact line. */
    fun floor(c: Canvas, d: Draw, l: Float, t: Float, r: Float, b: Float, detail: Float) {
        d.vGradient(
            c, l, t, r, b,
            Palette.mix(Palette.FLOOR_EDGE, Palette.TRIM, 0.25f),
            Palette.mix(Palette.FLOOR, Palette.VOID, 0.5f)
        )
        val lipPx = MathX.clamp(detail * 0.05f, 1.5f, 4f)
        d.rect(c, l, t, r, t + lipPx, Palette.mix(Palette.TRIM, Palette.TEXT_DIM, 0.3f))
        if (detail < 24f) return
        // Tread plate: short diagonal ticks along the walking surface.
        val step = detail * 0.42f
        if (step > 6f) {
            var x = l
            var i = 0
            while (x < r) {
                val y = t + lipPx * 1.6f
                val tick = step * 0.34f
                d.line(
                    c, x, y + tick, x + tick, y,
                    Palette.withAlpha(if (i % 2 == 0) Palette.TRIM else Palette.VOID, 0.18f),
                    max(1.2f, detail * 0.018f)
                )
                x += step
                i++
            }
        }
        // Panel joints every couple of metres.
        val joint = detail * 2.1f
        if (joint > 24f) {
            var x = l + joint
            while (x < r) {
                d.rect(c, x - 1f, t, x + 1.5f, b, Palette.withAlpha(Palette.VOID, 0.30f))
                x += joint
            }
        }
    }

    /** A run of pipe with end flanges and a specular highlight. */
    fun pipe(c: Canvas, d: Draw, l: Float, t: Float, r: Float, b: Float, tint: Int, detail: Float) {
        val h = b - t
        d.vGradient(c, l, t, r, b, Palette.mix(tint, Palette.TEXT_DIM, 0.30f), Palette.mix(tint, Palette.VOID, 0.55f))
        d.rect(c, l, t + h * 0.18f, r, t + h * 0.34f, Palette.withAlpha(Palette.TEXT, 0.14f))
        if (detail < 26f) return
        val flangeW = max(3f, detail * 0.07f)
        val step = detail * 2.6f
        var x = l
        while (x < r) {
            d.rect(c, x, t - h * 0.22f, x + flangeW, b + h * 0.22f, Palette.mix(tint, Palette.TRIM, 0.4f))
            d.rect(c, x, t - h * 0.22f, x + flangeW, t + h * 0.1f, Palette.withAlpha(Palette.TEXT, 0.10f))
            x += step
        }
    }

    /**
     * A heavy cable hanging in a catenary between two points, drawn as a thick
     * black line with a lit top edge. Used for every feeder run in the game.
     */
    fun cable(
        c: Canvas,
        d: Draw,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        sagPx: Float,
        thickness: Float,
        tint: Int = CABLE,
        highlight: Int = Palette.TRIM,
        segments: Int = 14
    ) {
        var px = x1
        var py = y1
        for (i in 1..segments) {
            val f = i / segments.toFloat()
            val cx = MathX.lerp(x1, x2, f)
            // A parabola is close enough to a catenary and far cheaper.
            val cy = MathX.lerp(y1, y2, f) + sagPx * 4f * f * (1f - f)
            d.line(c, px, py, cx, cy, tint, thickness)
            px = cx
            py = cy
        }
        // Second pass for the lit upper edge.
        px = x1; py = y1
        for (i in 1..segments) {
            val f = i / segments.toFloat()
            val cx = MathX.lerp(x1, x2, f)
            val cy = MathX.lerp(y1, y2, f) + sagPx * 4f * f * (1f - f)
            d.line(
                c, px, py - thickness * 0.22f, cx, cy - thickness * 0.22f,
                Palette.withAlpha(highlight, 0.30f), thickness * 0.30f
            )
            px = cx
            py = cy
        }
    }

    /** Painted floor or wall stencil, e.g. a hazard chevron band. */
    fun hazardBand(c: Canvas, d: Draw, l: Float, t: Float, r: Float, b: Float, tint: Int, phase: Float = 0f) {
        d.rect(c, l, t, r, b, Palette.withAlpha(Palette.VOID, 0.55f))
        val h = b - t
        val step = max(6f, h * 1.5f)
        var x = l - step + (phase % step)
        while (x < r) {
            d.poly(
                c,
                floatArrayOf(x, b, x + step * 0.5f, t, x + step * 0.95f, t, x + step * 0.45f, b),
                Palette.withAlpha(tint, 0.75f)
            )
            x += step
        }
        d.rect(c, l, t, r, t + max(1f, h * 0.18f), Palette.withAlpha(tint, 0.35f))
    }

    /** Grime pooled in an inside corner. */
    fun cornerGrime(c: Canvas, d: Draw, cx: Float, cy: Float, radius: Float) {
        d.ellipse(c, cx, cy, radius, radius * 0.35f, Palette.withAlpha(Palette.VOID, 0.35f))
    }

    /** Deterministic scatter of small surface marks over a region. */
    fun wear(c: Canvas, d: Draw, l: Float, t: Float, r: Float, b: Float, detail: Float, seed: Float, count: Int = 7) {
        if (detail < 30f) return
        val rng = Random((seed * 1000f).toLong())
        repeat(count) {
            val sx = MathX.lerp(l, r, rng.nextFloat())
            val sy = MathX.lerp(t, b, rng.nextFloat())
            val len = detail * (0.05f + rng.nextFloat() * 0.12f)
            val dark = rng.nextFloat() < 0.65f
            d.line(
                c, sx, sy, sx + len, sy + len * (rng.nextFloat() - 0.5f) * 0.4f,
                Palette.withAlpha(if (dark) Palette.VOID else Palette.TEXT, 0.12f),
                max(1f, detail * 0.012f)
            )
        }
    }

    const val RUST = 0xFF8A4A2B.toInt()
    const val CABLE = 0xFF0A0C10.toInt()

    /** True when a span is big enough on screen to be worth detailing. */
    fun worthDetailing(widthPx: Float, heightPx: Float): Boolean = abs(widthPx) > 10f && abs(heightPx) > 6f
}
