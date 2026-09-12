package com.expstudio.facilitycore.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * A small synthesis toolkit. Everything the game plays is built here at
 * startup, so the APK ships no audio files.
 *
 * The palette is deliberately low and dirty: sub-bass that is felt more than
 * heard, resonant filters that make noise sound like a throat, and soft
 * saturation so loud things distort rather than clip.
 */
object Synth {

    const val RATE = 22050

    fun buffer(seconds: Float) = FloatArray((seconds * RATE).toInt().coerceAtLeast(1))

    // ---- envelopes -------------------------------------------------------

    /** Attack/decay/sustain/release, evaluated per sample index. */
    fun adsr(i: Int, total: Int, attack: Float, decay: Float, sustain: Float, release: Float): Float {
        val t = i.toFloat() / RATE
        val len = total.toFloat() / RATE
        val releaseStart = (len - release).coerceAtLeast(0f)
        return when {
            t < attack -> if (attack <= 0f) 1f else t / attack
            t < attack + decay -> {
                val k = if (decay <= 0f) 1f else (t - attack) / decay
                1f - (1f - sustain) * k
            }
            t < releaseStart -> sustain
            else -> {
                val k = if (release <= 0f) 1f else ((t - releaseStart) / release).coerceIn(0f, 1f)
                sustain * (1f - k)
            }
        }.coerceIn(0f, 1f)
    }

    fun expDecay(i: Int, rate: Float): Float = exp(-rate * i.toFloat() / RATE).toFloat()

    /** Rises from 0 to 1 over the whole buffer, curved by [shape]. */
    fun ramp(i: Int, total: Int, shape: Float = 1f): Float =
        (i.toFloat() / total).coerceIn(0f, 1f).pow(shape)

    // ---- oscillators -----------------------------------------------------

    fun sine(phase: Double): Float = sin(phase).toFloat()

    fun saw(phase: Double): Float {
        val p = (phase / (2 * PI)) % 1.0
        return (2.0 * p - 1.0).toFloat()
    }

    fun square(phase: Double, width: Double = 0.5): Float {
        val p = (phase / (2 * PI)) % 1.0
        return if (p < width) 1f else -1f
    }

    /** Adds a partial into [out] with a frequency envelope. */
    inline fun addTone(
        out: FloatArray,
        amp: Float,
        crossinline freqAt: (Float) -> Float,
        crossinline envAt: (Int) -> Float,
        crossinline wave: (Double) -> Float = { sine(it) }
    ) {
        var phase = 0.0
        for (i in out.indices) {
            val f = freqAt(i.toFloat() / out.size)
            phase += 2.0 * PI * f / RATE
            out[i] += wave(phase) * envAt(i) * amp
        }
    }

    // ---- noise and filters -----------------------------------------------

    /** White noise shaped by an envelope. */
    inline fun addNoise(out: FloatArray, amp: Float, seed: Int, crossinline envAt: (Int) -> Float) {
        val rng = Random(seed)
        for (i in out.indices) {
            out[i] += (rng.nextFloat() * 2f - 1f) * envAt(i) * amp
        }
    }

    /**
     * Two-pole resonant low pass. This is what turns flat noise into something
     * that sounds like it is coming out of a body.
     */
    fun lowPass(buf: FloatArray, cutoffAt: (Float) -> Float, resonance: Float) {
        var low = 0f
        var band = 0f
        for (i in buf.indices) {
            val cutoff = cutoffAt(i.toFloat() / buf.size).coerceIn(20f, RATE * 0.45f)
            val f = 2f * sin(PI * cutoff / RATE).toFloat()
            val q = (1f - resonance).coerceIn(0.02f, 1f)
            val high = buf[i] - low - q * band
            band += f * high
            low += f * band
            buf[i] = low
        }
    }

    fun highPass(buf: FloatArray, cutoff: Float) {
        val rc = 1f / (2f * PI.toFloat() * cutoff)
        val dt = 1f / RATE
        val alpha = rc / (rc + dt)
        var prevIn = 0f
        var prevOut = 0f
        for (i in buf.indices) {
            val x = buf[i]
            val y = alpha * (prevOut + x - prevIn)
            prevIn = x
            prevOut = y
            buf[i] = y
        }
    }

    /** Soft saturation: loud material distorts into grit instead of clipping. */
    fun saturate(buf: FloatArray, drive: Float) {
        if (drive <= 1f) return
        for (i in buf.indices) buf[i] = tanh(buf[i] * drive) / tanh(drive)
    }

    /** A short slap of reverb, which is what a concrete floor sounds like. */
    fun room(buf: FloatArray, delaySeconds: Float, feedback: Float, mix: Float, taps: Int = 4) {
        val delay = (delaySeconds * RATE).toInt().coerceAtLeast(1)
        val wet = FloatArray(buf.size)
        for (t in 1..taps) {
            val offset = delay * t
            val gain = feedback.pow(t.toFloat())
            if (offset >= buf.size) break
            for (i in offset until buf.size) wet[i] += buf[i - offset] * gain
        }
        for (i in buf.indices) buf[i] += wet[i] * mix
    }

    /** Slow amplitude wobble; unsettling on sustained material. */
    fun tremolo(buf: FloatArray, hz: Float, depth: Float) {
        for (i in buf.indices) {
            val lfo = 1f - depth * (0.5f + 0.5f * sin(2.0 * PI * hz * i / RATE).toFloat())
            buf[i] *= lfo
        }
    }

    fun normalise(buf: FloatArray, peak: Float) {
        var max = 0f
        for (v in buf) max = kotlin.math.max(max, abs(v))
        if (max <= 0.0001f) return
        val gain = peak / max
        for (i in buf.indices) buf[i] *= gain
    }

    /** Fades both ends so a one-shot never clicks. */
    fun deClick(buf: FloatArray, seconds: Float = 0.006f) {
        val n = (seconds * RATE).toInt().coerceIn(1, buf.size / 2)
        for (i in 0 until n) {
            val k = i.toFloat() / n
            buf[i] *= k
            buf[buf.size - 1 - i] *= k
        }
    }

    fun toPcm(buf: FloatArray): ShortArray {
        val out = ShortArray(buf.size)
        for (i in buf.indices) {
            out[i] = (buf[i] * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}
