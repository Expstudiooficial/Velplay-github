package com.expstudio.facilitycore.core

import android.graphics.Canvas
import kotlin.math.sin
import kotlin.random.Random

/**
 * A fixed-capacity particle pool. Everything is stored in parallel float arrays
 * and reused, so a busy scene never allocates and never triggers a collection
 * mid-frame.
 */
class Particles(private val capacity: Int = 320) {

    enum class Kind { SPARK, EMBER, STEAM, DEBRIS, DUST, GLOW }

    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val size = FloatArray(capacity)
    private val spin = FloatArray(capacity)
    private val color = IntArray(capacity)
    private val kind = arrayOfNulls<Kind>(capacity)
    private val drag = FloatArray(capacity)
    private val gravity = FloatArray(capacity)

    private var next = 0
    private val rng = Random(0xC0FFEE)

    var enabled = true

    fun clear() {
        for (i in 0 until capacity) life[i] = 0f
    }

    private fun alloc(): Int {
        // Round-robin: the oldest particle is the one worth stealing.
        var best = next
        var bestLife = Float.MAX_VALUE
        for (probe in 0 until 8) {
            val i = (next + probe) % capacity
            if (life[i] <= 0f) { best = i; break }
            if (life[i] < bestLife) { bestLife = life[i]; best = i }
        }
        next = (best + 1) % capacity
        return best
    }

    fun spawn(
        kind: Kind,
        px: Float,
        py: Float,
        pvx: Float,
        pvy: Float,
        seconds: Float,
        radius: Float,
        tint: Int,
        gravityScale: Float = 1f,
        dragScale: Float = 1f
    ) {
        if (!enabled) return
        val i = alloc()
        this.kind[i] = kind
        x[i] = px; y[i] = py
        vx[i] = pvx; vy[i] = pvy
        life[i] = seconds; maxLife[i] = seconds
        size[i] = radius
        color[i] = tint
        spin[i] = rng.nextFloat() * 6.283f
        gravity[i] = gravityScale
        drag[i] = dragScale
    }

    /** A cone of sparks, as thrown by a contact or a shorting cable. */
    fun sparkBurst(px: Float, py: Float, count: Int, spread: Float, speed: Float, tint: Int = Palette.WARN) {
        if (!enabled) return
        repeat(count) {
            val angle = (rng.nextFloat() * 2f - 1f) * spread - 1.57f
            val v = speed * (0.4f + rng.nextFloat() * 0.9f)
            spawn(
                Kind.SPARK, px, py,
                kotlin.math.cos(angle) * v, kotlin.math.sin(angle) * v,
                0.25f + rng.nextFloat() * 0.45f,
                0.025f + rng.nextFloat() * 0.03f,
                tint, gravityScale = 1.4f, dragScale = 0.6f
            )
        }
    }

    fun embers(px: Float, py: Float, count: Int, tint: Int = Palette.WARN) {
        if (!enabled) return
        repeat(count) {
            spawn(
                Kind.EMBER, px + (rng.nextFloat() - 0.5f) * 0.8f, py,
                (rng.nextFloat() - 0.5f) * 0.5f, -0.5f - rng.nextFloat() * 1.2f,
                1.4f + rng.nextFloat() * 2.2f,
                0.02f + rng.nextFloat() * 0.035f,
                tint, gravityScale = -0.12f, dragScale = 0.35f
            )
        }
    }

    fun steam(px: Float, py: Float, count: Int, tint: Int = Palette.TEXT_DIM) {
        if (!enabled) return
        repeat(count) {
            spawn(
                Kind.STEAM, px + (rng.nextFloat() - 0.5f) * 0.5f, py,
                (rng.nextFloat() - 0.5f) * 0.35f, -0.7f - rng.nextFloat() * 0.6f,
                1.1f + rng.nextFloat() * 1.3f,
                0.16f + rng.nextFloat() * 0.22f,
                tint, gravityScale = -0.05f, dragScale = 0.5f
            )
        }
    }

    fun debris(px: Float, py: Float, count: Int, speed: Float, tint: Int) {
        if (!enabled) return
        repeat(count) {
            val angle = rng.nextFloat() * 6.283f
            val v = speed * (0.3f + rng.nextFloat())
            spawn(
                Kind.DEBRIS, px, py,
                kotlin.math.cos(angle) * v, kotlin.math.sin(angle) * v - speed * 0.4f,
                0.7f + rng.nextFloat() * 1.1f,
                0.04f + rng.nextFloat() * 0.07f,
                tint, gravityScale = 1.6f, dragScale = 0.4f
            )
        }
    }

    fun update(dt: Float) {
        for (i in 0 until capacity) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            if (life[i] <= 0f) continue
            vy[i] += 9.2f * gravity[i] * dt
            val d = 1f - (drag[i] * 1.6f * dt).coerceIn(0f, 0.9f)
            vx[i] *= d
            vy[i] *= d
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            spin[i] += dt * 3f
        }
    }

    fun draw(c: Canvas, d: Draw, cam: Camera) {
        for (i in 0 until capacity) {
            if (life[i] <= 0f) continue
            val k = kind[i] ?: continue
            val t = (life[i] / maxLife[i]).coerceIn(0f, 1f)
            val sx = cam.sx(x[i])
            val sy = cam.sy(y[i])
            when (k) {
                Kind.SPARK -> {
                    // Streak along travel; a dot reads as a bug, a streak reads as a spark.
                    val tail = 0.045f
                    d.line(
                        c, sx - vx[i] * cam.scale * tail, sy - vy[i] * cam.scale * tail, sx, sy,
                        Palette.withAlpha(color[i], t), cam.s(size[i]) * 1.4f
                    )
                    d.circle(c, sx, sy, cam.s(size[i]) * 0.8f, Palette.withAlpha(Palette.TEXT, t * 0.85f))
                }
                Kind.EMBER -> {
                    val flicker = 0.6f + 0.4f * sin(spin[i] * 4f)
                    d.glow(c, sx, sy, cam.s(size[i]) * 5f, color[i], t * 0.8f * flicker)
                    d.circle(c, sx, sy, cam.s(size[i]), Palette.withAlpha(color[i], t * flicker))
                }
                Kind.STEAM -> {
                    val grow = 1f + (1f - t) * 1.8f
                    d.circle(c, sx, sy, cam.s(size[i]) * grow, Palette.withAlpha(color[i], t * 0.13f))
                }
                Kind.DEBRIS -> {
                    val r = cam.s(size[i])
                    d.poly(
                        c,
                        floatArrayOf(
                            sx - r, sy - r * 0.6f,
                            sx + r * 0.8f, sy - r,
                            sx + r, sy + r * 0.7f,
                            sx - r * 0.7f, sy + r
                        ),
                        Palette.withAlpha(color[i], t)
                    )
                }
                Kind.DUST -> d.circle(c, sx, sy, cam.s(size[i]), Palette.withAlpha(color[i], t * 0.3f))
                Kind.GLOW -> d.glow(c, sx, sy, cam.s(size[i]) * 4f, color[i], t)
            }
        }
    }
}
