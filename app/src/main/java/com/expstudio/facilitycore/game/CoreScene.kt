package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Chapter 3's closing shot.
 *
 * The first version of this was four rectangles and two capsules, which is
 * about as much as a still frame needs and nowhere near enough for the last
 * thing the chapter says. This is staged properly: a gantry you are watching
 * from, the well beneath it, both of them arriving at a run, the jump they
 * cannot make, and then the light.
 *
 * Drawn entirely in screen space — there is no session behind it — so it can be
 * rendered and looked at without playing three chapters to get here.
 */
class CoreScene {

    /** Fires once when the crate takes the first impact, for the sound. */
    private var impactFired = false
    private var lastProgress = -1f

    fun consumeImpact(): Boolean {
        val f = impactFired
        impactFired = false
        return f
    }

    fun draw(c: Canvas, d: Draw, w: Float, h: Float, time: Float, progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        if (p < lastProgress) impactFired = false
        lastProgress = p

        val horizon = h * 0.70f          // the deck the pit is cut into
        val pitL = w * 0.30f
        val pitR = w * 0.71f
        val cx = (pitL + pitR) * 0.5f

        // How hot the well is. It has been lit since the gallery; the arrival
        // of two bodies is what makes it a sun.
        val swell = MathX.smoothStep(MathX.clamp((p - 0.52f) / 0.26f, 0f, 1f))
        val heat = 0.45f + 0.55f * swell

        drawBackdrop(c, d, w, h, horizon, heat, time)
        drawWell(c, d, w, h, pitL, pitR, horizon, heat, time)
        drawDeck(c, d, w, h, pitL, pitR, horizon)

        // The crate, sitting exactly where the landing used to be.
        val crateW = w * 0.075f
        val crateL = pitR - crateW * 0.35f
        drawCrate(c, d, crateL, horizon - crateW, crateL + crateW, horizon, p)

        drawArrival(c, d, w, h, pitL, pitR, horizon, p, time)

        // The player, watching from the far side. Small, and much too close.
        if (p < 0.86f) {
            val px = w * 0.86f
            drawWatcher(c, d, px, horizon, h * 0.095f, MathX.clamp((0.86f - p) / 0.1f, 0f, 1f))
        }

        drawBloom(c, d, w, h, cx, horizon, p, time)
        drawCard(c, d, w, h, p, time)
    }

    // ---- the room --------------------------------------------------------

    private fun drawBackdrop(c: Canvas, d: Draw, w: Float, h: Float, horizon: Float, heat: Float, time: Float) {
        d.rect(c, 0f, 0f, w, h, Palette.VOID)
        // Gantry structure above, lit from below by the well. Three receding
        // rows so the space has a ceiling to lose itself in.
        var row = 0
        while (row < 3) {
            val f = row / 3f
            val y = h * (0.06f + f * 0.17f)
            val inset = w * (0.04f + f * 0.09f)
            val tint = Palette.mix(Palette.WALL, Palette.WARN, 0.05f + 0.10f * heat * (1f - f))
            d.rect(c, inset, y, w - inset, y + h * 0.016f, tint)
            var post = 0
            while (post < 7) {
                val x = MathX.lerp(inset, w - inset, post / 6f)
                d.rect(c, x - w * 0.004f, y, x + w * 0.004f, y + h * 0.07f, Palette.mix(tint, Palette.VOID, 0.35f))
                post++
            }
            row++
        }
        // Chains hanging into the light.
        var i = 0
        while (i < 5) {
            val x = w * (0.16f + i * 0.17f)
            val sway = sin(time * 0.6f + i) * w * 0.004f
            d.line(c, x, h * 0.10f, x + sway, horizon - h * 0.19f,
                Palette.withAlpha(Palette.TRIM, 0.34f), h * 0.0035f)
            i++
        }
    }

