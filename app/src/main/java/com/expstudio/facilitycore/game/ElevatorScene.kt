package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.core.Art
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The lift car, drawn in screen space so the closing beats can be framed
 * exactly. One scene serves two purposes: Chapter 1 ends the moment the roof
 * caves in, and Chapter 2 replays that and keeps going as the thing tears its
 * way inside.
 *
 * Beats, in scene progress:
 *   0.00  doors shut, the car starts down
 *   0.55  IMPACT — something lands on the roof, hard
 *   0.58  the roof deforms: it bows in, rivets pop, the lights fail
 *   0.70  the plate tears and a hole opens
 *   0.80  Chapter 1 cuts to black here
 *   0.84  (Chapter 2) two hands take the torn edges and haul it open
 *   0.94  (Chapter 2) it drops through and lands in the car
 */
class ElevatorScene {

    enum class Mode { CH1_ENDING, CH2_OPENING }

    private val rng = Random(20260912)
    private var lastImpactCue = false

    /** Consumed by the caller so it can fire the sound and the haptic once. */
    var impactCue = false
        private set

    fun reset() {
        lastImpactCue = false
        impactCue = false
    }

    fun update(progress: Float) {
        val hit = progress >= IMPACT
        impactCue = hit && !lastImpactCue
        lastImpactCue = hit
    }

    fun draw(c: Canvas, d: Draw, w: Float, h: Float, time: Float, progress: Float, mode: Mode) {
        val p = progress.coerceIn(0f, 1f)

        // How far past the impact we are, 0..1 over the following moment.
        val hit = ((p - IMPACT) / 0.05f).coerceIn(0f, 1f)
        val warp = ((p - IMPACT) / 0.14f).coerceIn(0f, 1f)
        val tear = ((p - TEAR) / 0.10f).coerceIn(0f, 1f)
        val hands = if (mode == Mode.CH2_OPENING) ((p - HANDS) / 0.10f).coerceIn(0f, 1f) else 0f
        val widen = if (mode == Mode.CH2_OPENING) ((p - WIDEN) / 0.10f).coerceIn(0f, 1f) else 0f
        val drop = if (mode == Mode.CH2_OPENING) ((p - DROP) / 0.06f).coerceIn(0f, 1f) else 0f

        // The car is thrown around by the impact and keeps ringing afterwards.
        val ring = if (p >= IMPACT) {
            val since = (p - IMPACT) * 22f
            sin(since * 9f) * h * 0.020f * kotlin.math.exp(-since * 0.9f).toFloat()
        } else 0f
        val lurch = hit * h * 0.035f * (1f - warp * 0.6f)

        val carW = MathX.min(w * 0.46f, h * 0.95f)
        val cx = w * 0.5f
        val carT = h * 0.20f + lurch + ring
        val carB = h * 0.94f + ring * 0.4f
        val carL = cx - carW * 0.5f
        val carR = cx + carW * 0.5f

        drawShaft(c, d, w, h, p, time)

        // ---- car shell ----------------------------------------------------
        val lightsDead = warp > 0.25f
        val flicker = when {
            warp > 0.55f -> 0f
            lightsDead -> if (sin(time * 44f) > 0.1f) 0.7f else 0.08f
            else -> 0.9f + 0.1f * sin(time * 6f)
        }
        val lampTint = if (lightsDead) Palette.BAD else Palette.ACCENT

        // Back wall of the car.
        d.vGradient(
            c, carL, carT, carR, carB,
            Palette.mix(Palette.WALL_LIT, lampTint, 0.10f * flicker),
            Palette.mix(Palette.WALL, Palette.VOID, 0.45f)
        )
        val detail = carW * 0.28f
        // Wall panelling in three bays.
        for (i in 0 until 3) {
            val bl = MathX.lerp(carL, carR, i / 3f) + carW * 0.02f
            val br = MathX.lerp(carL, carR, (i + 1) / 3f) - carW * 0.02f
            Art.plate(
                c, d, bl, carT + h * 0.06f, br, carB - h * 0.10f,
                Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.30f),
                Palette.mix(Palette.WALL, Palette.VOID, 0.5f),
                Palette.mix(Palette.TRIM, lampTint, 0.25f * flicker),
                detail, bl
            )
        }
        // Handrail.
        val railY = carB - h * 0.30f
        d.round(c, carL + carW * 0.05f, railY, carR - carW * 0.05f, railY + h * 0.016f, h * 0.008f,
            Palette.mix(Palette.TRIM, Palette.TEXT_DIM, 0.35f))
        d.rect(c, carL + carW * 0.05f, railY, carR - carW * 0.05f, railY + h * 0.005f,
            Palette.withAlpha(Palette.TEXT, 0.18f))

