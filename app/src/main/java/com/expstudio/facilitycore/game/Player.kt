package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Camera
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The white figure. Position is the feet: x is the horizontal centre, y is the
 * ground contact point, which makes level authoring and standing checks simple.
 */
class Player {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f

    var facing = 1
        private set
    var onGround = false
        private set
    var crouching = false
        private set

    /** 0 = fully standing, 1 = fully crouched; drives both the hitbox and the pose. */
    var crouchBlend = 0f
        private set

    var height = STAND_HEIGHT
        private set

    /** Frozen during cutscenes and while an overlay puzzle is open. */
    var controlEnabled = true
    var visible = true

    /** Set while the player is hauling the heavy feeder cable. */
    var carrying = false

    /** Adrenaline multiplier; the chase pushes this above 1. */
    var speedScale = 1f

    private var coyote = 0f
    private var jumpBuffer = 0f
    private var walkPhase = 0f
    private var landSquash = 0f
    private var stepTimer = 0f

    /** Consumed by the session to play a footstep; avoids audio code in here. */
    var stepEvent = false
    /** Pulses after a ledge pull-up so the pose can sell the effort. */
    var mantleFlash = 0f
        private set

    fun bounds(): Box = Box(x - WIDTH * 0.5f, y - height, x + WIDTH * 0.5f, y)

    fun boundsAt(px: Float, py: Float, h: Float): Box =
        Box(px - WIDTH * 0.5f, py - h, px + WIDTH * 0.5f, py)

    fun teleport(nx: Float, ny: Float) {
        x = nx; y = ny; vx = 0f; vy = 0f
        onGround = false
        coyote = 0f
        jumpBuffer = 0f
    }

    fun update(
        dt: Float,
        moveX: Float,
        wantCrouch: Boolean,
        jumpPressed: Boolean,
        solids: List<Box>
    ) {
        val input = if (controlEnabled) moveX else 0f
        val crouchWanted = controlEnabled && wantCrouch

        if (jumpPressed && controlEnabled) jumpBuffer = JUMP_BUFFER
        if (jumpBuffer > 0f) jumpBuffer -= dt

        // Standing up is refused while a ceiling would trap us. Shrinking is
        // applied instantly rather than eased: a spawn or a fall into a duct
        // must never leave the taller box overlapping geometry for a frame,
        // which the vertical solver would resolve by flinging us upwards.
        val ceilingBlocked = !canFit(x, y, STAND_HEIGHT, solids)
        val targetHeight = if (crouchWanted || ceilingBlocked) CROUCH_HEIGHT else STAND_HEIGHT
        crouching = targetHeight < STAND_HEIGHT - 0.01f
        height = if (targetHeight < height) targetHeight
        else MathX.moveToward(height, targetHeight, dt * 6.5f)
        crouchBlend = MathX.clamp((STAND_HEIGHT - height) / (STAND_HEIGHT - CROUCH_HEIGHT), 0f, 1f)

        val maxSpeed = when {
            crouching -> CROUCH_SPEED
            carrying -> CARRY_SPEED
            else -> RUN_SPEED
        } * speedScale
        val accel = if (onGround) GROUND_ACCEL else AIR_ACCEL
        val target = input * maxSpeed
        vx = if (abs(target) > 0.01f) {
            MathX.moveToward(vx, target, accel * dt)
        } else {
            MathX.moveToward(vx, 0f, (if (onGround) GROUND_FRICTION else AIR_FRICTION) * dt)
        }
        if (abs(input) > 0.05f) facing = if (input > 0f) 1 else -1

        if (onGround) coyote = COYOTE else coyote -= dt

        val canJump = coyote > 0f && !crouching
        if (jumpBuffer > 0f && canJump) {
            vy = -JUMP_SPEED
            onGround = false
            coyote = 0f
            jumpBuffer = 0f
        }

        // Variable jump height: releasing early cuts the rise short.
        vy += GRAVITY * dt
        if (vy > MAX_FALL) vy = MAX_FALL

        moveAndCollide(dt, solids)
        if (!onGround) tryMantle(input, solids)
        if (mantleFlash > 0f) mantleFlash = (mantleFlash - dt * 4f).coerceAtLeast(0f)

        if (onGround && abs(vx) > 0.35f) {
            val rate = abs(vx) / maxSpeed
            walkPhase += dt * rate * (if (crouching) 7.5f else 11f)
            stepTimer -= dt * rate * (if (crouching) 0.55f else 1f)
            if (stepTimer <= 0f) {
                stepTimer = 0.32f
                stepEvent = true
            }
        } else {
            walkPhase = MathX.approach(walkPhase, 0f, 0.2f, dt)
            stepTimer = 0.05f
        }
        if (landSquash > 0f) landSquash = (landSquash - dt * 4.5f).coerceAtLeast(0f)
    }