    /** The well: layered heat, rising embers, and a throat that goes down. */
    private fun drawWell(
        c: Canvas, d: Draw, w: Float, h: Float,
        pitL: Float, pitR: Float, horizon: Float, heat: Float, time: Float
    ) {
        val cx = (pitL + pitR) * 0.5f
        d.rect(c, pitL, horizon, pitR, h, Palette.VOID)

        // Bands climbing the throat, each narrower and hotter than the last, so
        // the eye reads depth rather than a flat orange rectangle.
        var i = 0
        while (i < 18) {
            val f = i / 18f
            val y = MathX.lerp(h * 1.02f, horizon, f)
            // Full pit width at the lip, tapering only a little with depth. It
            // is a shaft, not a funnel — the first pass narrowed to nothing at
            // the bottom and read as a tornado.
            val squeeze = MathX.lerp(0.52f, 1f, f)
            val wob = sin(time * (1.7f + i * 0.29f) + i * 1.3f) * w * 0.008f
            val l = MathX.lerp(cx, pitL, squeeze) + wob
            val r = MathX.lerp(cx, pitR, squeeze) + wob
            // Molten at the bottom, through orange, to a deep red at the lip.
            // Mixing from white made the depths read as fog rather than fire;
            // the white belongs in the core glow, not in the walls.
            val tint = Palette.mix(Palette.WARN, Palette.BAD, MathX.clamp(f * 1.3f, 0f, 1f))
            d.rect(c, l, y - h * 0.045f, r, y, Palette.withAlpha(tint, (0.26f + 0.46f * (1f - f)) * heat))
            i++
        }
        // Embers, drifting up out of it.
        var e = 0
        while (e < 26) {
            val seed = e * 37.7f
            val life = ((time * 0.32f + e * 0.11f) % 1f)
            val x = MathX.lerp(pitL, pitR, ((sin(seed) + 1f) * 0.5f)) + sin(time * 1.4f + seed) * w * 0.012f
            val y = MathX.lerp(horizon + h * 0.06f, horizon - h * 0.42f, life)
            val a = (1f - life) * 0.8f * heat
            d.circle(c, x, y, h * (0.0022f + 0.0026f * (1f - life)), Palette.withAlpha(Palette.WARN, a))
            e++
        }
        // The core itself, low in the shaft, and the light it throws upward.
        d.glow(c, cx, h * 0.97f, w * 0.34f * heat, Palette.TEXT, 1.1f * heat)
        d.glow(c, cx, h * 0.97f, w * 0.55f * heat, Palette.WARN, 0.95f * heat)
        d.glow(c, cx, horizon, w * 0.46f * heat, Palette.WARN, 0.8f * heat)
        d.glow(c, cx, horizon, w * 0.15f * heat, Palette.TEXT, 0.4f * heat)
    }

    /** The deck either side, in silhouette, with the lip picked out in heat. */
    private fun drawDeck(c: Canvas, d: Draw, w: Float, h: Float, pitL: Float, pitR: Float, horizon: Float) {
        for ((l, r) in listOf(0f to pitL, pitR to w)) {
            d.rect(c, l, horizon, r, h, Palette.mix(Palette.FLOOR, Palette.VOID, 0.62f))
            d.rect(c, l, horizon, r, horizon + h * 0.011f, Palette.mix(Palette.TRIM, Palette.WARN, 0.45f))
            // Plate joints, so the deck is made of something.
            var x = l + w * 0.03f
            while (x < r - w * 0.01f) {
                d.rect(c, x, horizon, x + h * 0.002f, h, Palette.withAlpha(Palette.VOID, 0.5f))
                x += w * 0.055f
            }
        }
        // Hazard chevrons at both lips.
        for (side in intArrayOf(0, 1)) {
            val edge = if (side == 0) pitL else pitR
            var k = 0
            while (k < 3) {
                val o = (k - 1) * h * 0.022f
                val dir = if (side == 0) -1f else 1f
                d.line(c, edge + dir * (h * 0.012f) + o, horizon - h * 0.028f,
                    edge + dir * (h * 0.042f) + o, horizon, Palette.withAlpha(Palette.WARN, 0.75f), h * 0.006f)
                k++
            }
        }
    }

