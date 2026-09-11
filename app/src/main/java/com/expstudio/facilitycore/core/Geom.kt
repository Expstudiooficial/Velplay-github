package com.expstudio.facilitycore.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * World space is measured in metres with y pointing down, matching the canvas
 * axes so rendering never has to flip anything.
 */
class Vec2(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f) {
    fun set(nx: Float, ny: Float): Vec2 { x = nx; y = ny; return this }
    fun set(o: Vec2): Vec2 { x = o.x; y = o.y; return this }
    fun add(dx: Float, dy: Float): Vec2 { x += dx; y += dy; return this }
    fun length(): Float = sqrt(x * x + y * y)
    fun copy(): Vec2 = Vec2(x, y)
}

/** Axis aligned box. [r] and [b] are exclusive edges. */
class Box(
    @JvmField var l: Float,
    @JvmField var t: Float,
    @JvmField var r: Float,
    @JvmField var b: Float
) {
    val w: Float get() = r - l
    val h: Float get() = b - t
    val cx: Float get() = (l + r) * 0.5f
    val cy: Float get() = (t + b) * 0.5f

    fun set(nl: Float, nt: Float, nr: Float, nb: Float): Box {
        l = nl; t = nt; r = nr; b = nb; return this
    }

    fun overlaps(o: Box): Boolean = l < o.r && o.l < r && t < o.b && o.t < b

    fun contains(px: Float, py: Float): Boolean = px in l..r && py in t..b

    /** Shortest distance from a point to this box; 0 when inside. */
    fun distanceTo(px: Float, py: Float): Float {
        val dx = max(max(l - px, 0f), px - r)
        val dy = max(max(t - py, 0f), py - b)
        return sqrt(dx * dx + dy * dy)
    }

    fun inflated(amount: Float): Box = Box(l - amount, t - amount, r + amount, b + amount)

    fun copy(): Box = Box(l, t, r, b)

    companion object {
        /** Builds a box from a corner plus a size, which reads better for level data. */
        fun of(x: Float, y: Float, w: Float, h: Float) = Box(x, y, x + w, y + h)
    }
}

object MathX {
    fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v
    fun clamp(v: Int, lo: Int, hi: Int): Int = if (v < lo) lo else if (v > hi) hi else v
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * clamp(t, 0f, 1f)

    /** Frame-rate independent exponential approach; [rate] is the fraction closed per second. */
    fun approach(current: Float, target: Float, rate: Float, dt: Float): Float {
        val t = 1f - Math.pow((1f - clamp(rate, 0f, 0.9999f)).toDouble(), (dt * 60f).toDouble()).toFloat()
        return lerp(current, target, t)
    }

    fun moveToward(current: Float, target: Float, maxDelta: Float): Float {
        val d = target - current
        return if (abs(d) <= maxDelta) target else current + Math.signum(d) * maxDelta
    }

    /** Smooth 0..1 ramp used for most of the game's animation easing. */
    fun smoothStep(t: Float): Float {
        val c = clamp(t, 0f, 1f)
        return c * c * (3f - 2f * c)
    }

    fun min(a: Float, b: Float): Float = if (a < b) a else b
}
