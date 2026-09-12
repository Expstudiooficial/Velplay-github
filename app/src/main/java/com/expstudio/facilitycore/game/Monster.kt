package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Camera
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.abs
import kotlin.math.sin

/** One leg of the chase route. Each leg lives entirely inside a single room. */
class ChaseLeg(val roomId: String, val fromX: Float, val toX: Float, val groundY: Float) {
    val length: Float get() = abs(toX - fromX)
}

/**
 * The thing in the dark. Outside the chase it is a scripted actor; during the
 * chase its position is driven by the 15 second budget, so the pressure the
 * player feels is exactly the pressure the design intends.
 */
class Monster {
    enum class Mode { HIDDEN, LURKING, ALERTED, CHASING, VENT_SWIPE, PEERING, ENDING, ATTACKING }

    /**
     * SHADE is the thing from Chapter 1 — black, split by purple rot.
     * PLAYERR is what is waiting on Subfloor 1: bigger, scarred, and lit from
     * inside in red, violet and blue.
     */
    enum class Kind { SHADE, PLAYERR }

    var kind = Kind.SHADE
    var mode = Mode.HIDDEN

    /** 0..1 through a telegraphed swing; drives the wind-up and the strike. */
    var swing = 0f
    var roomId = ""
    var x = 0f
    var y = 0f
    var facing = -1

    /** 0..1 along the chase route. */
    var chaseProgress = 0f
        private set

    private var legs: List<ChaseLeg> = emptyList()
    private var totalLength = 0f
    private var animPhase = 0f
    private var screamCooldown = 0f

    /** Scales the whole silhouette; the ending and jumpscare push this up. */
    var scale = 1f

    fun setRoute(route: List<ChaseLeg>) {
        legs = route
        totalLength = route.sumOf { it.length.toDouble() }.toFloat().coerceAtLeast(0.001f)
    }

    fun resetChase() {
        chaseProgress = 0f
        legs.firstOrNull()?.let {
            roomId = it.roomId
            x = it.fromX
            y = it.groundY
            facing = if (it.toX > it.fromX) 1 else -1
        }
    }

    fun place(room: String, px: Float, py: Float, face: Int = -1) {
        roomId = room; x = px; y = py; facing = face
    }

    /** Total route length in metres, so callers can reason about leads. */
    val routeLength: Float get() = totalLength

    /**
     * Maps a position in the world onto the chase route, or null when that room
     * is not on it. Used to keep the pursuit anchored to the player rather than
     * to the clock.
     */
    fun progressAt(roomId: String, x: Float): Float? {
        if (legs.isEmpty()) return null
        var travelled = 0f
        for (leg in legs) {
            if (leg.roomId == roomId) {
                val along = if (leg.toX >= leg.fromX) (x - leg.fromX) else (leg.fromX - x)
                return ((travelled + along.coerceIn(0f, leg.length)) / totalLength).coerceIn(0f, 1f)
            }
            travelled += leg.length
        }
        return null
    }

    /** Drives the pursuit forward. It never reverses. */
    fun setChaseProgress(progress: Float) {
        if (legs.isEmpty()) return
        chaseProgress = progress.coerceIn(0f, 1f).coerceAtLeast(chaseProgress)
        var travelled = chaseProgress * totalLength
        for (leg in legs) {
            if (travelled <= leg.length || leg === legs.last()) {
                val f = if (leg.length <= 0.0001f) 1f else (travelled / leg.length).coerceIn(0f, 1f)
                roomId = leg.roomId
                x = MathX.lerp(leg.fromX, leg.toX, f)
                y = leg.groundY
                facing = if (leg.toX > leg.fromX) 1 else -1
                return
            }
            travelled -= leg.length
        }
    }

    fun update(dt: Float) {
        animPhase += dt * when (mode) {
            Mode.CHASING -> 13f
            Mode.ALERTED -> 6f
            else -> 2.2f
        }
        if (screamCooldown > 0f) screamCooldown -= dt
    }

    fun wantsScream(): Boolean {
        if (screamCooldown > 0f) return false
        screamCooldown = 2.6f
        return true
    }