    private fun drawCrate(c: Canvas, d: Draw, l: Float, t: Float, r: Float, b: Float, p: Float) {
        // It takes the hit and stays put; a little shudder is all it gives.
        val shake = if (p in 0.40f..0.50f) sin(p * 260f) * (r - l) * 0.05f else 0f
        d.round(c, l + shake, t, r + shake, b, (r - l) * 0.08f,
            Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.28f))
        d.rect(c, l + shake, t, r + shake, t + (b - t) * 0.13f, Palette.mix(Palette.TRIM, Palette.WARN, 0.3f))
        d.roundStroke(c, l + shake, t, r + shake, b, (r - l) * 0.08f,
            Palette.withAlpha(Palette.VOID, 0.6f), 3f)
    }

    // ---- the two of them -------------------------------------------------

    /**
     * Both arrive at a run, jump, meet the crate where the landing was, and go
     * in — the second over the top of the first.
     */
    private fun drawArrival(
        c: Canvas, d: Draw, w: Float, h: Float,
        pitL: Float, pitR: Float, horizon: Float, p: Float, time: Float
    ) {
        // Big enough to read, small enough that two of them are two of them
        // rather than one purple mass filling the frame.
        val hPx = h * 0.195f
        for (which in 0 until 2) {
            val lead = if (which == 0) 0f else 0.105f
            val t = (p - lead) / 0.62f
            if (t <= 0f) continue
            val tint = if (which == 0) Palette.MONSTER
            else Palette.mix(Palette.MONSTER, Palette.MONSTER_CRACK, 0.4f)
            val eye = if (which == 0) Palette.MONSTER_EYE else Palette.MONSTER_CRACK

            when {
                // The run-up, from off the left of frame.
                t < 0.42f -> {
                    val f = t / 0.42f
                    val x = MathX.lerp(-w * 0.12f, pitL - w * 0.02f, f)
                    drawRunner(c, d, x, horizon, hPx, tint, eye, time + which * 1.7f)
                }
                // The jump. Nothing on the far side to land on.
                t < 0.72f -> {
                    val f = (t - 0.42f) / 0.30f
                    val x = MathX.lerp(pitL - w * 0.02f, pitR + w * 0.02f, f)
                    val arc = sin(f * Math.PI.toFloat()) * h * 0.20f
                    drawFaller(c, d, x, horizon - arc, hPx, tint, eye, f * 1.4f)
                    if (f > 0.86f && !impactFired) impactFired = true
                }
                // Off the crate and down the throat.
                t < 1f -> {
                    val f = (t - 0.72f) / 0.28f
                    val e = f * f
                    val x = MathX.lerp(pitR + w * 0.02f, (pitL + pitR) * 0.5f + (which - 0.5f) * w * 0.05f, MathX.smoothStep(f))
                    val y = MathX.lerp(horizon - h * 0.05f, h * 1.25f, e)
                    val scale = MathX.lerp(1f, 0.30f, e)
                    drawFaller(c, d, x, y, hPx * scale, tint, eye, 1.4f + f * 7f)
                }
            }
        }
    }

    private fun drawRunner(
        c: Canvas, d: Draw, cx: Float, feetY: Float, hPx: Float, body: Int, eye: Int, time: Float
    ) {
        val stride = sin(time * 15f) * hPx * 0.20f
        val hipY = feetY - hPx * 0.46f
        val shoulderY = feetY - hPx * 0.86f
        d.glow(c, cx, feetY - hPx * 0.5f, hPx * 0.6f, eye, 0.4f)
        d.ellipse(c, cx, feetY, hPx * 0.30f, hPx * 0.07f, Palette.withAlpha(Palette.VOID, 0.6f))
        d.line(c, cx, hipY, cx + stride, feetY, body, hPx * 0.11f)
        d.line(c, cx, hipY, cx - stride, feetY, body, hPx * 0.11f)
        d.poly(c, floatArrayOf(
            cx - hPx * 0.14f, shoulderY, cx + hPx * 0.16f, shoulderY,
            cx + hPx * 0.09f, hipY, cx - hPx * 0.09f, hipY), body)
        // Arms thrown forward: it is not jogging.
        d.line(c, cx + hPx * 0.06f, shoulderY + hPx * 0.04f,
            cx + hPx * 0.30f, shoulderY + hPx * 0.14f - stride * 0.3f, body, hPx * 0.075f)
        d.line(c, cx - hPx * 0.02f, shoulderY + hPx * 0.04f,
            cx + hPx * 0.24f, shoulderY + hPx * 0.20f + stride * 0.3f, body, hPx * 0.075f)
        val headY = feetY - hPx * 0.92f
        d.circle(c, cx + hPx * 0.03f, headY, hPx * 0.115f, body)
        d.circle(c, cx + hPx * 0.07f, headY, hPx * 0.022f, eye)
    }

    private fun drawFaller(
        c: Canvas, d: Draw, cx: Float, feetY: Float, hPx: Float, body: Int, eye: Int, spin: Float
    ) {
        val tilt = sin(spin) * 0.6f
        val ct = cos(tilt)
        val st = sin(tilt)
        fun px(ox: Float, oy: Float) = cx + ox * ct - oy * st
        fun py(ox: Float, oy: Float) = feetY + ox * st + oy * ct

        d.glow(c, cx, feetY - hPx * 0.45f, hPx * 0.75f, eye, 0.5f)
        // Torso, tumbling.
        d.poly(c, floatArrayOf(
            px(-hPx * 0.15f, -hPx * 0.86f), py(-hPx * 0.15f, -hPx * 0.86f),
            px(hPx * 0.17f, -hPx * 0.86f), py(hPx * 0.17f, -hPx * 0.86f),
            px(hPx * 0.10f, -hPx * 0.46f), py(hPx * 0.10f, -hPx * 0.46f),
            px(-hPx * 0.10f, -hPx * 0.46f), py(-hPx * 0.10f, -hPx * 0.46f)), body)
        // Limbs out, clawing at nothing.
        for (k in intArrayOf(-1, 1)) {
            d.line(c, px(0f, -hPx * 0.46f), py(0f, -hPx * 0.46f),
                px(k * hPx * 0.26f, 0f), py(k * hPx * 0.26f, 0f), body, hPx * 0.10f)
            d.line(c, px(k * hPx * 0.08f, -hPx * 0.82f), py(k * hPx * 0.08f, -hPx * 0.82f),
                px(k * hPx * 0.40f, -hPx * 1.02f), py(k * hPx * 0.40f, -hPx * 1.02f), body, hPx * 0.07f)
        }
        d.circle(c, px(hPx * 0.02f, -hPx * 0.92f), py(hPx * 0.02f, -hPx * 0.92f), hPx * 0.115f, body)
        d.circle(c, px(hPx * 0.06f, -hPx * 0.92f), py(hPx * 0.06f, -hPx * 0.92f), hPx * 0.024f, eye)
    }

    /** The white figure, on the far lip, lit from underneath. */
    private fun drawWatcher(c: Canvas, d: Draw, cx: Float, feetY: Float, hPx: Float, alpha: Float) {
        val body = Palette.withAlpha(Palette.PLAYER, alpha)
        val shade = Palette.withAlpha(Palette.PLAYER_SHADE, alpha)
        d.ellipse(c, cx, feetY, hPx * 0.26f, hPx * 0.07f, Palette.withAlpha(Palette.VOID, 0.5f * alpha))
        d.rect(c, cx - hPx * 0.07f, feetY - hPx * 0.46f, cx - hPx * 0.01f, feetY, shade)
        d.rect(c, cx + hPx * 0.01f, feetY - hPx * 0.46f, cx + hPx * 0.07f, feetY, shade)
        d.round(c, cx - hPx * 0.12f, feetY - hPx * 0.84f, cx + hPx * 0.12f, feetY - hPx * 0.42f, hPx * 0.05f, body)
        d.circle(c, cx, feetY - hPx * 0.90f, hPx * 0.12f, body)
        d.rect(c, cx - hPx * 0.09f, feetY - hPx * 0.93f, cx + hPx * 0.05f, feetY - hPx * 0.88f,
            Palette.withAlpha(Palette.ACCENT, 0.75f * alpha))
    }

    // ---- the light -------------------------------------------------------

    private fun drawBloom(
        c: Canvas, d: Draw, w: Float, h: Float, cx: Float, horizon: Float, p: Float, time: Float
    ) {
        val f = MathX.clamp((p - 0.60f) / 0.22f, 0f, 1f)
        if (f <= 0f) return
        val e = MathX.smoothStep(f)
        // A ring going out across the deck, then the whole frame.
        val ring = w * (0.10f + 1.15f * e)
        d.circleStroke(c, cx, horizon, ring, Palette.withAlpha(Palette.WARN, 0.55f * (1f - e)), h * 0.012f * (1f - e * 0.6f))
        d.glow(c, cx, horizon, w * (0.25f + 0.85f * e), Palette.WARN, 1.25f * e)
        d.glow(c, cx, horizon, w * (0.08f + 0.45f * e), Palette.TEXT, 1.1f * e)
        // Sparks thrown clear.
        var i = 0
        while (i < 30) {
            val a = i * 0.209f
            val r = ring * (0.35f + 0.65f * ((sin(i * 12.9f) + 1f) * 0.5f))
            d.circle(c, cx + cos(a) * r, horizon + sin(a) * r * 0.42f,
                h * 0.003f, Palette.withAlpha(Palette.TEXT, (1f - e) * 0.8f))
            i++
        }
        val wash = MathX.clamp((p - 0.70f) / 0.10f, 0f, 1f)
        if (wash > 0f) d.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.TEXT, wash * wash * 0.96f))
        val settle = MathX.clamp((p - 0.81f) / 0.10f, 0f, 1f)
        if (settle > 0f) d.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.VOID, settle))
    }

    private fun drawCard(c: Canvas, d: Draw, w: Float, h: Float, p: Float, time: Float) {
        if (p < 0.93f) return
        val a = MathX.clamp((p - 0.93f) / 0.05f, 0f, 1f)
        d.textCentered(c, "CHAPTER 3 COMPLETE", w * 0.5f, h * 0.40f, h * 0.075f,
            Palette.withAlpha(Palette.TEXT, a), true)
        d.textCentered(c, "The core has what came for you.", w * 0.5f, h * 0.50f, h * 0.032f,
            Palette.withAlpha(Palette.TEXT_DIM, a))
        d.textCentered(c, "Ren is still down there.", w * 0.5f, h * 0.555f, h * 0.032f,
            Palette.withAlpha(Palette.TEXT_DIM, a))
        if (p >= 0.97f) {
            val hint = 0.45f + 0.35f * sin(time * 3f)
            d.textCentered(c, "tap to return to the menu", w * 0.5f, h * 0.67f, h * 0.034f,
                Palette.withAlpha(Palette.ACCENT, hint), true)
        }
    }
}
