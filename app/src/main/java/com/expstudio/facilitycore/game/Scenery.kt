package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Camera
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.sin
import kotlin.random.Random

/**
 * Depth for a room: three parallax layers of structure behind the playfield,
 * plus a drifting dust field in front of it.
 *
 * Everything is generated once per room from the room id, so a room looks the
 * same every time it is entered but no two rooms look alike, and nothing is
 * allocated while rendering.
 */
class Scenery(room: Room) {

    /** One piece of background structure, positioned in world units. */
    private class Piece(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val top: Int,
        val bottom: Int,
        val lip: Int
    )

    private val far = ArrayList<Piece>()
    private val mid = ArrayList<Piece>()
    private val near = ArrayList<Piece>()

    private val dustX: FloatArray
    private val dustY: FloatArray
    private val dustSize: FloatArray
    private val dustPhase: FloatArray

    private val bounds: Box = room.bounds

    init {
        val rng = Random(room.id.hashCode().toLong() * 2654435761L)
        val b = room.bounds
        val floorY = b.b
        val roofY = b.t

        // --- far: distant machine towers and lit doorways beyond the wall ---
        var x = b.l - 4f
        while (x < b.r + 4f) {
            val w = 1.6f + rng.nextFloat() * 3.4f
            val h = (b.h * 0.35f) + rng.nextFloat() * b.h * 0.5f
            far.add(
                Piece(
                    x, floorY - h, w, h,
                    Palette.mix(Palette.BG_FAR, Palette.BG_NEAR, 0.85f),
                    Palette.mix(Palette.BG_FAR, Palette.VOID, 0.45f),
                    Palette.mix(Palette.BG_NEAR, Palette.TRIM, 0.25f)
                )
            )
            // Occasionally a doorway glows faintly somewhere far off.
            if (rng.nextFloat() < 0.30f) {
                val dh = 1.6f + rng.nextFloat() * 1.4f
                far.add(
                    Piece(
                        x + w * 0.2f, floorY - dh, w * 0.55f, dh,
                        Palette.mix(Palette.BG_NEAR, Palette.ACCENT_DIM, 0.35f),
                        Palette.mix(Palette.BG_NEAR, Palette.VOID, 0.3f),
                        Palette.withAlpha(Palette.ACCENT_DIM, 0.5f)
                    )
                )
            }
            x += w + 1.4f + rng.nextFloat() * 3.6f
        }

        // --- mid: structural ribs and pipe runs ---
        x = b.l - 3f
        while (x < b.r + 3f) {
            val w = 0.42f + rng.nextFloat() * 0.5f
            mid.add(
                Piece(
                    x, roofY + 0.2f, w, b.h * (0.55f + rng.nextFloat() * 0.4f),
                    Palette.mix(Palette.BG_NEAR, Palette.WALL, 0.7f),
                    Palette.mix(Palette.BG_NEAR, Palette.VOID, 0.35f),
                    Palette.mix(Palette.WALL, Palette.TRIM, 0.35f)
                )
            )
            x += 3.2f + rng.nextFloat() * 4.5f
        }
        var pipeY = roofY + 0.9f
        var pipes = 0
        while (pipes < 3 && pipeY < floorY - 1.5f) {
            val px = b.l - 2f + rng.nextFloat() * 3f
            val pw = b.w * (0.35f + rng.nextFloat() * 0.6f)
            mid.add(
                Piece(
                    px, pipeY, pw, 0.26f + rng.nextFloat() * 0.16f,
                    Palette.mix(Palette.WALL, Palette.TRIM, 0.45f),
                    Palette.mix(Palette.WALL, Palette.VOID, 0.4f),
                    Palette.mix(Palette.TRIM, Palette.TEXT_DIM, 0.25f)
                )
            )
            pipeY += 1.1f + rng.nextFloat() * 1.8f
            pipes++
        }

        // --- near: wall panelling right behind the playfield ---
        x = b.l
        while (x < b.r) {
            val w = 2.2f + rng.nextFloat() * 1.8f
            near.add(
                Piece(
                    x + 0.08f, roofY + 0.35f, w - 0.16f, b.h - 0.9f,
                    Palette.mix(Palette.WALL, Palette.WALL_LIT, 0.30f),
                    Palette.mix(Palette.WALL, Palette.VOID, 0.35f),
                    Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.4f)
                )
            )
            x += w
        }