        // Floor.
        Art.floor(c, d, carL, carB - h * 0.05f, carR, carB, detail)

        // Control panel with a floor readout.
        val panelW = carW * 0.16f
        val panelL = carR - carW * 0.26f
        val panelT = railY - h * 0.20f
        Art.plate(
            c, d, panelL, panelT, panelL + panelW, panelT + h * 0.17f,
            Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.5f),
            Palette.mix(Palette.WALL, Palette.VOID, 0.4f),
            Palette.mix(Palette.TRIM, Palette.TEXT_DIM, 0.4f),
            detail, panelL
        )
        d.rect(c, panelL + panelW * 0.14f, panelT + h * 0.018f, panelL + panelW * 0.86f, panelT + h * 0.055f,
            Palette.withAlpha(Palette.VOID, 0.85f))
        d.textCentered(
            c, if (p > 0.35f) "-1" else "0", panelL + panelW * 0.5f, panelT + h * 0.037f,
            h * 0.030f, Palette.withAlpha(if (lightsDead) Palette.BAD else Palette.GOOD, flicker.coerceAtLeast(0.2f)), true
        )
        for (i in 0 until 3) {
            val by = panelT + h * 0.075f + i * h * 0.028f
            d.circle(c, panelL + panelW * 0.5f, by, h * 0.009f,
                Palette.withAlpha(if (lightsDead) Palette.BAD else Palette.ACCENT, 0.35f + 0.3f * flicker))
        }

        // Ceiling lamp.
        val lampY = carT + h * 0.03f
        d.round(c, cx - carW * 0.20f, lampY, cx + carW * 0.20f, lampY + h * 0.022f, h * 0.008f,
            Palette.withAlpha(lampTint, 0.25f + 0.7f * flicker))
        d.glow(c, cx, lampY + h * 0.02f, carW * 0.55f, lampTint, 0.85f * flicker)

        // ---- the figure ----------------------------------------------------
        if (drop < 0.5f) {
            val feetY = carB - h * 0.045f
            drawFigure(c, d, cx - carW * 0.12f, feetY, h * 0.30f, time, cowering = warp > 0.1f)
        }

        // ---- the roof, and what happens to it -------------------------------
        drawRoof(c, d, cx, carT, carW, h, warp, tear, hands, widen, drop, time)

        // Grit shaken loose by the impact, falling through the car.
        if (p >= IMPACT && warp < 1f) {
            // Screen-space grit falling from the ruptured roof.
            val count = 14
            for (i in 0 until count) {
                val f = ((time * 0.9f + i * 0.137f) % 1f)
                val gx = cx + (rng.nextFloat() * 0f) + sin(i * 12.9f) * carW * 0.42f
                val gy = carT + f * (carB - carT)
                d.circle(c, gx, gy, h * 0.004f, Palette.withAlpha(Palette.TEXT_DIM, 0.35f * (1f - f)))
            }
        }

        // Chapter 1 slams to black the instant the plate gives way.
        if (mode == Mode.CH1_ENDING && p >= BLACKOUT) {
            d.rect(c, 0f, 0f, w, h, Palette.VOID)
        } else if (mode == Mode.CH2_OPENING && p >= BLACKOUT && p < HANDS) {
            // A held beat of darkness before the hands arrive.
            val k = ((p - BLACKOUT) / (HANDS - BLACKOUT)).coerceIn(0f, 1f)
            d.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.VOID, 1f - k * 0.45f))
        }
    }

    // ---- pieces -----------------------------------------------------------

    private fun drawShaft(c: Canvas, d: Draw, w: Float, h: Float, p: Float, time: Float) {
        d.vGradient(c, 0f, 0f, w, h, Palette.mix(Palette.BG_FAR, Palette.VOID, 0.55f), Palette.VOID)

        // Speed builds, then the impact kills it.
        val speed = if (p < IMPACT) MathX.smoothStep(p / 0.35f) * 7.5f else 7.5f * (1f - ((p - IMPACT) / 0.10f).coerceIn(0f, 1f))
        val scroll = time * speed

        // Guide rails either side.
        for (side in intArrayOf(-1, 1)) {
            val rx = w * 0.5f + side * w * 0.30f
            d.vGradient(c, rx - w * 0.012f, 0f, rx + w * 0.012f, h,
                Palette.mix(Palette.WALL, Palette.TRIM, 0.35f), Palette.mix(Palette.WALL, Palette.VOID, 0.6f))
        }
        // Girders rushing upward past the car.
        val bandH = h * 0.26f
        var y = -((scroll * bandH) % bandH)
        var i = 0
        while (y < h + bandH) {
            val gh = h * 0.030f
            d.rect(c, 0f, y, w, y + gh, Palette.mix(Palette.WALL, Palette.VOID, 0.35f))
            d.rect(c, 0f, y, w, y + gh * 0.28f, Palette.withAlpha(Palette.TRIM, 0.22f))
            // A shaft lamp every few girders.
            if (i % 3 == 0) {
                val lx = w * 0.12f
                d.circle(c, lx, y + gh * 0.5f, h * 0.008f, Palette.withAlpha(Palette.WARN, 0.7f))
                d.glow(c, lx, y + gh * 0.5f, h * 0.06f, Palette.WARN, 0.5f)
            }
            y += bandH
            i++
        }
        // Hoist cables running down the middle of the shaft.
        for (off in floatArrayOf(-0.035f, 0.035f)) {
            d.line(c, w * (0.5f + off), 0f, w * (0.5f + off), h * 0.22f,
                Palette.withAlpha(Art.CABLE, 0.95f), w * 0.006f)
        }
    }

    /**
     * The roof plate. Before the impact it is a clean panel; afterwards it bows
     * inward, splits, and finally tears open.
     */
    private fun drawRoof(
        c: Canvas,
        d: Draw,
        cx: Float,
        carT: Float,
        carW: Float,
        h: Float,
        warp: Float,
        tear: Float,
        hands: Float,
        widen: Float,
        drop: Float,
        time: Float
    ) {
        val halfW = carW * 0.5f
        val thickness = h * 0.055f
        // Deformation: the middle of the plate is driven down.
        val dent = warp * h * 0.115f * (1f + widen * 0.45f)
        val holeHalf = (tear * carW * 0.17f + widen * carW * 0.17f)

        // The shaft void above the roof, so the caved plate has something dark
        // to read against instead of floating on the car's own back wall.
        d.rect(c, cx - halfW, carT - h * 0.10f, cx + halfW, carT + dent + thickness, Palette.VOID)

        // Draw the plate as two halves so a hole can open between them.
        val segments = 12
        val top = FloatArray((segments + 1) * 2)
        for (i in 0..segments) {
            val f = i / segments.toFloat()
            val px = MathX.lerp(cx - halfW, cx + halfW, f)
            // Smooth bell, deepest at the centre.
            val bell = kotlin.math.exp(-((f - 0.5f) * (f - 0.5f)) / 0.045f).toFloat()
            top[i * 2] = px
            top[i * 2 + 1] = carT + dent * bell
        }

        fun plateSpan(fromF: Float, toF: Float) {
            val pts = ArrayList<Float>()
            var i = 0
            while (i <= segments) {
                val f = i / segments.toFloat()
                if (f in fromF..toF) { pts.add(top[i * 2]); pts.add(top[i * 2 + 1]) }
                i++
            }
            if (pts.size < 4) return
            // Close the polygon along the underside.
            val back = ArrayList<Float>()
            i = pts.size - 2
            while (i >= 0) {
                back.add(pts[i]); back.add(pts[i + 1] + thickness)
                i -= 2
            }
            pts.addAll(back)
            d.poly(c, pts.toFloatArray(), Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.35f))
        }

        if (holeHalf <= 1f) {
            plateSpan(0f, 1f)
        } else {
            val gap = holeHalf / carW
            plateSpan(0f, 0.5f - gap)
            plateSpan(0.5f + gap, 1f)
            // Torn edges: long teeth of plate peeled down into the car.
            val teeth = 6
            for (side in intArrayOf(-1, 1)) {
                for (t in 0 until teeth) {
                    val f = t / (teeth - 1f)
                    val ex = cx + side * (holeHalf - f * holeHalf * 0.42f)
                    val ey = carT + dent + thickness * 0.35f
                    val len = h * (0.035f + 0.055f * abs(sin(t * 2.3f + side.toFloat())))
                    val wdt = carW * (0.030f + 0.016f * abs(cos(t * 1.7f)))
                    d.poly(
                        c,
                        floatArrayOf(
                            ex, ey - thickness * 0.5f,
                            ex - side * wdt, ey + len * 0.35f,
                            ex - side * wdt * 0.45f, ey + len,
                            ex + side * wdt * 0.35f, ey + len * 0.45f
                        ),
                        Palette.mix(Palette.TRIM, Palette.TEXT_DIM, 0.25f)
                    )
                    // Bright torn edge where the steel sheared.
                    d.line(
                        c, ex, ey - thickness * 0.5f, ex - side * wdt * 0.45f, ey + len,
                        Palette.withAlpha(Palette.TEXT, 0.35f), MathX.clamp(h * 0.003f, 1f, 3f)
                    )
                }
            }
            // The opening itself: pure dark, with a rim of sheared metal.
            d.poly(
                c,
                floatArrayOf(
                    cx - holeHalf, carT + dent - h * 0.05f,
                    cx + holeHalf, carT + dent - h * 0.05f,
                    cx + holeHalf * 0.82f, carT + dent + thickness * 1.1f,
                    cx - holeHalf * 0.82f, carT + dent + thickness * 1.1f
                ),
                Palette.VOID
            )
            if (tear > 0.5f && drop < 0.2f) {
                val eyeY = carT + dent - h * 0.012f
                val gap2 = holeHalf * 0.34f
                d.glow(c, cx - gap2, eyeY, h * 0.05f, Palette.MONSTER_EYE, 1.1f)
                d.glow(c, cx + gap2, eyeY, h * 0.05f, Palette.MONSTER_EYE, 1.1f)
                d.circle(c, cx - gap2, eyeY, h * 0.008f, Palette.MONSTER_EYE)
                d.circle(c, cx + gap2, eyeY, h * 0.008f, Palette.MONSTER_EYE)
            }
        }

        // Popped rivets and stress cracks radiating from the impact point.
        if (warp > 0.05f) {
            val cracks = 6
            for (i in 0 until cracks) {
                val a = (i / cracks.toFloat()) * 3.14159f
                val len = carW * 0.30f * warp
                val ex = cx + cos(a) * len
                val ey = carT + dent + sin(a) * h * 0.012f
                d.line(c, cx, carT + dent, ex, ey, Palette.withAlpha(Palette.VOID, 0.55f * warp), h * 0.004f)
            }
        }

        // The impact itself: a flash, then a ring of displaced air and dust.
        if (warp in 0.001f..0.45f) {
            val k = 1f - warp / 0.45f
            d.glow(c, cx, carT + dent, carW * 0.8f, Palette.TEXT, k * 0.95f)
            val ring = carW * (0.15f + (1f - k) * 0.75f)
            d.circleStroke(c, cx, carT + dent, ring, Palette.withAlpha(Palette.TEXT, k * 0.35f),
                MathX.clamp(h * 0.006f * k, 1f, 8f))
            d.circleStroke(c, cx, carT + dent, ring * 0.62f, Palette.withAlpha(Palette.TEXT_DIM, k * 0.25f),
                MathX.clamp(h * 0.004f * k, 1f, 5f))
        }

        // ---- Chapter 2: hands on the torn edges, then it comes through ----
        if (hands > 0f && drop < 0.6f) {
            val grip = MathX.smoothStep(hands)
            val strain = sin(time * 18f) * carW * 0.006f * widen
            for (side in intArrayOf(-1, 1)) {
                val hx = cx + side * (holeHalf * 0.9f) + strain * side
                val hy = carT + dent + h * 0.004f
                val armLen = h * 0.085f * grip
                // Forearm reaching down out of the dark, then the hand gripping.
                d.line(c, hx - side * carW * 0.03f, hy - armLen, hx, hy, Palette.MONSTER, h * 0.022f)
                d.circle(c, hx, hy, h * 0.018f, Palette.MONSTER)
                for (f in 0 until 4) {
                    val fy = hy + h * 0.004f + f * h * 0.009f
                    d.line(c, hx, fy, hx - side * carW * 0.045f, fy + h * 0.010f,
                        Palette.MONSTER, h * 0.006f)
                }
                d.line(c, hx - side * carW * 0.02f, hy - armLen * 0.6f, hx, hy,
                    Palette.withAlpha(Palette.MONSTER_CRACK, 0.55f), h * 0.005f)
            }
        }

        if (drop > 0f) {
            // It comes through the hole and lands.
            val k = MathX.smoothStep(drop)
            val feetY = MathX.lerp(carT + dent, carT + h * 0.62f, k)
            val scale = MathX.lerp(h * 0.22f, h * 0.40f, k)
            drawIntruder(c, d, cx, feetY, scale, time)
        }
    }

    /** The white figure, standing very still, or bracing when the roof goes. */
    private fun drawFigure(c: Canvas, d: Draw, fx: Float, feetY: Float, fh: Float, time: Float, cowering: Boolean) {
        val crouch = if (cowering) 0.82f else 1f
        val top = feetY - fh * crouch
        val headR = fh * 0.13f
        val shoulderY = top + headR * 2.0f
        val hipY = feetY - fh * 0.46f * crouch
        val sway = sin(time * 1.7f) * fh * 0.006f

        d.ellipse(c, fx, feetY, fh * 0.15f, fh * 0.035f, Palette.withAlpha(Palette.VOID, 0.55f))
        d.line(c, fx - fh * 0.05f, hipY, fx - fh * 0.07f, feetY, Palette.PLAYER_SHADE, fh * 0.075f)
        d.line(c, fx + fh * 0.05f, hipY, fx + fh * 0.07f, feetY, Palette.PLAYER, fh * 0.075f)
        d.poly(
            c,
            floatArrayOf(
                fx - fh * 0.115f + sway, shoulderY,
                fx + fh * 0.115f + sway, shoulderY,
                fx + fh * 0.085f, hipY,
                fx - fh * 0.085f, hipY
            ),
            Palette.PLAYER
        )
        val armY = shoulderY + fh * 0.02f
        if (cowering) {
            // Arms thrown up over the head as the roof comes in.
            d.line(c, fx - fh * 0.09f, armY, fx - fh * 0.17f, armY - fh * 0.16f, Palette.PLAYER_SHADE, fh * 0.055f)
            d.line(c, fx + fh * 0.09f, armY, fx + fh * 0.17f, armY - fh * 0.16f, Palette.PLAYER, fh * 0.055f)
        } else {
            d.line(c, fx - fh * 0.09f, armY, fx - fh * 0.12f, armY + fh * 0.26f, Palette.PLAYER_SHADE, fh * 0.055f)
            d.line(c, fx + fh * 0.09f, armY, fx + fh * 0.12f, armY + fh * 0.26f, Palette.PLAYER, fh * 0.055f)
        }
        d.circle(c, fx + sway, top + headR, headR, Palette.PLAYER)
        d.round(
            c, fx + sway - headR * 0.7f, top + headR * 0.72f,
            fx + sway + headR * 0.7f, top + headR * 1.05f, headR * 0.15f,
            Palette.withAlpha(Palette.BG_FAR, 0.4f)
        )
        d.glow(c, fx + sway, top + headR, headR * 2.4f, Palette.PLAYER, 0.25f)
    }

    /** The thing that came through, drawn big and close. */
    private fun drawIntruder(c: Canvas, d: Draw, cx: Float, feetY: Float, hPx: Float, time: Float) {
        d.glow(c, cx, feetY - hPx * 0.5f, hPx * 0.7f, Palette.MONSTER_CRACK, 0.6f)
        val hipY = feetY - hPx * 0.46f
        val shoulderY = feetY - hPx * 0.86f
        val headR = hPx * 0.12f
        d.line(c, cx, hipY, cx - hPx * 0.12f, feetY, Palette.MONSTER, hPx * 0.11f)
        d.line(c, cx, hipY, cx + hPx * 0.12f, feetY, Palette.MONSTER, hPx * 0.11f)
        d.poly(
            c,
            floatArrayOf(
                cx - hPx * 0.15f, shoulderY, cx + hPx * 0.15f, shoulderY,
                cx + hPx * 0.09f, hipY, cx - hPx * 0.09f, hipY
            ),
            Palette.MONSTER
        )
        d.line(c, cx - hPx * 0.05f, shoulderY + hPx * 0.05f, cx + hPx * 0.03f, hipY,
            Palette.withAlpha(Palette.MONSTER_CRACK, 0.8f), hPx * 0.02f)
        d.line(c, cx - hPx * 0.14f, shoulderY, cx - hPx * 0.34f, shoulderY + hPx * 0.30f, Palette.MONSTER, hPx * 0.075f)
        d.line(c, cx + hPx * 0.14f, shoulderY, cx + hPx * 0.34f, shoulderY + hPx * 0.30f, Palette.MONSTER, hPx * 0.075f)
        d.circle(c, cx, feetY - hPx * 0.92f, headR, Palette.MONSTER)
        val glowK = 0.75f + 0.25f * sin(time * 6f)
        d.glow(c, cx, feetY - hPx * 0.93f, headR * 3.2f, Palette.MONSTER_EYE, 0.8f * glowK)
        d.circle(c, cx - headR * 0.45f, feetY - hPx * 0.94f, headR * 0.2f, Palette.MONSTER_EYE)
        d.circle(c, cx + headR * 0.45f, feetY - hPx * 0.94f, headR * 0.2f, Palette.MONSTER_EYE)
    }

    companion object {
        const val IMPACT = 0.55f
        const val TEAR = 0.70f
        const val BLACKOUT = 0.80f
        const val HANDS = 0.84f
        const val WIDEN = 0.89f
        const val DROP = 0.94f
    }
}
