package com.expstudio.facilitycore.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every sound is synthesised at startup, so the APK ships no audio assets.
 * All playback is wrapped defensively: audio is a nicety and must never be able
 * to take the game down on an unusual device.
 */
class Sfx {

    enum class Id { CLICK, CONFIRM, DENY, PICKUP, CONNECT, UNLOCK, POWER, THUD, SCREAM, RUMBLE, CHIME }

    var enabled = true
    var ambienceEnabled = true

    private val tracks = HashMap<Id, AudioTrack>()
    private var ambience: AudioTrack? = null
    private var released = false

    init {
        try {
            tracks[Id.CLICK] = build(tone(0.06f, 880f, 880f, 0.22f, decay = 40f))
            tracks[Id.CONFIRM] = build(mix(tone(0.10f, 660f, 990f, 0.25f, decay = 16f), tone(0.16f, 990f, 1320f, 0.18f, decay = 12f)))
            tracks[Id.DENY] = build(tone(0.22f, 180f, 120f, 0.30f, decay = 10f, square = true))
            tracks[Id.PICKUP] = build(tone(0.14f, 520f, 1040f, 0.22f, decay = 14f))
            tracks[Id.CONNECT] = build(mix(tone(0.09f, 320f, 320f, 0.25f, decay = 26f, square = true), noise(0.07f, 0.10f, 30f)))
            tracks[Id.UNLOCK] = build(mix(tone(0.35f, 240f, 480f, 0.22f, decay = 7f), tone(0.45f, 120f, 90f, 0.16f, decay = 5f)))
            tracks[Id.POWER] = build(mix(tone(0.9f, 40f, 220f, 0.28f, decay = 2.2f), noise(0.5f, 0.09f, 5f)))
            tracks[Id.THUD] = build(mix(tone(0.30f, 90f, 45f, 0.34f, decay = 11f), noise(0.12f, 0.16f, 22f)))
            tracks[Id.SCREAM] = build(scream())
            tracks[Id.RUMBLE] = build(mix(noise(1.2f, 0.12f, 1.4f), tone(1.2f, 55f, 38f, 0.20f, decay = 1.6f)))
            tracks[Id.CHIME] = build(mix(tone(0.5f, 1320f, 1320f, 0.14f, decay = 5f), tone(0.6f, 1980f, 1980f, 0.09f, decay = 4f)))
        } catch (t: Throwable) {
            tracks.clear()
        }
    }

    fun play(id: Id, volume: Float = 1f) {
        if (!enabled || released) return
        val track = tracks[id] ?: return
        try {
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            track.stop()
            track.reloadStaticData()
            track.setVolume(volume.coerceIn(0f, 1f))
            track.play()
        } catch (t: Throwable) {
            // Ignore: a busy or reclaimed track just means this cue is skipped.
        }
    }

    fun startAmbience() {
        if (!ambienceEnabled || released) return
        try {
            if (ambience == null) {
                val data = ambienceLoop()
                val t = build(data)
                t.setLoopPoints(0, data.size, -1)
                t.setVolume(0.35f)
                ambience = t
            }
            ambience?.let { if (it.playState != AudioTrack.PLAYSTATE_PLAYING) it.play() }
        } catch (t: Throwable) {
            ambience = null
        }
    }

    fun stopAmbience() {
        try { ambience?.pause() } catch (t: Throwable) { /* already gone */ }
    }

    fun release() {
        released = true
        try { ambience?.stop(); ambience?.release() } catch (t: Throwable) { /* already gone */ }
        ambience = null
        for (t in tracks.values) {
            try { t.stop(); t.release() } catch (th: Throwable) { /* already gone */ }
        }
        tracks.clear()
    }

    // ---- synthesis -------------------------------------------------------

    private fun build(samples: ShortArray): AudioTrack {
        val bytes = samples.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bytes.coerceAtLeast(1024))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    /** Exponentially decaying tone with a linear frequency sweep. */
    private fun tone(
        seconds: Float,
        fromHz: Float,
        toHz: Float,
        amp: Float,
        decay: Float,
        square: Boolean = false
    ): ShortArray {
        val n = (seconds * RATE).toInt().coerceAtLeast(1)
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            val f = fromHz + (toHz - fromHz) * (i.toFloat() / n)
            phase += 2.0 * PI * f / RATE
            val raw = if (square) (if (sin(phase) >= 0) 1.0 else -1.0) else sin(phase)
            val env = exp((-decay * t).toDouble())
            out[i] = (raw * env * amp * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun noise(seconds: Float, amp: Float, decay: Float): ShortArray {
        val n = (seconds * RATE).toInt().coerceAtLeast(1)
        val out = ShortArray(n)
        val rng = Random(1337)
        var last = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            // One-pole low pass keeps the noise from sounding like plain hiss.
            last = last * 0.72 + (rng.nextDouble() * 2.0 - 1.0) * 0.28
            val env = exp((-decay * t).toDouble())
            out[i] = (last * env * amp * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun mix(vararg parts: ShortArray): ShortArray {
        val n = parts.maxOfOrNull { it.size } ?: 0
        val out = ShortArray(n)
        for (i in 0 until n) {
            var acc = 0
            for (p in parts) if (i < p.size) acc += p[i].toInt()
            out[i] = acc.coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Detuned descending shriek layered with a noise burst. */
    private fun scream(): ShortArray {
        val seconds = 1.1f
        val n = (seconds * RATE).toInt()
        val out = ShortArray(n)
        val rng = Random(99)
        var p1 = 0.0
        var p2 = 0.0
        var lastNoise = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            val prog = i.toFloat() / n
            val base = 760f - 430f * prog
            val warble = sin(t * 2.0 * PI * 17.0) * 34.0
            p1 += 2.0 * PI * (base + warble) / RATE
            p2 += 2.0 * PI * (base * 1.51 + warble * 1.7) / RATE
            lastNoise = lastNoise * 0.55 + (rng.nextDouble() * 2.0 - 1.0) * 0.45
            val env = (1.0 - exp((-28.0 * t))) * exp((-2.3 * t))
            val v = (sin(p1) * 0.5 + sin(p2) * 0.28 + lastNoise * 0.34) * env * 0.82
            out[i] = (v * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Seamless low drone: only whole numbers of cycles so the loop never clicks. */
    private fun ambienceLoop(): ShortArray {
        val seconds = 4f
        val n = (seconds * RATE).toInt()
        val out = ShortArray(n)
        val rng = Random(4242)
        var lastNoise = 0.0
        for (i in 0 until n) {
            val phase = i.toDouble() / n * 2.0 * PI
            val drone = sin(phase * 168.0) * 0.30 + sin(phase * 251.0) * 0.16 + sin(phase * 84.0) * 0.22
            lastNoise = lastNoise * 0.94 + (rng.nextDouble() * 2.0 - 1.0) * 0.06
            // Fade the noise bed in and out across the loop so the seam is silent.
            val seam = sin(i.toDouble() / n * PI)
            val v = (drone * 0.4 + lastNoise * 0.55 * seam) * 0.5
            out[i] = (v * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private companion object {
        const val RATE = 22050
    }
}