        // --- dust ---
        val count = 52
        dustX = FloatArray(count)
        dustY = FloatArray(count)
        dustSize = FloatArray(count)
        dustPhase = FloatArray(count)
        for (i in 0 until count) {
            dustX[i] = b.l + rng.nextFloat() * b.w
            dustY[i] = roofY + rng.nextFloat() * b.h
            dustSize[i] = 0.012f + rng.nextFloat() * 0.026f
            dustPhase[i] = rng.nextFloat() * 6.283f
        }
    }

    /**
     * Draws a layer offset toward the camera centre by (1 - depth), which is
     * what makes it sit behind the playfield.
     */
    private fun layer(
        c: Canvas,
        d: Draw,
        cam: Camera,
        pieces: List<Piece>,
        depth: Float,
        tint: Float,
        detailed: Boolean
    ) {
        val ox = (cam.x - bounds.cx) * (1f - depth)
        val oy = (cam.y - bounds.cy) * (1f - depth) * 0.55f
        val screenW = cam.viewW * cam.scale
        for (p in pieces) {
            val l = cam.sx(p.x + ox)
            val r = cam.sx(p.x + p.w + ox)
            if (r < -40f || l > screenW + 40f) continue
            val t = cam.sy(p.y + oy)
            val b = cam.sy(p.y + p.h + oy)
            if (detailed) {
                d.surface(
                    c, l, t, r, b,
                    Palette.mix(p.top, Palette.BG_FAR, tint),
                    Palette.mix(p.bottom, Palette.BG_FAR, tint),
                    Palette.mix(p.lip, Palette.BG_FAR, tint),
                    MathX.min(cam.s(0.05f), 3f)
                )
            } else {
                // Distant shapes get the gradient body only: the bevel detail is
                // invisible back there and the fill rate is not.
                d.vGradient(
                    c, l, t, r, b,
                    Palette.mix(p.top, Palette.BG_FAR, tint),
                    Palette.mix(p.bottom, Palette.BG_FAR, tint)
                )
            }
        }
    }

    fun drawFar(c: Canvas, d: Draw, cam: Camera) = layer(c, d, cam, far, 0.22f, 0.55f, detailed = false)
    fun drawMid(c: Canvas, d: Draw, cam: Camera) = layer(c, d, cam, mid, 0.45f, 0.30f, detailed = true)

    /**
     * Wall panelling right behind the playfield. Only the seams and top lips are
     * drawn: filling every panel was a second full-screen gradient pass per
     * frame for detail the seams already imply.
     */
    fun drawNear(c: Canvas, d: Draw, cam: Camera) {
        val depth = 0.72f
        val ox = (cam.x - bounds.cx) * (1f - depth)
        val oy = (cam.y - bounds.cy) * (1f - depth) * 0.55f
        val screenW = cam.viewW * cam.scale
        val seam = MathX.min(cam.s(0.05f), 3f).coerceAtLeast(1.5f)
        for (p in near) {
            val l = cam.sx(p.x + ox)
            val r = cam.sx(p.x + p.w + ox)
            if (r < -40f || l > screenW + 40f) continue
            val t = cam.sy(p.y + oy)
            val b = cam.sy(p.y + p.h + oy)
            d.rect(c, l, t, r, t + seam, Palette.withAlpha(p.lip, 0.30f))
            d.rect(c, l - seam, t, l, b, Palette.withAlpha(Palette.VOID, 0.42f))
            d.rect(c, r, t, r + seam, b, Palette.withAlpha(Palette.VOID, 0.42f))
            d.rect(c, l, b - seam, r, b, Palette.withAlpha(Palette.VOID, 0.32f))
        }
    }

    /** Motes drifting upward on the room's air. Brightest near the player. */
    fun drawDust(c: Canvas, d: Draw, cam: Camera, time: Float, focusX: Float, focusY: Float, brightness: Float) {
        if (brightness <= 0.02f) return
        val span = bounds.h
        for (i in dustX.indices) {
            val phase = dustPhase[i]
            val rise = ((time * 0.06f + phase * 0.16f) % 1f) * span
            val wx = dustX[i] + sin(time * 0.35f + phase) * 0.45f
            val wy = bounds.b - rise
            val dist = kotlin.math.hypot(wx - focusX, wy - focusY)
            val near = MathX.clamp(1f - dist / 6.5f, 0f, 1f)
            if (near <= 0.02f) continue
            val twinkle = 0.55f + 0.45f * sin(time * 2.3f + phase * 3f)
            d.circle(
                c, cam.sx(wx), cam.sy(wy), cam.s(dustSize[i]),
                Palette.withAlpha(Palette.TEXT, 0.30f * near * twinkle * brightness)
            )
        }
    }
}