    private fun bodyTint(): Int = if (kind == Kind.PLAYERR) PLAYERR_BODY else Palette.MONSTER
    private fun rimTint(): Int = if (kind == Kind.PLAYERR) PLAYERR_EYE else Palette.MONSTER_CRACK
    private fun crackTint(): Int = if (kind == Kind.PLAYERR) PLAYERR_SCAR else Palette.MONSTER_CRACK
    private fun eyeTint(): Int = if (kind == Kind.PLAYERR) PLAYERR_EYE else Palette.MONSTER_EYE

    fun height(): Float = HEIGHT * (if (kind == Kind.PLAYERR) 1.25f else 1f) * scale

    fun bounds(): Box {
        val half = 0.55f * scale * (if (kind == Kind.PLAYERR) 1.2f else 1f)
        return Box(x - half, y - height(), x + half, y)
    }

    fun touching(player: Player): Boolean = bounds().inflated(-0.12f).overlaps(player.bounds())

    fun draw(c: Canvas, d: Draw, cam: Camera, time: Float) {
        if (mode == Mode.HIDDEN) return
        drawAt(c, d, cam.sx(x), cam.sy(y), cam.s(height()), time)
    }

    /**
     * Silhouette drawing split out so cutscenes and the jumpscare can place the
     * same figure in screen space without a camera.
     */
    fun drawAt(c: Canvas, d: Draw, sx: Float, feetY: Float, hPx: Float, time: Float) {
        val stride = hPx * 0.16f * (if (mode == Mode.CHASING) 1f else 0.35f)
        val swing = sin(animPhase)
        val swing2 = sin(animPhase + Math.PI.toFloat())
        val hipY = feetY - hPx * 0.46f
        val shoulderY = feetY - hPx * 0.86f
        val headR = hPx * 0.115f
        val headY = feetY - hPx * 0.92f
        val limb = hPx * 0.055f
        val shW = hPx * 0.135f
        val hipW = hPx * 0.085f
        val lean = if (facing >= 0) hPx * 0.07f else -hPx * 0.07f
        val armY = shoulderY + hPx * 0.03f
        val reach = if (mode == Mode.CHASING) hPx * 0.30f else hPx * 0.12f

        // Rot haze hanging around the whole figure.
        d.glow(c, sx, feetY - hPx * 0.5f, hPx * 0.62f, crackTint(), 0.55f)
        d.ellipse(c, sx, feetY, hPx * 0.34f, hPx * 0.09f, Palette.withAlpha(0xFF000000.toInt(), 0.55f))
        d.ellipse(c, sx, feetY - hPx * 0.01f, hPx * 0.24f, hPx * 0.06f,
            Palette.withAlpha(crackTint(), 0.16f))

        // Purple rim behind the silhouette: the figure never reads as a hole in
        // the screen, it reads as something standing in front of it.
        val rim = Palette.withAlpha(rimTint(), if (kind == Kind.PLAYERR) 0.55f else 0.42f)
        val rimW = limb * 2.1f + hPx * 0.018f
        d.line(c, sx, hipY, sx + swing * stride, feetY, rim, rimW)
        d.line(c, sx, hipY, sx + swing2 * stride, feetY, rim, rimW)
        d.poly(
            c,
            floatArrayOf(
                sx - shW + lean - hPx * 0.016f, shoulderY - hPx * 0.016f,
                sx + shW + lean + hPx * 0.016f, shoulderY - hPx * 0.016f,
                sx + hipW + hPx * 0.016f, hipY,
                sx - hipW - hPx * 0.016f, hipY
            ),
            rim
        )

        // Legs — long and angular.
        d.line(c, sx, hipY, sx + swing * stride, feetY, bodyTint(), limb * 2.1f)
        d.line(c, sx, hipY, sx + swing2 * stride, feetY, bodyTint(), limb * 2.1f)

        // Hunched torso.
        d.poly(
            c,
            floatArrayOf(
                sx - shW + lean, shoulderY,
                sx + shW + lean, shoulderY,
                sx + hipW, hipY,
                sx - hipW, hipY
            ),
            Palette.MONSTER
        )

        // Broken spines along the back.
        var i = 0
        while (i < 4) {
            val f = i / 4f
            val bx = sx - shW * 0.9f + lean * (1f - f)
            val by = shoulderY + (hipY - shoulderY) * f
            d.poly(
                c,
                floatArrayOf(
                    bx, by,
                    bx - hPx * (0.05f + 0.03f * sin(time * 1.7f + i)), by + hPx * 0.03f,
                    bx, by + hPx * 0.08f
                ),
                Palette.MONSTER
            )
            i++
        }

        // Torn seams of purple rot, lit from inside.
        val crack = Palette.withAlpha(crackTint(), 0.85f)
        d.line(c, sx - shW * 0.3f + lean, shoulderY + hPx * 0.04f, sx + hipW * 0.2f, hipY - hPx * 0.05f, crack, limb * 0.55f)
        d.line(c, sx + shW * 0.45f + lean, shoulderY + hPx * 0.09f, sx + hipW * 0.6f, hipY - hPx * 0.12f, crack, limb * 0.4f)
        d.line(c, sx - hipW * 0.7f, hipY - hPx * 0.02f, sx - hipW * 0.2f, hipY + hPx * 0.10f, crack, limb * 0.35f)
        d.glow(c, sx + lean * 0.4f, (shoulderY + hipY) * 0.5f, hPx * 0.20f, crackTint(), 0.5f)

        // Arms, reaching further while chasing.
        d.line(c, sx + lean, armY, sx + facing * reach + swing2 * stride * 0.6f, armY + hPx * 0.26f, bodyTint(), limb * 1.7f)
        d.line(c, sx + lean, armY, sx + facing * reach * 1.25f + swing * stride * 0.6f, armY + hPx * 0.20f, bodyTint(), limb * 1.7f)
        // Claws on the leading hand.
        val handX = sx + facing * reach * 1.25f + swing * stride * 0.6f
        val handY = armY + hPx * 0.20f
        var k = -1
        while (k <= 1) {
            d.line(c, handX, handY, handX + facing * hPx * 0.07f, handY + hPx * (0.05f + k * 0.035f),
                bodyTint(), limb * 0.5f)
            k++
        }

        // Head and the two red eyes, with a lens flare across them.
        d.circle(c, sx + lean * 1.4f, headY, headR, bodyTint())
        val eyeGap = headR * 0.46f
        val eyeY = headY - headR * 0.12f
        val glowK = 0.75f + 0.25f * sin(time * 5.3f)
        val ex = sx + lean * 1.4f + facing * headR * 0.22f
        d.glow(c, ex, eyeY, headR * 2.6f, eyeTint(), 0.55f * glowK)
        d.glow(c, ex - eyeGap, eyeY, headR * 0.95f, eyeTint(), glowK)
        d.glow(c, ex + eyeGap, eyeY, headR * 0.95f, eyeTint(), glowK)
        d.line(c, ex - headR * 1.9f, eyeY, ex + headR * 1.9f, eyeY,
            Palette.withAlpha(eyeTint(), 0.16f * glowK), headR * 0.16f)
        d.circle(c, ex - eyeGap, eyeY, headR * 0.20f, eyeTint())
        d.circle(c, ex + eyeGap, eyeY, headR * 0.20f, eyeTint())

        if (kind == Kind.PLAYERR) drawPlayerrMarks(c, d, sx, feetY, hPx, time)

        // A telegraphed swing: the arm winds back, then sweeps through.
        if (swing > 0.001f) {
            val windUp = (swing / 0.45f).coerceIn(0f, 1f)
            val strike = ((swing - 0.45f) / 0.55f).coerceIn(0f, 1f)
            val a = MathX.lerp(-2.5f, 0.6f, MathX.smoothStep(strike)) * facing - (1f - windUp) * 0f
            val len = hPx * (0.42f + strike * 0.30f)
            val ax = sx + facing * hPx * 0.10f
            val ay = feetY - hPx * 0.80f
            val hx = ax + kotlin.math.cos(a) * len * facing
            val hy = ay + kotlin.math.sin(a) * len * 0.55f + len * 0.35f
            if (windUp < 1f) {
                // Wind-up tell: the arm glows before it moves.
                d.glow(c, ax, ay, hPx * 0.5f, eyeTint(), windUp * 0.9f)
            }
            d.line(c, ax, ay, hx, hy, bodyTint(), limb * 2.4f)
            d.circle(c, hx, hy, limb * 1.5f, bodyTint())
            for (f in -1..1) {
                d.line(c, hx, hy, hx + facing * hPx * 0.14f, hy + f * hPx * 0.07f, bodyTint(), limb * 0.7f)
            }
            if (strike > 0f && strike < 1f) {
                d.line(c, ax, ay, hx, hy, Palette.withAlpha(eyeTint(), 0.35f * (1f - strike)), limb * 4f)
            }
        }
    }

