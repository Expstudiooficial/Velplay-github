package com.expstudio.facilitycore.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.random.Random

/**
 * Maps world metres to screen pixels. The camera keeps a fixed vertical field of
 * view so the game frames identically on every phone aspect ratio.
 */
class Camera {
    var x = 0f
    var y = 0f
    var scale = 48f
        private set

    var viewW = 0f
        private set
    var viewH = 0f
        private set

    private var shakeTime = 0f
    private var shakeMag = 0f
    private var shakeX = 0f
    private var shakeY = 0f
    private val rng = Random(7)

    /** Metres visible vertically. Tuned so a 2 m character reads clearly. */
    var verticalMetres = 11f

    fun resize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        scale = heightPx / verticalMetres
        viewW = widthPx / scale
        viewH = heightPx / scale
    }

    fun shake(magnitude: Float, duration: Float) {
        // Never let a weaker shake cut a stronger one short.
        if (magnitude >= shakeMag || shakeTime <= 0f) {
            shakeMag = magnitude
            shakeTime = duration
        }
    }

    fun update(dt: Float) {
        if (shakeTime > 0f) {
            shakeTime -= dt
            val k = max(0f, shakeTime) * shakeMag
            shakeX = (rng.nextFloat() * 2f - 1f) * k
            shakeY = (rng.nextFloat() * 2f - 1f) * k
            if (shakeTime <= 0f) { shakeMag = 0f; shakeX = 0f; shakeY = 0f }
        }
    }

    /** Centres on a target, easing in, then clamps so the room never shows void. */
    fun follow(targetX: Float, targetY: Float, bounds: Box, dt: Float, snap: Boolean = false) {
        val tx = targetX
        val ty = targetY
        if (snap) { x = tx; y = ty } else {
            x = MathX.approach(x, tx, 0.16f, dt)
            y = MathX.approach(y, ty, 0.12f, dt)
        }
        clampTo(bounds)
    }

    fun clampTo(bounds: Box) {
        val halfW = viewW * 0.5f
        val halfH = viewH * 0.5f
        x = if (bounds.w <= viewW) bounds.cx else MathX.clamp(x, bounds.l + halfW, bounds.r - halfW)
        y = if (bounds.h <= viewH) bounds.cy else MathX.clamp(y, bounds.t + halfH, bounds.b - halfH)
    }

    fun sx(worldX: Float): Float = (worldX - (x + shakeX)) * scale + viewW * scale * 0.5f
    fun sy(worldY: Float): Float = (worldY - (y + shakeY)) * scale + viewH * scale * 0.5f
    fun s(metres: Float): Float = metres * scale

    fun worldX(screenX: Float): Float = (screenX - viewW * scale * 0.5f) / scale + (x + shakeX)
    fun worldY(screenY: Float): Float = (screenY - viewH * scale * 0.5f) / scale + (y + shakeY)

    fun isVisible(box: Box, margin: Float = 2f): Boolean {
        val halfW = viewW * 0.5f + margin
        val halfH = viewH * 0.5f + margin
        return box.r > x - halfW && box.l < x + halfW && box.b > y - halfH && box.t < y + halfH
    }
}

/** Shared paints + drawing primitives. One instance per view, never per frame. */
class Draw {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val rect = RectF()
    private val path = Path()

    fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        fill.color = color
        c.drawRect(l, t, r, b, fill)
    }

    fun round(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        fill.color = color
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, radius, radius, fill)
    }

    fun roundStroke(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, radius, radius, stroke)
    }

    fun circle(c: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        fill.color = color
        c.drawCircle(cx, cy, radius, fill)
    }

    fun circleStroke(c: Canvas, cx: Float, cy: Float, radius: Float, color: Int, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width
        c.drawCircle(cx, cy, radius, stroke)
    }

    fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width
        c.drawLine(x1, y1, x2, y2, stroke)
    }

    private val glowBitmaps = HashMap<Int, Bitmap>()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val blitDst = RectF()

    /**
     * Soft halo. The falloff is rendered once per colour into a small bitmap and
     * blitted scaled: a radial gradient evaluated per pixel is one of the most
     * expensive things this renderer can ask for, and stacking translucent
     * discs — the cheap alternative — produced visible concentric banding.
     */
    fun glow(c: Canvas, cx: Float, cy: Float, radius: Float, color: Int, strength: Float = 1f) {
        if (strength <= 0.005f || radius <= 0.5f) return
        val bmp = glowBitmaps.getOrPut(color) { buildFalloff(color) }
        glowPaint.alpha = MathX.clamp(strength * 0.42f * 255f, 0f, 255f).toInt()
        blitDst.set(cx - radius, cy - radius, cx + radius, cy + radius)
        c.drawBitmap(bmp, null, blitDst, glowPaint)
        glowPaint.alpha = 255
    }

    private fun buildFalloff(color: Int): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val half = size * 0.5f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            half, half, half,
            intArrayOf(
                Palette.withAlpha(color, 1f),
                Palette.withAlpha(color, 0.52f),
                Palette.withAlpha(color, 0.17f),
                Palette.withAlpha(color, 0f)
            ),
            floatArrayOf(0f, 0.26f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(half, half, half, paint)
        return bmp
    }

    /** Draws a closed polygon from flat x,y pairs. */
    fun poly(c: Canvas, pts: FloatArray, color: Int) {
        if (pts.size < 6) return
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        fill.color = color
        c.drawPath(path, fill)
    }

    fun textCentered(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, bold: Boolean = false) {
        text.textSize = size
        text.color = color
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        val fm = text.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) * 0.5f, text)
    }

    fun textLeft(c: Canvas, s: String, x: Float, baselineY: Float, size: Float, color: Int, bold: Boolean = false) {
        text.textSize = size
        text.color = color
        text.textAlign = Paint.Align.LEFT
        text.typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        c.drawText(s, x, baselineY, text)
    }

    fun measure(s: String, size: Float, bold: Boolean = false): Float {
        text.textSize = size
        text.typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        return text.measureText(s)
    }

    /** Greedy word wrap; returns the lines so callers can lay out a panel first. */
    fun wrap(s: String, size: Float, maxWidth: Float): List<String> {
        if (maxWidth <= 0f) return listOf(s)
        text.textSize = size
        text.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val out = ArrayList<String>()
        for (paragraph in s.split("\n")) {
            if (paragraph.isEmpty()) { out.add(""); continue }
            var line = StringBuilder()
            for (word in paragraph.split(" ")) {
                val candidate = if (line.isEmpty()) word else "${line} $word"
                if (text.measureText(candidate) <= maxWidth || line.isEmpty()) {
                    line = StringBuilder(candidate)
                } else {
                    out.add(line.toString())
                    line = StringBuilder(word)
                }
            }
            out.add(line.toString())
        }
        return out
    }

    private val gradients = HashMap<Long, LinearGradient>()
    private val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradMatrix = Matrix()

    /**
     * Vertical gradient fill. One unit-tall shader per colour pair is built once
     * and stretched with a matrix, so a gradient costs no more than a flat fill
     * after the first frame.
     */
    fun vGradient(c: Canvas, l: Float, t: Float, r: Float, b: Float, top: Int, bottom: Int) {
        if (b <= t || r <= l) return
        val key = (top.toLong() shl 32) or (bottom.toLong() and 0xFFFFFFFFL)
        val shader = gradients.getOrPut(key) {
            LinearGradient(0f, 0f, 0f, 1f, top, bottom, Shader.TileMode.CLAMP)
        }
        gradMatrix.reset()
        gradMatrix.setScale(1f, b - t)
        gradMatrix.postTranslate(0f, t)
        shader.setLocalMatrix(gradMatrix)
        gradPaint.shader = shader
        c.drawRect(l, t, r, b, gradPaint)
        gradPaint.shader = null
    }

    private val ovalRect = RectF()

    fun ellipse(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        fill.color = color
        ovalRect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(ovalRect, fill)
    }

    /**
     * A shaft of light falling from a fixture. Three stacked trapezoids read as
     * volumetric without needing a blur or an offscreen layer.
     */
    fun lightCone(
        c: Canvas,
        apexX: Float,
        apexY: Float,
        topHalf: Float,
        bottomHalf: Float,
        length: Float,
        color: Int,
        strength: Float
    ) {
        if (strength <= 0.01f || length <= 0f) return
        var i = 0
        while (i < 6) {
            val f = 1f - i * 0.15f
            val a = 0.030f * strength
            poly(
                c,
                floatArrayOf(
                    apexX - topHalf * f, apexY,
                    apexX + topHalf * f, apexY,
                    apexX + bottomHalf * f, apexY + length * f,
                    apexX - bottomHalf * f, apexY + length * f
                ),
                Palette.withAlpha(color, a)
            )
            i++
        }
    }

    /**
     * A surface slab: gradient body, a lit top lip, a shaded underside, and a
     * contact shadow where it meets whatever is beneath it.
     */
    fun surface(
        c: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        top: Int,
        bottom: Int,
        lip: Int,
        lipPx: Float,
        occlusionPx: Float = 0f
    ) {
        vGradient(c, l, t, r, b, top, bottom)
        if (lipPx > 0.4f) {
            rect(c, l, t, r, t + lipPx, lip)
            rect(c, l, b - lipPx * 0.5f, r, b, Palette.withAlpha(Palette.VOID, 0.28f))
        }
        // Vertical edges catch a touch of light so corners read as corners.
        rect(c, l, t, l + lipPx * 0.6f, b, Palette.withAlpha(lip, 0.18f))
        rect(c, r - lipPx * 0.6f, t, r, b, Palette.withAlpha(Palette.VOID, 0.20f))
        if (occlusionPx > 0.5f) {
            vGradient(
                c, l - occlusionPx * 0.5f, b - occlusionPx, r + occlusionPx * 0.5f, b + occlusionPx,
                Palette.withAlpha(Palette.VOID, 0f), Palette.withAlpha(Palette.VOID, 0.45f)
            )
        }
    }

    private var darkBitmap: Bitmap? = null
    private var darkKeyColor = 0
    private var darkKeyDepth = -1f
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    /**
     * Pours darkness over the screen with a soft hole around ([cx], [cy]).
     * The falloff is a cached bitmap rather than a live gradient: this pass
     * covers the whole screen every frame in every unlit room, and measured as
     * more than half the cost of a frame when it was shaded per pixel.
     */
    fun darkness(
        c: Canvas,
        widthPx: Float,
        heightPx: Float,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        /** Darkness at the far edge. Kept below 1 so unlit space stays readable. */
        depth: Float = 0.84f
    ) {
        val r = radius.coerceAtLeast(1f)
        if (darkBitmap == null || darkKeyColor != color || darkKeyDepth != depth) {
            darkBitmap = buildDarkness(color, depth)
            darkKeyColor = color
            darkKeyDepth = depth
        }
        val bmp = darkBitmap ?: return
        blitDst.set(cx - r, cy - r, cx + r, cy + r)
        c.drawBitmap(bmp, null, blitDst, darkPaint)
        // The blit only covers the hole; seal everything outside it flat.
        darkPaint.color = Palette.withAlpha(color, depth)
        if (cx - r > 0f) c.drawRect(0f, 0f, cx - r, heightPx, darkPaint)
        if (cx + r < widthPx) c.drawRect(cx + r, 0f, widthPx, heightPx, darkPaint)
        if (cy - r > 0f) c.drawRect(cx - r, 0f, cx + r, cy - r, darkPaint)
        if (cy + r < heightPx) c.drawRect(cx - r, cy + r, cx + r, heightPx, darkPaint)
    }

    private fun buildDarkness(color: Int, depth: Float): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val half = size * 0.5f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Square corners must already read as fully dark, or the seal rects
        // would show as bright seams against the blit.
        paint.color = Palette.withAlpha(color, depth)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = RadialGradient(
            half, half, half,
            intArrayOf(
                Palette.withAlpha(color, 0f),
                Palette.withAlpha(color, depth * 0.34f),
                Palette.withAlpha(color, depth * 0.82f),
                Palette.withAlpha(color, depth)
            ),
            floatArrayOf(0f, 0.46f, 0.84f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC)
        canvas.drawCircle(half, half, half, paint)
        return bmp
    }

    private var vignetteBitmap: Bitmap? = null
    private var vignetteKeyColor = 0
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    /**
     * Standing screen-edge falloff; [strength] is pushed up during the chase.
     * Cached as a bitmap for the same reason as [darkness]: it covers the whole
     * frame in every room, every frame.
     */
    fun vignette(c: Canvas, widthPx: Float, heightPx: Float, strength: Float, color: Int) {
        if (strength <= 0.001f || widthPx <= 0f || heightPx <= 0f) return
        if (vignetteBitmap == null || vignetteKeyColor != color) {
            vignetteBitmap = buildVignette(color)
            vignetteKeyColor = color
        }
        val bmp = vignetteBitmap ?: return
        vignettePaint.alpha = MathX.clamp(strength * 255f, 0f, 255f).toInt()
        blitDst.set(0f, 0f, widthPx, heightPx)
        c.drawBitmap(bmp, null, blitDst, vignettePaint)
        vignettePaint.alpha = 255
    }

    private fun buildVignette(color: Int): Bitmap {
        // Stretched to the screen, so a square source is fine and cheap.
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val half = size * 0.5f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            half, half, half,
            intArrayOf(Palette.withAlpha(color, 0f), Palette.withAlpha(color, 0.18f), Palette.withAlpha(color, 1f)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bmp
    }

    fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        round(c, l, t, r, b, radius, Palette.PANEL)
        roundStroke(c, l, t, r, b, radius, Palette.PANEL_EDGE, 2f)
    }
}
