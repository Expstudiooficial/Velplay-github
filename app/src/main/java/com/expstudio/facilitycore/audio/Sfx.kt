package com.expstudio.facilitycore.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.expstudio.facilitycore.audio.Synth.RATE
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every sound in the game, synthesised at startup — the APK ships no audio
 * assets.
 *
 * The brief is dread first, violence second: a low oppressive bed with almost
 * nothing in it, so that when something does happen it lands hard. Impacts
 * carry sub-bass you feel rather than hear, the pursuit sounds are throat-like
 * rather than musical, and the heartbeat is driven by the game so the room gets
 * louder as it gets worse.
 *
 * All playback is wrapped defensively: audio is a nicety and must never be able
 * to take the game down on an unusual device.
 */
class Sfx {

    enum class Id {
        // Interface
        CLICK, CONFIRM, DENY, CHIME,
        // Interaction
        PICKUP, CONNECT, UNLOCK, POWER, CUBE_SEAT,
        // Physical
        THUD, STEP, CLANK, DRIP, GROAN, DEBRIS, SHUTTER,
        // The things down there
        SCREAM, SNARL, WHISPER, BREATH, HEARTBEAT, STINGER, RISER,
        // Big events
        RUMBLE, IMPACT, ALARM
    }

    var enabled = true
    var ambienceEnabled = true

    private val tracks = HashMap<Id, AudioTrack>()
    private var ambience: AudioTrack? = null
    private var released = false

    init {
        try {
            build()
        } catch (t: Throwable) {
            tracks.clear()
        }
    }

    private fun build() {
        put(Id.CLICK, ui(760f, 0.045f, 0.16f))
        put(Id.CONFIRM, confirm())
        put(Id.DENY, deny())
        put(Id.CHIME, chime())

        put(Id.PICKUP, pickup())
        put(Id.CONNECT, connect())
        put(Id.UNLOCK, unlock())
        put(Id.POWER, power())
        put(Id.CUBE_SEAT, cubeSeat())

        put(Id.THUD, thud(0.55f, 0.30f))
        put(Id.STEP, step())
        put(Id.CLANK, clank())
        put(Id.DRIP, drip())
        put(Id.GROAN, groan())
        put(Id.DEBRIS, debris())
        put(Id.SHUTTER, shutter())

        put(Id.SCREAM, scream())
        put(Id.SNARL, snarl())
        put(Id.WHISPER, whisper())
        put(Id.BREATH, breath())
        put(Id.HEARTBEAT, heartbeat())
        put(Id.STINGER, stinger())
        put(Id.RISER, riser())

        put(Id.RUMBLE, rumble())
        put(Id.IMPACT, impact())
        put(Id.ALARM, alarm())
    }

    private fun put(id: Id, samples: ShortArray) {
        tracks[id] = track(samples)
    }

    // ---- playback --------------------------------------------------------

    fun play(id: Id, volume: Float = 1f) {
        if (!enabled || released) return
        val t = tracks[id] ?: return
        try {
            if (t.state != AudioTrack.STATE_INITIALIZED) return
            t.stop()
            t.reloadStaticData()
            t.setVolume(volume.coerceIn(0f, 1f))
            t.play()
        } catch (th: Throwable) {
            // A busy or reclaimed track just means this cue is skipped.
        }
    }

    /** Pitch-shifts a one-shot by resampling on playback. */
    fun playPitched(id: Id, volume: Float, pitch: Float) {
        if (!enabled || released) return
        val t = tracks[id] ?: return
        try {
            if (t.state != AudioTrack.STATE_INITIALIZED) return
            t.stop()
            t.reloadStaticData()
            t.playbackRate = (RATE * pitch.coerceIn(0.5f, 2f)).toInt()
            t.setVolume(volume.coerceIn(0f, 1f))
            t.play()
        } catch (th: Throwable) {
            // Ignore.
        }
    }