    /**
     * Extra pass for the Playerr: scar seams in three colours across the body,
     * a cracked faceplate, and a blue underglow. Drawn over the base silhouette.
     */
    private fun drawPlayerrMarks(c: Canvas, d: Draw, sx: Float, feetY: Float, hPx: Float, time: Float) {
        val shoulderY = feetY - hPx * 0.86f
        val hipY = feetY - hPx * 0.46f
        val headY = feetY - hPx * 0.92f
        val headR = hPx * 0.115f
        val pulse = 0.6f + 0.4f * sin(time * 3.4f)

        // Scar seams: red down the chest, violet across the ribs, blue at the hip.
        d.line(c, sx - hPx * 0.02f, shoulderY + hPx * 0.02f, sx + hPx * 0.04f, hipY,
            Palette.withAlpha(PLAYERR_SCAR, 0.9f * pulse), hPx * 0.022f)
        d.line(c, sx - hPx * 0.11f, shoulderY + hPx * 0.16f, sx + hPx * 0.10f, shoulderY + hPx * 0.22f,
            Palette.withAlpha(PLAYERR_EYE, 0.75f * pulse), hPx * 0.016f)
        d.line(c, sx - hPx * 0.08f, hipY - hPx * 0.06f, sx + hPx * 0.07f, hipY + hPx * 0.03f,
            Palette.withAlpha(PLAYERR_COLD, 0.8f * pulse), hPx * 0.016f)
        d.glow(c, sx, (shoulderY + hipY) * 0.5f, hPx * 0.42f, PLAYERR_COLD, 0.35f * pulse)

        // Cracked faceplate: a split mask with a third eye above the pair.
        d.poly(
            c,
            floatArrayOf(
                sx - headR * 0.95f, headY - headR * 0.2f,
                sx + headR * 0.95f, headY - headR * 0.2f,
                sx + headR * 0.7f, headY + headR * 0.85f,
                sx - headR * 0.7f, headY + headR * 0.85f
            ),
            Palette.mix(PLAYERR_BODY, Palette.VOID, 0.35f)
        )
        d.line(c, sx - headR * 0.35f, headY - headR * 0.2f, sx + headR * 0.15f, headY + headR * 0.85f,
            Palette.withAlpha(PLAYERR_SCAR, 0.9f), headR * 0.14f)
        d.circle(c, sx, headY - headR * 0.5f, headR * 0.17f, PLAYERR_EYE)
        d.glow(c, sx, headY - headR * 0.5f, headR * 1.6f, PLAYERR_EYE, 0.7f * pulse)
    }

    companion object {
        const val HEIGHT = 2.35f
        /**
         * The Playerr's palette: a bruised body lit by red, violet and blue.
         * The body is deliberately lighter than the shade's — at the shade's
         * value the scars were all that read and the figure looked like a smear
         * of colour floating in the dark.
         */
        val PLAYERR_BODY = 0xFF3A1E2E.toInt()
        val PLAYERR_SCAR = 0xFFFF2E4C.toInt()
        val PLAYERR_EYE = 0xFFB14CFF.toInt()
        val PLAYERR_COLD = 0xFF3FA9FF.toInt()
    }
}
