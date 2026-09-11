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
    enum class Mode { HIDDEN, LURKING, ALERTED, CHASING, VENT_SWIPE, PEERING, ENDING }

    var mode = Mode.HIDDEN
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

    /** Advances along the route so the full run takes [durationSeconds]. */
    fun advanceChase(dt: Float, durationSeconds: Float) {
        if (legs.isEmpty()) return
        chaseProgress = (chaseProgress + dt / durationSeconds).coerceIn(0f, 1f)
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

    fun bounds(): Box = Box(x - 0.55f * scale, y - HEIGHT * scale, x + 0.55f * scale, y)

    fun touching(player: Player): Boolean = bounds().inflated(-0.12f).overlaps(player.bounds())

    fun draw(c: Canvas, d: Draw, cam: Camera, time: Float) {
        if (mode == Mode.HIDDEN) return
        drawAt(c, d, cam.sx(x), cam.sy(y), cam.s(HEIGHT * scale), time)
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

        // Smoke pooling at the feet.
        d.circle(c, sx, feetY, hPx * 0.30f, Palette.withAlpha(0xFF000000.toInt(), 0.45f))
        d.circle(c, sx, feetY - hPx * 0.02f, hPx * 0.20f, Palette.withAlpha(Palette.MONSTER_CRACK, 0.14f))

        // Legs — long and angular.
        d.line(c, sx, hipY, sx + swing * stride, feetY, Palette.MONSTER, limb * 2.1f)
        d.line(c, sx, hipY, sx + swing2 * stride, feetY, Palette.MONSTER, limb * 2.1f)

        // Hunched torso.
        val shW = hPx * 0.135f
        val hipW = hPx * 0.085f
        val lean = if (facing >= 0) hPx * 0.07f else -hPx * 0.07f
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

        // Torn seams of purple rot.
        val crack = Palette.withAlpha(Palette.MONSTER_CRACK, 0.75f)
        d.line(c, sx - shW * 0.3f + lean, shoulderY + hPx * 0.04f, sx + hipW * 0.2f, hipY - hPx * 0.05f, crack, limb * 0.55f)
        d.line(c, sx + shW * 0.45f + lean, shoulderY + hPx * 0.09f, sx + hipW * 0.6f, hipY - hPx * 0.12f, crack, limb * 0.4f)
        d.line(c, sx - hipW * 0.7f, hipY - hPx * 0.02f, sx - hipW * 0.2f, hipY + hPx * 0.10f, crack, limb * 0.35f)

        // Arms, reaching further while chasing.
        val reach = if (mode == Mode.CHASING) hPx * 0.30f else hPx * 0.12f
        val armY = shoulderY + hPx * 0.03f
        d.line(c, sx + lean, armY, sx + facing * reach + swing2 * stride * 0.6f, armY + hPx * 0.26f, Palette.MONSTER, limb * 1.7f)
        d.line(c, sx + lean, armY, sx + facing * reach * 1.25f + swing * stride * 0.6f, armY + hPx * 0.20f, Palette.MONSTER, limb * 1.7f)

        // Head and the two red eyes.
        d.circle(c, sx + lean * 1.4f, headY, headR, Palette.MONSTER)
        val eyeGap = headR * 0.46f
        val eyeY = headY - headR * 0.12f
        val glowK = 0.75f + 0.25f * sin(time * 5.3f)
        val ex = sx + lean * 1.4f + facing * headR * 0.22f
        d.glow(c, ex - eyeGap, eyeY, headR * 0.95f, Palette.MONSTER_EYE, glowK)
        d.glow(c, ex + eyeGap, eyeY, headR * 0.95f, Palette.MONSTER_EYE, glowK)
        d.circle(c, ex - eyeGap, eyeY, headR * 0.20f, Palette.MONSTER_EYE)
        d.circle(c, ex + eyeGap, eyeY, headR * 0.20f, Palette.MONSTER_EYE)
    }

    companion object { const val HEIGHT = 2.35f }
}