    fun startAmbience() {
        if (!ambienceEnabled || released) return
        try {
            if (ambience == null) {
                val data = ambienceLoop()
                val t = track(data)
                t.setLoopPoints(0, data.size, -1)
                t.setVolume(0.42f)
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

    private fun track(samples: ShortArray): AudioTrack {
        val bytes = samples.size * 2
        val t = AudioTrack.Builder()
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
        t.write(samples, 0, samples.size)
        return t
    }

    // ---- interface -------------------------------------------------------

    private fun ui(freq: Float, seconds: Float, amp: Float): ShortArray {
        val b = Synth.buffer(seconds)
        Synth.addTone(b, amp, { freq }, { Synth.expDecay(it, 34f) })
        Synth.addTone(b, amp * 0.4f, { freq * 2.01f }, { Synth.expDecay(it, 48f) })
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun confirm(): ShortArray {
        val b = Synth.buffer(0.42f)
        // A rising fifth, but dull and machine-like rather than musical.
        Synth.addTone(b, 0.20f, { 320f + 180f * it }, { Synth.expDecay(it, 8f) })
        Synth.addTone(b, 0.11f, { 481f + 270f * it }, { Synth.expDecay(it, 9f) })
        Synth.addNoise(b, 0.05f, 11) { Synth.expDecay(it, 30f) }
        Synth.lowPass(b, { 2600f }, 0.2f)
        Synth.room(b, 0.05f, 0.30f, 0.25f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun deny(): ShortArray {
        val b = Synth.buffer(0.40f)
        // A dead buzz. Two close frequencies beating against each other.
        Synth.addTone(b, 0.22f, { 96f }, { Synth.adsr(it, b.size, 0.004f, 0.1f, 0.6f, 0.12f) }) { Synth.square(it) }
        Synth.addTone(b, 0.16f, { 101f }, { Synth.adsr(it, b.size, 0.004f, 0.1f, 0.6f, 0.12f) }) { Synth.square(it) }
        Synth.lowPass(b, { 900f }, 0.35f)
        Synth.saturate(b, 2.2f)
        Synth.normalise(b, 0.44f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun chime(): ShortArray {
        val b = Synth.buffer(0.85f)
        Synth.addTone(b, 0.13f, { 1180f }, { Synth.expDecay(it, 5.5f) })
        Synth.addTone(b, 0.07f, { 1771f }, { Synth.expDecay(it, 7f) })
        Synth.addTone(b, 0.04f, { 2360f }, { Synth.expDecay(it, 9f) })
        Synth.room(b, 0.09f, 0.36f, 0.4f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    // ---- interaction -----------------------------------------------------

    private fun pickup(): ShortArray {
        val b = Synth.buffer(0.30f)
        Synth.addTone(b, 0.15f, { 380f + 520f * it }, { Synth.expDecay(it, 16f) })
        Synth.addNoise(b, 0.09f, 3) { Synth.expDecay(it, 40f) }
        Synth.lowPass(b, { 3200f }, 0.25f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun connect(): ShortArray {
        val b = Synth.buffer(0.34f)
        // Contact: a hard click, then the fizz of current finding a path.
        Synth.addNoise(b, 0.34f, 7) { Synth.expDecay(it, 90f) }
        Synth.addNoise(b, 0.12f, 8) { i -> Synth.expDecay(i, 11f) * (0.4f + 0.6f * Random(i).nextFloat()) }
        Synth.addTone(b, 0.13f, { 128f }, { Synth.expDecay(it, 16f) }) { Synth.square(it) }
        Synth.lowPass(b, { t -> 5200f - 3600f * t }, 0.3f)
        Synth.saturate(b, 1.8f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun unlock(): ShortArray {
        val b = Synth.buffer(1.1f)
        // Heavy bolts withdrawing, one after another.
        for (k in 0 until 3) {
            val at = (0.06f + k * 0.14f) * RATE
            Synth.addNoise(b, 0.26f, 20 + k) { i ->
                val d = i - at
                if (d < 0) 0f else Synth.expDecay(d.toInt(), 55f)
            }
        }
        Synth.addTone(b, 0.22f, { 62f - 14f * it }, { Synth.expDecay(it, 3.2f) })
        Synth.addTone(b, 0.10f, { 150f }, { Synth.expDecay(it, 5f) }) { Synth.saw(it) }
        Synth.lowPass(b, { t -> 1500f - 700f * t }, 0.3f)
        Synth.room(b, 0.11f, 0.4f, 0.45f)
        Synth.normalise(b, 0.62f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun power(): ShortArray {
        val b = Synth.buffer(1.9f)
        // Something enormous spinning up, felt in the chest.
        Synth.addTone(b, 0.40f, { t -> 26f + 88f * t * t }, { Synth.adsr(it, b.size, 0.5f, 0.4f, 0.75f, 0.7f) })
        Synth.addTone(b, 0.16f, { t -> 52f + 176f * t * t }, { Synth.adsr(it, b.size, 0.6f, 0.4f, 0.6f, 0.7f) }) { Synth.saw(it) }
        Synth.addNoise(b, 0.10f, 31) { Synth.adsr(it, b.size, 0.7f, 0.3f, 0.5f, 0.8f) }
        Synth.lowPass(b, { t -> 400f + 2600f * t }, 0.35f)
        Synth.saturate(b, 1.6f)
        Synth.normalise(b, 0.72f)
        Synth.deClick(b, 0.02f)
        return Synth.toPcm(b)
    }

    private fun cubeSeat(): ShortArray {
        val b = Synth.buffer(0.7f)
        Synth.addNoise(b, 0.30f, 44) { Synth.expDecay(it, 70f) }
        Synth.addTone(b, 0.26f, { 74f }, { Synth.expDecay(it, 7f) })
        Synth.addTone(b, 0.10f, { t -> 600f + 700f * t }, { Synth.expDecay(it, 6f) })
        Synth.lowPass(b, { 2200f }, 0.3f)
        Synth.room(b, 0.07f, 0.32f, 0.3f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    // ---- physical --------------------------------------------------------

    private fun thud(amp: Float, seconds: Float): ShortArray {
        val b = Synth.buffer(seconds)
        // Sub-bass body plus the crack of the surface.
        Synth.addTone(b, amp, { t -> 96f * (1f - 0.62f * t) }, { Synth.expDecay(it, 13f) })
        Synth.addTone(b, amp * 0.45f, { t -> 46f * (1f - 0.4f * t) }, { Synth.expDecay(it, 7f) })
        Synth.addNoise(b, amp * 0.34f, 51) { Synth.expDecay(it, 46f) }
        Synth.lowPass(b, { t -> 1700f - 1200f * t }, 0.28f)
        Synth.saturate(b, 1.5f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun step(): ShortArray {
        val b = Synth.buffer(0.16f)
        Synth.addNoise(b, 0.26f, 63) { Synth.expDecay(it, 90f) }
        Synth.addTone(b, 0.12f, { 120f }, { Synth.expDecay(it, 60f) })
        Synth.lowPass(b, { 1500f }, 0.2f)
        Synth.deClick(b, 0.004f)
        return Synth.toPcm(b)
    }

    private fun clank(): ShortArray {
        val b = Synth.buffer(0.9f)
        // Struck metal: inharmonic partials, long ring.
        val partials = floatArrayOf(1f, 2.76f, 5.40f, 8.93f)
        for ((k, mul) in partials.withIndex()) {
            Synth.addTone(b, 0.16f / (k + 1), { 210f * mul }, { Synth.expDecay(it, 3.4f + k * 1.6f) })
        }
        Synth.addNoise(b, 0.16f, 71) { Synth.expDecay(it, 80f) }
        Synth.room(b, 0.08f, 0.35f, 0.4f)
        Synth.normalise(b, 0.5f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun drip(): ShortArray {
        val b = Synth.buffer(0.5f)
        Synth.addTone(b, 0.16f, { t -> 900f + 1500f * t }, { Synth.expDecay(it, 30f) })
        Synth.room(b, 0.13f, 0.42f, 0.55f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun groan(): ShortArray {
        val b = Synth.buffer(2.6f)
        // Structural metal under load. Slow, creaking, unhurried.
        Synth.addTone(b, 0.26f, { t -> 58f + 22f * sin(t * 5.0).toFloat() },
            { Synth.adsr(it, b.size, 0.6f, 0.5f, 0.7f, 1.1f) }) { Synth.saw(it) }
        Synth.addTone(b, 0.13f, { t -> 173f + 60f * sin(t * 3.3).toFloat() },
            { Synth.adsr(it, b.size, 0.8f, 0.4f, 0.5f, 1.2f) }) { Synth.saw(it) }
        Synth.addNoise(b, 0.06f, 83) { Synth.adsr(it, b.size, 0.9f, 0.3f, 0.4f, 1.2f) }
        Synth.lowPass(b, { t -> 700f + 400f * sin(t * 9.0).toFloat() }, 0.62f)
        Synth.tremolo(b, 6.5f, 0.35f)
        Synth.room(b, 0.14f, 0.4f, 0.5f)
        Synth.normalise(b, 0.42f)
        Synth.deClick(b, 0.05f)
        return Synth.toPcm(b)
    }

    private fun debris(): ShortArray {
        val b = Synth.buffer(1.3f)
        val rng = Random(97)
        repeat(16) {
            val at = (rng.nextFloat() * 0.9f * RATE).toInt()
            Synth.addNoise(b, 0.10f, 100 + it) { i ->
                val d = i - at
                if (d < 0) 0f else Synth.expDecay(d, 70f)
            }
        }
        Synth.lowPass(b, { 2800f }, 0.3f)
        Synth.room(b, 0.1f, 0.34f, 0.35f)
        Synth.normalise(b, 0.5f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun shutter(): ShortArray {
        val b = Synth.buffer(1.5f)
        // A heavy door running down its track, then slamming home.
        Synth.addNoise(b, 0.16f, 120) { i -> Synth.ramp(i, b.size, 0.6f) * (1f - Synth.ramp(i, b.size, 8f)) }
        Synth.lowPass(b, { t -> 500f + 1400f * t }, 0.4f)
        val slamAt = (0.82f * b.size).toInt()
        Synth.addTone(b, 0.55f, { 70f }, { i -> if (i < slamAt) 0f else Synth.expDecay(i - slamAt, 11f) })
        Synth.addNoise(b, 0.34f, 121) { i -> if (i < slamAt) 0f else Synth.expDecay(i - slamAt, 40f) }
        Synth.saturate(b, 1.6f)
        Synth.normalise(b, 0.78f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    // ---- the things down there -------------------------------------------

    /**
     * Not a musical scream. A formant-shaped shriek with unstable pitch, a
     * second detuned voice underneath it, and enough drive to tear.
     */
    private fun scream(): ShortArray {
        val b = Synth.buffer(1.6f)
        val jitter = Random(9)
        var wobble = 0f
        val jitterTable = FloatArray(b.size)
        for (i in jitterTable.indices) {
            if (i % 220 == 0) wobble = (jitter.nextFloat() * 2f - 1f) * 70f
            jitterTable[i] = wobble
        }

        fun freqAt(t: Float, base: Float): Float {
            val i = (t * (b.size - 1)).toInt().coerceIn(0, b.size - 1)
            // Falls away as it runs out of breath, and never sits still.
            return base * (1f - 0.42f * t) + jitterTable[i] + 26f * sin(t * 58.0).toFloat()
        }

        Synth.addTone(b, 0.34f, { freqAt(it, 620f) }, { Synth.adsr(it, b.size, 0.03f, 0.2f, 0.8f, 0.7f) }) { Synth.saw(it) }
        Synth.addTone(b, 0.20f, { freqAt(it, 934f) }, { Synth.adsr(it, b.size, 0.05f, 0.2f, 0.7f, 0.8f) }) { Synth.saw(it) }
        Synth.addTone(b, 0.14f, { freqAt(it, 311f) }, { Synth.adsr(it, b.size, 0.02f, 0.3f, 0.8f, 0.6f) }) { Synth.square(it, 0.32) }
        Synth.addNoise(b, 0.20f, 13) { Synth.adsr(it, b.size, 0.04f, 0.25f, 0.6f, 0.8f) }
        // Vocal-tract formants: this is what makes it read as a throat.
        Synth.lowPass(b, { t -> 2400f - 900f * t }, 0.55f)
        Synth.highPass(b, 140f)
        Synth.saturate(b, 3.4f)
        Synth.room(b, 0.12f, 0.42f, 0.5f)
        Synth.normalise(b, 0.95f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun snarl(): ShortArray {
        val b = Synth.buffer(0.9f)
        Synth.addNoise(b, 0.5f, 17) { Synth.adsr(it, b.size, 0.05f, 0.2f, 0.7f, 0.4f) }
        // Amplitude modulation at growl rate gives it a rasp.
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            b[i] *= 0.55f + 0.45f * sin(2.0 * PI * 42.0 * t).toFloat()
        }
        Synth.addTone(b, 0.22f, { t -> 74f - 18f * t }, { Synth.adsr(it, b.size, 0.04f, 0.2f, 0.8f, 0.4f) }) { Synth.saw(it) }
        Synth.lowPass(b, { t -> 1100f - 500f * t }, 0.6f)
        Synth.saturate(b, 2.6f)
        Synth.normalise(b, 0.72f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun whisper(): ShortArray {
        val b = Synth.buffer(1.7f)
        // Breathy noise pushed through moving formants: almost words.
        Synth.addNoise(b, 0.5f, 23) { Synth.adsr(it, b.size, 0.25f, 0.3f, 0.6f, 0.6f) }
        Synth.lowPass(b, { t -> 900f + 1500f * (0.5f + 0.5f * sin(t * 21.0).toFloat()) }, 0.72f)
        Synth.highPass(b, 420f)
        Synth.tremolo(b, 5.5f, 0.5f)
        Synth.room(b, 0.16f, 0.4f, 0.55f)
        Synth.normalise(b, 0.34f)
        Synth.deClick(b, 0.04f)
        return Synth.toPcm(b)
    }

    private fun breath(): ShortArray {
        val b = Synth.buffer(2.0f)
        val half = b.size / 2
        Synth.addNoise(b, 0.4f, 29) { i ->
            if (i < half) Synth.ramp(i, half, 1.4f) * (1f - Synth.ramp(i, half, 6f))
            else Synth.ramp(i - half, half, 0.8f) * (1f - Synth.ramp(i - half, half, 3f))
        }
        Synth.lowPass(b, { t -> 700f + 900f * sin(t * PI.toFloat()) }, 0.5f)
        Synth.highPass(b, 200f)
        Synth.normalise(b, 0.30f)
        Synth.deClick(b, 0.05f)
        return Synth.toPcm(b)
    }

    /** Two beats. The game plays it faster as things get worse. */
    private fun heartbeat(): ShortArray {
        val b = Synth.buffer(1.0f)
        fun beat(at: Float, amp: Float) {
            val start = (at * RATE).toInt()
            Synth.addTone(b, amp, { t -> 54f * (1f - 0.45f * t) }, { i ->
                val d = i - start
                if (d < 0) 0f else Synth.expDecay(d, 17f)
            })
            Synth.addNoise(b, amp * 0.2f, 37 + start) { i ->
                val d = i - start
                if (d < 0) 0f else Synth.expDecay(d, 60f)
            }
        }
        beat(0.02f, 0.62f)
        beat(0.26f, 0.42f)
        Synth.lowPass(b, { 420f }, 0.25f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    /** The jolt under a jump scare. */
    private fun stinger(): ShortArray {
        val b = Synth.buffer(1.4f)
        Synth.addTone(b, 0.5f, { t -> 1400f * (1f - 0.92f * t) }, { Synth.expDecay(it, 3.4f) }) { Synth.saw(it) }
        Synth.addTone(b, 0.34f, { t -> 1409f * (1f - 0.92f * t) }, { Synth.expDecay(it, 3.2f) }) { Synth.saw(it) }
        Synth.addTone(b, 0.42f, { 41f }, { Synth.expDecay(it, 3.8f) })
        Synth.addNoise(b, 0.24f, 59) { Synth.expDecay(it, 12f) }
        Synth.saturate(b, 2.8f)
        Synth.room(b, 0.1f, 0.4f, 0.4f)
        Synth.normalise(b, 0.98f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    /** Rising dread, played under a chase starting. */
    private fun riser(): ShortArray {
        val b = Synth.buffer(2.8f)
        Synth.addTone(b, 0.26f, { t -> 60f + 340f * t * t }, { Synth.ramp(it, b.size, 1.6f) }) { Synth.saw(it) }
        Synth.addTone(b, 0.18f, { t -> 90f + 520f * t * t }, { Synth.ramp(it, b.size, 2.0f) }) { Synth.saw(it) }
        Synth.addNoise(b, 0.16f, 67) { Synth.ramp(it, b.size, 2.4f) }
        Synth.lowPass(b, { t -> 600f + 4200f * t }, 0.45f)
        Synth.saturate(b, 1.9f)
        Synth.normalise(b, 0.78f)
        Synth.deClick(b, 0.03f)
        return Synth.toPcm(b)
    }

    // ---- big events ------------------------------------------------------

    private fun rumble(): ShortArray {
        val b = Synth.buffer(2.6f)
        Synth.addNoise(b, 0.5f, 73) { Synth.adsr(it, b.size, 0.4f, 0.4f, 0.7f, 1.0f) }
        Synth.lowPass(b, { t -> 150f + 90f * sin(t * 7.0).toFloat() }, 0.5f)
        Synth.addTone(b, 0.30f, { t -> 34f + 8f * sin(t * 4.0).toFloat() },
            { Synth.adsr(it, b.size, 0.3f, 0.4f, 0.8f, 1.0f) })
        Synth.saturate(b, 1.7f)
        Synth.normalise(b, 0.66f)
        Synth.deClick(b, 0.04f)
        return Synth.toPcm(b)
    }

    private fun impact(): ShortArray {
        val b = Synth.buffer(2.2f)
        Synth.addTone(b, 0.9f, { t -> 120f * (1f - 0.78f * t) }, { Synth.expDecay(it, 5.5f) })
        Synth.addTone(b, 0.5f, { t -> 38f * (1f - 0.3f * t) }, { Synth.expDecay(it, 2.4f) })
        Synth.addNoise(b, 0.55f, 79) { Synth.expDecay(it, 26f) }
        Synth.lowPass(b, { t -> 2600f - 2200f * t }, 0.35f)
        Synth.saturate(b, 2.4f)
        Synth.room(b, 0.13f, 0.45f, 0.5f)
        Synth.normalise(b, 1f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    private fun alarm(): ShortArray {
        val b = Synth.buffer(2.0f)
        // Two-tone industrial alarm, deliberately harsh.
        Synth.addTone(b, 0.3f, { t -> if ((t * 4f).toInt() % 2 == 0) 480f else 360f },
            { Synth.adsr(it, b.size, 0.02f, 0.1f, 0.85f, 0.3f) }) { Synth.square(it) }
        Synth.lowPass(b, { 1800f }, 0.35f)
        Synth.saturate(b, 1.8f)
        Synth.room(b, 0.14f, 0.4f, 0.45f)
        Synth.normalise(b, 0.55f)
        Synth.deClick(b)
        return Synth.toPcm(b)
    }

    /**
     * The bed. Eight seconds of almost nothing: a pair of detuned sub tones
     * beating slowly against each other, a breath of filtered noise, and the
     * occasional distant event. The seam is silent because everything fades
     * across it.
     */
    private fun ambienceLoop(): ShortArray {
        val seconds = 8f
        val b = Synth.buffer(seconds)
        val n = b.size

        // Detuned sub pair. The beat frequency is what makes a room feel wrong.
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val seam = sin(PI * i / n).toFloat()
            b[i] += (sin(2 * PI * 41.0 * t) * 0.34 + sin(2 * PI * 41.9 * t) * 0.30).toFloat() * seam
            b[i] += (sin(2 * PI * 82.3 * t) * 0.10).toFloat() * seam
        }
        // Air moving somewhere far off.
        val air = Synth.buffer(seconds)
        Synth.addNoise(air, 0.5f, 401) { 1f }
        Synth.lowPass(air, { t -> 260f + 150f * sin(t * 3.0).toFloat() }, 0.4f)
        for (i in 0 until n) b[i] += air[i] * 0.34f * sin(PI * i / n).toFloat()

        // Two distant events per loop, buried low.
        val rng = Random(555)
        repeat(2) { k ->
            val at = ((0.25f + k * 0.4f) * n).toInt()
            val amp = 0.09f + rng.nextFloat() * 0.05f
            Synth.addTone(b, amp, { 60f }, { i ->
                val d = i - at
                if (d < 0) 0f else Synth.expDecay(d, 1.6f) * sin(PI * i / n).toFloat()
            })
        }
        Synth.lowPass(b, { 900f }, 0.2f)
        Synth.normalise(b, 0.55f)
        return Synth.toPcm(b)
    }
}