    private fun moveAndCollide(dt: Float, solids: List<Box>) {
        // Axis-separated sweep: simple, and impossible to tunnel at these speeds.
        x += vx * dt
        var b = bounds()
        for (s in solids) {
            if (!b.overlaps(s)) continue
            if (vx > 0f) x = s.l - WIDTH * 0.5f - EPS
            else if (vx < 0f) x = s.r + WIDTH * 0.5f + EPS
            else {
                // Pushed into a wall by geometry changes: pop out the shorter way.
                x = if (abs(b.cx - s.l) < abs(b.cx - s.r)) s.l - WIDTH * 0.5f - EPS
                else s.r + WIDTH * 0.5f + EPS
            }
            vx = 0f
            b = bounds()
        }

        val wasFalling = vy > 0f
        y += vy * dt
        onGround = false
        b = bounds()
        for (s in solids) {
            if (!b.overlaps(s)) continue
            if (vy > 0f) {
                y = s.t - EPS
                if (wasFalling && vy > 6f) landSquash = 1f
                onGround = true
            } else if (vy < 0f) {
                y = s.b + height + EPS
            }
            vy = 0f
            b = bounds()
        }

        // A short ground probe keeps the figure glued to floors on small steps.
        if (!onGround && vy >= 0f) {
            val probe = Box(x - WIDTH * 0.5f, y, x + WIDTH * 0.5f, y + GROUND_PROBE)
            for (s in solids) {
                if (probe.overlaps(s) && s.t >= y - EPS) {
                    y = s.t - EPS
                    onGround = true
                    vy = 0f
                    break
                }
            }
        }
    }

    /**
     * Ledge pull-up. Without it, jumping while pressed against a waist-high
     * crate is unwinnable: the horizontal solver zeroes the run-up on contact,
     * so the figure rises straight up and drops back down in place. Anyone who
     * walks into an obstacle and taps JUMP — which is what a player actually
     * does — gets pulled over it instead.
     */
    private fun tryMantle(input: Float, solids: List<Box>): Boolean {
        if (abs(input) < 0.25f) return false
        // Only once the climb is topping out, never on the way up.
        if (vy < -4f) return false
        val dir = if (input > 0f) 1f else -1f
        val b = bounds()

        var ledge: Box? = null
        var ledgeTop = 0f
        for (s in solids) {
            val top = s.t
            // The ledge has to sit just above the feet...
            if (top > y + 0.02f || top < y - MANTLE_RISE) continue
            // ...and be in front of us, or already under our leading edge.
            val gap = if (dir > 0f) s.l - b.r else b.l - s.r
            if (gap > MANTLE_REACH) continue
            if (dir > 0f && s.r < b.l) continue
            if (dir < 0f && s.l > b.r) continue
            if (ledge == null || top > ledgeTop) { ledge = s; ledgeTop = top }
        }
        val target = ledge ?: return false

        val landX = if (dir > 0f) max(x, target.l + WIDTH * 0.55f) else min(x, target.r - WIDTH * 0.55f)
        val landY = ledgeTop - EPS
        val landing = boundsAt(landX, landY, height)
        for (s in solids) if (landing.overlaps(s)) return false

        x = landX
        y = landY
        vy = 0f
        onGround = true
        mantleFlash = 1f
        return true
    }

