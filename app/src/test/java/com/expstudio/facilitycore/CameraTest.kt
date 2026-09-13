package com.expstudio.facilitycore

import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Camera
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The camera has to keep up.
 *
 * Its easing rate is written in units of "fraction closed per second", and it
 * was once written in "fraction closed per frame at 60Hz" — the same number
 * means something eight times slower under the new reading, which is a camera
 * trailing half a room behind the player with no test to notice.
 *
 * Deliberately a test of [Camera.follow] rather than of a session: in a session
 * the camera is clamped inside the room, so a large gap between it and the
 * player is usually the clamp doing its job rather than the easing failing.
 */
class CameraTest {

    private val dt = 1f / 60f

    /** A room far bigger than the view, so nothing is clamped. */
    private val room = Box(-400f, -400f, 400f, 400f)

    private fun camera(): Camera = Camera().apply { resize(1920, 1080) }

    @Test
    fun itSettlesOnAStandingTarget() {
        val c = camera()
        c.follow(60f, -3f, room, dt, snap = true)
        c.follow(0f, 0f, room, dt)      // a target 60 m away, then let it ease in
        var t = 0f
        while (t < 0.8f) { c.follow(0f, 0f, room, dt); t += dt }
        assertTrue(
            "it had not arrived after 0.8s (%.2f, %.2f off)".format(abs(c.x), abs(c.y)),
            abs(c.x) < 0.15f && abs(c.y) < 0.15f
        )
    }

    @Test
    fun itHoldsAConstantLagBehindARunningTarget() {
        val c = camera()
        c.follow(0f, 0f, room, dt, snap = true)
        var target = 0f
        var t = 0f
        var worst = 0f
        while (t < 6f) {
            target += 4.35f * dt          // a player at a flat run
            c.follow(target, 0f, room, dt)
            if (t > 1f) worst = maxOf(worst, abs(c.x - target))
            t += dt
        }
        // At rate r the steady-state lag of a target moving at v is v/r, so a
        // 4.35 m/s run should sit under half a metre behind. At the old number
        // read as per-second it was more than five.
        assertTrue("it trailed %.2f m behind a run".format(worst), worst < 0.6f)
    }
}
