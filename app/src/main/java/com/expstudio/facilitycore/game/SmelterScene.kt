package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.core.Art
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * The end of Chapter 2, watched from behind the pour control glass: the gantry
 * gives way under it, it falls, and the pour takes it.
 *
 * Beats, in scene progress:
 *   0.00  the hatch seals; the glass is all that is between you and the heat
 *   0.22  it slams into the glass, twice
 *   0.45  the gantry it is standing on shears away
 *   0.52  the fall — long, and it never stops looking at you
 *   0.78  the pour swallows it; the room goes white, then dark
 */
class SmelterScene {

    private val rng = Random(77)
    private var impactFired = false
    private var lastBeat = 0

    /** True once, on each beat worth a sound. */
    fun consumeImpact(): Boolean {
        val fired = impactFired
        impactFired = false
        return fired
    }

    fun draw(c: Canvas, d: Draw, w: Float, h: Float, time: Float, progress: Float) {
        val p = progress.coerceIn(0f, 1f)

        val beat = when {
            p >= FALL -> 3
            p >= SHEAR -> 2
            p >= SLAM -> 1
            else -> 0
        }
        if (beat != lastBeat && beat > 0) { impactFired = true; lastBeat = beat }

        // ---- the pour, seen through the window ---------------------------
        d.vGradient(c, 0f, 0f, w, h, Palette.mix(Palette.BG_FAR, Palette.VOID, 0.4f), Palette.VOID)

        val winL = w * 0.12f
        val winR = w * 0.88f
        val winT = h * 0.14f
        val winB = h * 0.80f

        // Everything beyond the glass.
        d.vGradient(c, winL, winT, winR, winB,
            Palette.mix(Smelter.LAVA_DEEP, Palette.VOID, 0.55f), Smelter.LAVA_HOT)

        // Ladles and gantries silhouetted against the glow.
        for (i in 0 until 4) {
            val gy = winT + (winB - winT) * (0.18f + i * 0.16f)
            d.rect(c, winL, gy, winR, gy + h * 0.012f, Palette.withAlpha(Palette.VOID, 0.55f))
        }
        val pourX = w * 0.5f
        d.vGradient(c, pourX - w * 0.035f, winT, pourX + w * 0.035f, winB * 0.92f,
            Smelter.LAVA_HOT, Smelter.LAVA_GLOW)
        d.glow(c, pourX, winB * 0.86f, w * 0.30f, Smelter.LAVA_GLOW, 0.9f)

        // Churn on the surface of the pour.
        for (i in 0 until 6) {
            val f = i / 6f
            val y = winB * (0.80f + f * 0.10f)
            val shift = sin(time * (1.1f + f * 0.7f) + i) * w * 0.03f
            d.rect(c, winL + shift, y, winR + shift, y + h * 0.009f,
                Palette.withAlpha(Smelter.LAVA_HOT, 0.45f))
        }

        // ---- the gantry it is standing on ---------------------------------
        val gantryY = winT + (winB - winT) * 0.40f
        val shear = ((p - SHEAR) / 0.06f).coerceIn(0f, 1f)
        val gantryDrop = shear * h * 0.10f
        val tilt = shear * h * 0.06f
        d.poly(
            c,
            floatArrayOf(
                w * 0.30f, gantryY + gantryDrop,
                w * 0.70f, gantryY + gantryDrop + tilt,
                w * 0.70f, gantryY + gantryDrop + tilt + h * 0.022f,
                w * 0.30f, gantryY + gantryDrop + h * 0.022f
            ),
            Palette.mix(Palette.WALL, Palette.VOID, 0.25f)
        )

        // ---- the figure ---------------------------------------------------
        val fallK = ((p - FALL) / 0.26f).coerceIn(0f, 1f)
        val consumed = ((p - CONSUMED) / 0.08f).coerceIn(0f, 1f)
        if (consumed < 1f) {
            val slamK = ((p - SLAM) / 0.18f).coerceIn(0f, 1f)
            // Before the shear it is hammering on the glass.
            val lunge = if (p < SHEAR) abs(sin(slamK * 9f)) * w * 0.04f else 0f
            val fx = w * 0.5f + lunge * 0.2f
            val fy = MathX.lerp(gantryY + gantryDrop, winB * 0.88f, MathX.smoothStep(fallK))
            val fh = MathX.lerp(h * 0.30f, h * 0.13f, fallK)
            val spin = fallK * 2.2f
            drawFalling(c, d, fx, fy, fh, spin, time, 1f - consumed)

            if (p in SLAM..SHEAR) {
                // Cracks spidering across the glass where it struck.
                val cracks = 9
                for (i in 0 until cracks) {
                    val a = i / cracks.toFloat() * 6.283f
                    val len = w * 0.05f * slamK * (0.5f + rng.nextFloat() * 0.0f + 0.5f * abs(sin(i * 3.1f)))
                    d.line(
                        c, fx, winT + (winB - winT) * 0.42f,
                        fx + kotlin.math.cos(a) * len, winT + (winB - winT) * 0.42f + sin(a) * len,
                        Palette.withAlpha(Palette.TEXT, 0.55f * slamK), MathX.clamp(h * 0.003f, 1f, 3f)
                    )
                }
            }
        }

        // Splash when the pour takes it.
        if (p >= CONSUMED) {
            val k = ((p - CONSUMED) / 0.10f).coerceIn(0f, 1f)
            d.glow(c, pourX, winB * 0.88f, w * (0.2f + k * 0.55f), Smelter.LAVA_HOT, 1.2f * (1f - k * 0.4f))
            for (i in 0 until 18) {
                val a = -0.3f - rng.nextFloat() * 2.5f
                val dist = w * 0.22f * k * (0.4f + rng.nextFloat())
                d.circle(
                    c, pourX + kotlin.math.cos(a) * dist, winB * 0.88f + sin(a) * dist * 0.5f,
                    h * 0.006f, Palette.withAlpha(Smelter.LAVA_HOT, 1f - k)
                )
            }
        }

        // ---- the glass and the room you are standing in --------------------
        d.rect(c, winL, winT, winR, winB, Palette.withAlpha(Palette.ACCENT, 0.05f))
        for (i in 0 until 3) {
            val mx = MathX.lerp(winL, winR, (i + 1) / 4f)
            d.rect(c, mx - w * 0.006f, winT, mx + w * 0.006f, winB, Palette.mix(Palette.WALL, Palette.VOID, 0.2f))
        }
        // Window frame.
        val frame = h * 0.028f
        d.rect(c, winL - frame, winT - frame, winR + frame, winT, Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.4f))
        d.rect(c, winL - frame, winB, winR + frame, winB + frame, Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.4f))
        d.rect(c, winL - frame, winT - frame, winL, winB + frame, Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.4f))
        d.rect(c, winR, winT - frame, winR + frame, winB + frame, Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.4f))
        Art.floor(c, d, 0f, winB + frame, w, h, w * 0.05f)

        // The player watching, a silhouette against the glass.
        val px = w * 0.22f
        val feetY = h * 0.97f
        val fh = h * 0.20f
        d.ellipse(c, px, feetY, fh * 0.16f, fh * 0.04f, Palette.withAlpha(Palette.VOID, 0.6f))
        d.line(c, px - fh * 0.05f, feetY - fh * 0.45f, px - fh * 0.07f, feetY, Palette.PLAYER_SHADE, fh * 0.08f)
        d.line(c, px + fh * 0.05f, feetY - fh * 0.45f, px + fh * 0.07f, feetY, Palette.PLAYER, fh * 0.08f)
        d.poly(
            c,
            floatArrayOf(
                px - fh * 0.12f, feetY - fh * 0.80f, px + fh * 0.12f, feetY - fh * 0.80f,
                px + fh * 0.09f, feetY - fh * 0.45f, px - fh * 0.09f, feetY - fh * 0.45f
            ),
            Palette.PLAYER
        )
        d.circle(c, px, feetY - fh * 0.90f, fh * 0.13f, Palette.PLAYER)
        d.glow(c, px, feetY - fh * 0.9f, fh * 2.2f, Smelter.LAVA_GLOW, 0.25f)

        // Heat wash, then white, then out.
        if (p >= CONSUMED) {
            val k = ((p - CONSUMED) / 0.07f).coerceIn(0f, 1f)
            d.rect(c, 0f, 0f, w, h, Palette.withAlpha(Smelter.LAVA_HOT, 0.55f * k * (1f - k)))
        }
        if (p >= FADE) {
            val k = ((p - FADE) / (1f - FADE)).coerceIn(0f, 1f)
            d.rect(c, 0f, 0f, w, h, Palette.withAlpha(Palette.VOID, k))
        }
    }

    /** It falls without flailing, and it keeps its eyes on the window. */
    private fun drawFalling(c: Canvas, d: Draw, cx: Float, feetY: Float, hPx: Float, spin: Float, time: Float, alpha: Float) {
        val tilt = sin(spin) * 0.35f
        val hipY = feetY - hPx * 0.46f
        val shoulderY = feetY - hPx * 0.86f
        val lean = tilt * hPx * 0.25f
        val body = Palette.withAlpha(Monster.PLAYERR_BODY, alpha)

        d.glow(c, cx, feetY - hPx * 0.5f, hPx * 0.8f, Monster.PLAYERR_SCAR, 0.5f * alpha)
        d.line(c, cx + lean * 0.5f, hipY, cx - hPx * 0.16f + lean, feetY, body, hPx * 0.1f)
        d.line(c, cx + lean * 0.5f, hipY, cx + hPx * 0.14f + lean, feetY, body, hPx * 0.1f)
        d.poly(
            c,
            floatArrayOf(
                cx - hPx * 0.15f + lean, shoulderY, cx + hPx * 0.15f + lean, shoulderY,
                cx + hPx * 0.09f + lean * 0.5f, hipY, cx - hPx * 0.09f + lean * 0.5f, hipY
            ),
            body
        )
        // Arms reaching back up toward the gantry it fell from.
        d.line(c, cx - hPx * 0.14f + lean, shoulderY, cx - hPx * 0.34f, shoulderY - hPx * 0.26f, body, hPx * 0.07f)
        d.line(c, cx + hPx * 0.14f + lean, shoulderY, cx + hPx * 0.32f, shoulderY - hPx * 0.30f, body, hPx * 0.07f)
        d.line(c, cx - hPx * 0.04f + lean, shoulderY + hPx * 0.04f, cx + hPx * 0.03f + lean, hipY,
            Palette.withAlpha(Monster.PLAYERR_SCAR, 0.9f * alpha), hPx * 0.022f)

        val headR = hPx * 0.12f
        val headY = feetY - hPx * 0.93f
        d.circle(c, cx + lean, headY, headR, body)
        val glowK = 0.7f + 0.3f * sin(time * 7f)
        d.glow(c, cx + lean, headY, headR * 3.4f, Monster.PLAYERR_EYE, 0.85f * glowK * alpha)
        d.circle(c, cx + lean - headR * 0.42f, headY, headR * 0.2f, Palette.withAlpha(Monster.PLAYERR_EYE, alpha))
        d.circle(c, cx + lean + headR * 0.42f, headY, headR * 0.2f, Palette.withAlpha(Monster.PLAYERR_EYE, alpha))
    }

    companion object {
        const val SLAM = 0.22f
        const val SHEAR = 0.45f
        const val FALL = 0.52f
        const val CONSUMED = 0.78f
        const val FADE = 0.90f
    }
}
