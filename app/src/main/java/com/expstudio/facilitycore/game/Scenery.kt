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

    /** One mark left on a wall or floor by whatever happened here. */
    private class Mark(val kind: Int, val x: Float, val y: Float, val a: Float, val b: Float, val seed: Float)

    private val marks = ArrayList<Mark>()

    private val dustX: FloatArray
    private val dustY: FloatArray
    private val dustSize: FloatArray
    private val dustPhase: FloatArray

    private val bounds: Box = room.bounds

    private companion object {
        const val MARK_CLAW = 0
        const val MARK_DRAG = 1
        const val MARK_HAND = 2
        const val MARK_STAIN = 3
    }

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

        // --- marks left behind ---
        // These replace the wall signs. A sign tells you where to go; four
        // gouges at chest height and a long drag mark tell you something was
        // taken this way, which is the same information and much worse.
        val markCount = 3 + rng.nextInt(4)
        repeat(markCount) {
            val kind = rng.nextInt(4)
            val mx = b.l + 1.5f + rng.nextFloat() * (b.w - 3f)
            val my = when (kind) {
                MARK_DRAG -> floorY - 0.06f
                MARK_STAIN -> floorY - rng.nextFloat() * 0.5f
                else -> floorY - 1.1f - rng.nextFloat() * 1.6f
            }
            marks.add(
                Mark(
                    kind, mx, my,
                    0.5f + rng.nextFloat() * 1.4f,
                    0.3f + rng.nextFloat() * 0.9f,
                    rng.nextFloat()
                )
            )
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

    /**
     * Claw gouges, drag trails, handprints and stains, drawn onto the wall just
     * behind the playfield. Deterministic per room, so a place always carries
     * the same history.
     */
    fun drawMarks(c: Canvas, d: Draw, cam: Camera, amb: Float) {
        if (marks.isEmpty()) return
        val detail = cam.scale
        if (detail < 20f) return
        for (m in marks) {
            val sx = cam.sx(m.x)
            val sy = cam.sy(m.y)
            if (sx < -80f || sx > cam.viewW * cam.scale + 80f) continue
            val ink = Palette.withAlpha(Palette.VOID, (0.30f + 0.25f * m.seed) * (0.4f + 0.6f * amb))
            when (m.kind) {
                MARK_CLAW -> {
                    // Four parallel gouges, raked downward.
                    val len = cam.s(m.a)
                    val spread = cam.s(0.11f)
                    for (k in 0 until 4) {
                        val ox = sx + (k - 1.5f) * spread
                        val lean = cam.s(0.22f) * (m.seed - 0.5f)
                        d.line(c, ox, sy, ox + lean, sy + len, ink, MathX.clamp(detail * 0.022f, 1.2f, 3f))
                        // A lighter edge where the paint curled away.
                        d.line(
                            c, ox + 1.5f, sy, ox + lean + 1.5f, sy + len,
                            Palette.withAlpha(Palette.TEXT, 0.07f * amb),
                            MathX.clamp(detail * 0.012f, 1f, 2f)
                        )
                    }
                }
                MARK_DRAG -> {
                    // Something was pulled along the floor, and resisted.
                    val len = cam.s(m.a * 2.4f)
                    d.vGradient(
                        c, sx, sy, sx + len, sy + cam.s(0.16f),
                        Palette.withAlpha(Palette.VOID, 0.34f * amb),
                        Palette.withAlpha(Palette.VOID, 0f)
                    )
                    for (k in 0 until 3) {
                        val oy = sy + k * cam.s(0.05f)
                        d.line(c, sx, oy, sx + len * (0.6f + 0.4f * m.seed), oy, ink,
                            MathX.clamp(detail * 0.015f, 1f, 2.5f))
                    }
                }
                MARK_HAND -> {
                    // A print, pressed and then dragged down.
                    val r = cam.s(0.13f)
                    d.ellipse(c, sx, sy, r * 0.8f, r, ink)
                    for (k in 0 until 4) {
                        val fx = sx + (k - 1.5f) * r * 0.55f
                        d.line(c, fx, sy - r * 0.5f, fx + (m.seed - 0.5f) * r * 0.4f, sy - r * 1.7f, ink, r * 0.34f)
                    }
                    d.vGradient(
                        c, sx - r * 0.7f, sy, sx + r * 0.7f, sy + cam.s(0.5f + m.seed * 0.6f),
                        Palette.withAlpha(Palette.VOID, 0.28f * amb),
                        Palette.withAlpha(Palette.VOID, 0f)
                    )
                }
                else -> {
                    // A stain that soaked in and dried a long time ago.
                    val rx = cam.s(m.a * 0.6f)
                    val ry = cam.s(m.b * 0.35f)
                    d.ellipse(c, sx, sy, rx, ry, Palette.withAlpha(Palette.VOID, 0.30f * amb))
                    d.ellipse(c, sx + rx * 0.4f, sy + ry * 0.3f, rx * 0.45f, ry * 0.5f,
                        Palette.withAlpha(Palette.VOID, 0.22f * amb))
                }
            }
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
