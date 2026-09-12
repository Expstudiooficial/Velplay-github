package com.expstudio.facilitycore

import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.audio.Synth
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Measures the monster's voice rather than trusting it.
 *
 * The old scream was a 620 Hz sawtooth vowel held for a second and a half —
 * which is a person going "aaah", and reads as comedy. These are the properties
 * that stop it being that: the weight sits low, it does not hold, and it is not
 * a clean pitch. If a future change puts the vowel back, this fails.
 */
class MonsterVoiceTest {

    private val sfx = Sfx()

    private fun samples(id: Sfx.Id): FloatArray {
        val s = sfx.pcmForTest(id)
        return FloatArray(s.size) { s[it] / 32767f }
    }

    /** Energy-weighted mean of the zero-crossing rate: a cheap brightness proxy. */
    private fun crossingRate(b: FloatArray): Float {
        var crossings = 0
        for (i in 1 until b.size) if ((b[i - 1] < 0f) != (b[i] < 0f)) crossings++
        return crossings.toFloat() * Synth.RATE / b.size / 2f
    }

    private fun rmsOf(b: FloatArray, from: Int, to: Int): Float {
        var sum = 0.0
        for (i in from until to) sum += (b[i] * b[i]).toDouble()
        return sqrt(sum / (to - from)).toFloat()
    }

    @Test
    fun theRoarSitsLowRatherThanInAVoice() {
        val b = samples(Sfx.Id.SCREAM)
        val rate = crossingRate(b)
        // A held vowel at 620 Hz crosses zero about 1200 times a second. The
        // roar's fundamental starts at 118 Hz and falls from there.
        assertTrue("the roar is too bright to be a roar (%.0f Hz equivalent)".format(rate), rate < 700f)
    }

    @Test
    fun theRoarDoesNotHold() {
        val b = samples(Sfx.Id.SCREAM)
        val third = b.size / 3
        val head = rmsOf(b, 0, third)
        val tail = rmsOf(b, third * 2, b.size)
        // It has to come apart. A sustain that ends where it started is the
        // thing that gets annoying on the fourth hearing.
        assertTrue(
            "the roar sustains instead of collapsing (head %.3f, tail %.3f)".format(head, tail),
            tail < head * 0.72f
        )
    }

    @Test
    fun theRoarIsNotACleanPitch() {
        val b = samples(Sfx.Id.SCREAM)
        // Autocorrelation at the lags a musical note would sit at. A strongly
        // periodic signal scores near 1; anything textured scores much lower.
        var best = 0f
        val window = minOf(b.size, Synth.RATE / 2)
        for (lag in (Synth.RATE / 400) until (Synth.RATE / 60)) {
            var num = 0.0
            var den = 0.0
            var i = 0
            while (i + lag < window) {
                num += (b[i] * b[i + lag]).toDouble()
                den += (b[i] * b[i]).toDouble()
                i++
            }
            if (den > 0.0) {
                val score = (num / den).toFloat()
                if (score > best) best = score
            }
        }
        assertTrue("the roar is too periodic to be anything but a note (%.2f)".format(best), best < 0.92f)
    }

    @Test
    fun everyMonsterSoundIsAudibleAndClean() {
        for (id in listOf(Sfx.Id.SCREAM, Sfx.Id.SNARL, Sfx.Id.GROAN, Sfx.Id.BREATH, Sfx.Id.WHISPER)) {
            val b = samples(id)
            var peak = 0f
            for (v in b) peak = maxOf(peak, abs(v))
            assertTrue("$id is silent", peak > 0.2f)
            assertTrue("$id clips", peak <= 1.0f)
            // No click at either end: a hard edge on a loud sound is a pop.
            assertTrue("$id starts with a click", abs(b.first()) < 0.05f)
            assertTrue("$id ends with a click", abs(b.last()) < 0.05f)
        }
    }

    /**
     * The jumpscare hit is an impact, not a violin shriek.
     *
     * It used to be two detuned 1.4 kHz saws falling together — the most worn
     * out sound in the genre, and shrill enough on a phone to be unpleasant
     * rather than frightening.
     */
    @Test
    fun theStingerHitsRatherThanShrieks() {
        val b = samples(Sfx.Id.STINGER)
        val rate = crossingRate(b)
        assertTrue("the sting is a shriek again (%.0f Hz equivalent)".format(rate), rate < 450f)
        val third = b.size / 3
        assertTrue(
            "the sting rings on instead of hitting",
            rmsOf(b, third * 2, b.size) < rmsOf(b, 0, third) * 0.5f
        )
    }

    /** Repeats must not be identical, or the chase becomes a metronome. */
    @Test
    fun theRoarIsPitchedDifferentlyEachTime() {
        val m = com.expstudio.facilitycore.game.Monster()
        val pitches = ArrayList<Float>()
        repeat(6) {
            m.update(9f)          // run the cooldown out
            if (m.wantsScream()) pitches.add(m.screamPitch())
        }
        assertTrue("the roar never fired", pitches.size >= 4)
        assertTrue("every roar is pitched the same", pitches.toSet().size >= 3)
    }
}
