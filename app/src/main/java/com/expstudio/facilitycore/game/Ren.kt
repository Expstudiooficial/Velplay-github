package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Camera
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.abs
import kotlin.math.sin

/**
 * Ren.
 *
 * The reason for all four chapters, and the only figure in the game that is not
 * either the player or something hunting them. He sits until he is found, gets
 * up, and then keeps a few paces behind — badly, because he has been down here
 * a long time.
 *
 * Deliberately not a physics body. He follows the player's own footing, which
 * means he can never be the thing that gets stuck on geometry; after four
 * chapters of chasing bugs into corners, the companion is the last place that
 * should be possible.
 */
class Ren(box: Box) : Prop(box) {

    var found = false
    var following = false

    /** Rises once he is up, so he is drawn standing rather than folded. */
    private var stand = 0f
    private var walk = 0f
    private var facing = -1

    /** Trails the player by a few paces, sampled so he does not mirror them. */
    private val trailX = FloatArray(TRAIL)
    private val trailY = FloatArray(TRAIL)
    private var trailAt = 0
    private var seeded = false

    override val reach: Float = 1.8f

    override fun interactLabel(g: GameSession): String = if (found) "" else "REN?"

    override fun onInteract(g: GameSession) {
        if (found) return
        found = true
        g.onCompanionFound(this)
    }

    fun x(): Float = (box.l + box.r) * 0.5f
    fun y(): Float = box.b

    override fun update(g: GameSession, dt: Float) {
        stand = MathX.approach(stand, if (found) 1f else 0f, 1.6f, dt)
        if (!following) return

        if (!seeded) {
            for (i in 0 until TRAIL) { trailX[i] = g.player.x; trailY[i] = g.player.y }
            seeded = true
        }
        // Record where the player has been, and stand where they were a moment
        // ago. Following a path they have already walked is why he cannot end
        // up inside anything.
        trailAt = (trailAt + 1) % TRAIL
        trailX[trailAt] = g.player.x
        trailY[trailAt] = g.player.y
        val back = (trailAt + 1) % TRAIL
        val tx = trailX[back]
        val ty = trailY[back]

        val cx = x()
        val nx = MathX.approach(cx, tx, FOLLOW_SPEED, dt)
        val ny = MathX.approach(y(), ty, FOLLOW_SPEED * 1.4f, dt)
        val w = box.r - box.l
        val h = box.b - box.t
        box.set(nx - w * 0.5f, ny - h, nx + w * 0.5f, ny)

        if (abs(nx - cx) > 0.004f) {
            facing = if (nx > cx) 1 else -1
            walk += abs(nx - cx) * 5.5f
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val sx = cam.sx(x())
        val feet = cam.sy(y())
        val full = cam.s(1.75f)
        // Folded up against the wall until he is found, then upright.
        val hPx = MathX.lerp(full * 0.52f, full, MathX.smoothStep(stand))
        val body = Palette.mix(Palette.PLAYER, Palette.ACCENT_DIM, 0.30f)
        val shade = Palette.mix(Palette.PLAYER_SHADE, Palette.ACCENT_DIM, 0.35f)

        d.ellipse(c, sx, feet, hPx * 0.26f, hPx * 0.07f, Palette.withAlpha(Palette.VOID, 0.45f))

        val swing = if (following) sin(walk) * hPx * 0.13f else 0f
        val hipY = feet - hPx * 0.46f
        val shoulderY = feet - hPx * 0.84f
        d.line(c, sx, hipY, sx + swing, feet, shade, hPx * 0.10f)
        d.line(c, sx, hipY, sx - swing, feet, shade, hPx * 0.10f)
        d.round(c, sx - hPx * 0.13f, shoulderY, sx + hPx * 0.13f, hipY + hPx * 0.04f, hPx * 0.05f, body)
        // Arms, one of them held against his side.
        d.line(c, sx - hPx * 0.10f, shoulderY + hPx * 0.06f, sx - hPx * 0.16f - swing * 0.5f,
            hipY + hPx * 0.12f, shade, hPx * 0.07f)
        d.line(c, sx + hPx * 0.10f, shoulderY + hPx * 0.06f, sx + hPx * 0.14f + swing * 0.5f,
            hipY + hPx * 0.10f, shade, hPx * 0.07f)
        d.circle(c, sx, feet - hPx * 0.92f, hPx * 0.125f, body)
        // A visor like the player's, but dark: his light went out a long time ago.
        d.rect(c, sx - hPx * 0.10f * facing, feet - hPx * 0.95f,
            sx + hPx * 0.055f * facing, feet - hPx * 0.90f,
            Palette.withAlpha(if (found) Palette.ACCENT else Palette.TEXT_DIM, if (found) 0.9f else 0.45f))

        if (!found) {
            val pulse = 0.35f + 0.3f * sin(g.time * 1.7f)
            d.glow(c, sx, feet - hPx * 0.5f, cam.s(1.6f), Palette.ACCENT, 0.3f * pulse)
        }
    }

    private companion object {
        /** Frames of the player's path he lags behind by. */
        const val TRAIL = 34
        const val FOLLOW_SPEED = 7.5f
    }
}