    private fun canFit(px: Float, py: Float, h: Float, solids: List<Box>): Boolean {
        val b = boundsAt(px, py, h)
        for (s in solids) if (b.overlaps(s)) return false
        return true
    }

    fun draw(c: Canvas, d: Draw, cam: Camera, timeSeconds: Float) {
        if (!visible) return
        val sx = cam.sx(x)
        val feetY = cam.sy(y)
        val hPx = cam.s(height)
        // Landing squash, plus a short pop coming out of a ledge pull-up.
        val squash = 1f - landSquash * 0.14f + mantleFlash * 0.05f
        val bodyTop = feetY - hPx * squash

        // Contact shadow: tight and dark when planted, wide and faint in the air.
        val air = if (onGround) 0f else MathX.clamp(abs(vy) / 9f, 0f, 1f)
        d.ellipse(
            c, sx, feetY + cam.s(0.02f),
            cam.s(0.46f * (1f - crouchBlend * 0.22f)) * (1f + air * 0.5f),
            cam.s(0.13f) * (1f - air * 0.35f),
            Palette.withAlpha(0xFF000000.toInt(), 0.45f * (1f - air * 0.55f))
        )

        // --- skeleton -----------------------------------------------------
        val headR = cam.s(0.225f)
        val headY = bodyTop + headR * 1.02f
        val shoulderY = bodyTop + headR * 2.15f
        val hipY = feetY - hPx * 0.47f * squash
        val shW = cam.s(0.235f)
        val hipW = cam.s(0.165f)
        val legW = cam.s(0.155f)
        val armW = cam.s(0.115f)
        val swing = sin(walkPhase)
        val swing2 = sin(walkPhase + Math.PI.toFloat())
        val stride = cam.s(0.34f) * (if (onGround) 1f else 0.35f)
        // A little daylight between the feet so an idle pose is not one stick.
        val stance = cam.s(0.10f)
        val footL = sx - stance + swing * stride
        val footR = sx + stance + swing2 * stride
        val hipL = sx - hipW * 0.45f
        val hipR = sx + hipW * 0.45f

        val armDrop = (hipY - shoulderY) * 1.30f
        val armSwing = cam.s(0.26f) * (if (onGround) 1f else 0.25f)
        val shoulderL = sx - shW * 0.72f
        val shoulderR = sx + shW * 0.72f
        val handL = shoulderL + swing2 * armSwing
        val handR = shoulderR + swing * armSwing
        val handY = shoulderY + armDrop

        val tilt = facing * cam.s(0.045f)

        // --- rim pass: the same figure, a shade fatter, in a cold tint ------
        // It reads as light wrapping the silhouette and keeps the figure off
        // the near-black backgrounds.
        val rim = Palette.withAlpha(Palette.ACCENT, 0.32f)
        val grow = cam.s(0.035f)
        d.line(c, hipL, hipY, footL, feetY, rim, legW + grow * 2f)
        d.line(c, hipR, hipY, footR, feetY, rim, legW + grow * 2f)
        d.line(c, shoulderL, shoulderY, handL, handY, rim, armW + grow * 2f)
        d.line(c, shoulderR, shoulderY, handR, handY, rim, armW + grow * 2f)
        d.poly(
            c,
            floatArrayOf(
                sx - shW - grow, shoulderY - grow,
                sx + shW + grow, shoulderY - grow,
                sx + hipW + grow, hipY + grow,
                sx - hipW - grow, hipY + grow
            ),
            rim
        )
        d.circle(c, sx + tilt, headY, headR + grow, rim)

        // --- body ----------------------------------------------------------
        // Far limbs first, in shade, so the figure has front and back.
        d.line(c, hipL, hipY, footL, feetY, Palette.PLAYER_SHADE, legW)
        if (!carrying) d.line(c, shoulderL, shoulderY, handL, handY, Palette.PLAYER_SHADE, armW)

        d.poly(
            c,
            floatArrayOf(sx - shW, shoulderY, sx + shW, shoulderY, sx + hipW, hipY, sx - hipW, hipY),
            Palette.PLAYER
        )
        // Shade down the trailing side of the torso.
        d.poly(
            c,
            floatArrayOf(
                sx + shW * 0.3f, shoulderY,
                sx + shW, shoulderY,
                sx + hipW, hipY,
                sx + hipW * 0.3f, hipY
            ),
            Palette.withAlpha(Palette.PLAYER_SHADE, 0.7f)
        )
        // Collar line: a single detail that gives the torso a top.
        d.line(
            c, sx - shW * 0.8f, shoulderY + cam.s(0.05f), sx + shW * 0.8f, shoulderY + cam.s(0.05f),
            Palette.withAlpha(Palette.BG_FAR, 0.30f), cam.s(0.035f)
        )

        // Near limbs on top.
        d.line(c, hipR, hipY, footR, feetY, Palette.PLAYER, legW)
        if (carrying) {
            // Both arms forward, taking the weight of the coil.
            val reachX = sx + facing * cam.s(0.44f)
            d.line(c, shoulderL, shoulderY, reachX, shoulderY + cam.s(0.30f), Palette.PLAYER_SHADE, armW)
            d.line(c, shoulderR, shoulderY, reachX, shoulderY + cam.s(0.22f), Palette.PLAYER, armW)
        } else {
            d.line(c, shoulderR, shoulderY, handR, handY, Palette.PLAYER, armW)
        }

        // --- head -----------------------------------------------------------
        d.glow(c, sx + tilt, headY, headR * 3.0f, Palette.PLAYER, 0.30f)
        d.circle(c, sx + tilt, headY, headR, Palette.PLAYER)
        // Terminator across the head, away from the facing direction.
        d.circle(c, sx + tilt - facing * headR * 0.34f, headY + headR * 0.14f, headR * 0.80f,
            Palette.withAlpha(Palette.PLAYER_SHADE, 0.50f))
        d.circle(c, sx + tilt + facing * headR * 0.36f, headY - headR * 0.10f, headR * 0.20f,
            Palette.withAlpha(Palette.BG_FAR, 0.55f))

        // Breathing shimmer: tiny, but it stops the idle pose looking frozen.
        val idle = (sin(timeSeconds * 2.1f) * 0.5f + 0.5f) * 0.08f
        d.circle(c, sx + tilt, headY, headR * (1.02f + idle * 0.05f), Palette.withAlpha(Palette.PLAYER, 0.10f))
    }

    /** Hand position used when throwing or connecting cables. */
    fun handX(): Float = x + facing * 0.35f
    fun handY(): Float = y - height * 0.62f

    companion object {
        const val WIDTH = 0.62f
        const val STAND_HEIGHT = 1.72f
        const val CROUCH_HEIGHT = 0.92f
        const val RUN_SPEED = 4.35f
        const val CROUCH_SPEED = 1.85f
        const val CARRY_SPEED = 2.75f
        const val GROUND_ACCEL = 34f
        const val AIR_ACCEL = 18f
        const val GROUND_FRICTION = 30f
        const val AIR_FRICTION = 7f
        const val GRAVITY = 27f
        const val JUMP_SPEED = 9.2f
        const val MAX_FALL = 21f
        const val COYOTE = 0.11f
        const val JUMP_BUFFER = 0.12f
        const val GROUND_PROBE = 0.10f
        /** How far above the feet a ledge can be and still be pulled onto. */
        const val MANTLE_RISE = 0.62f
        /** Horizontal gap that still counts as being at the ledge. */
        const val MANTLE_REACH = 0.42f
        const val EPS = 0.001f
    }
}
