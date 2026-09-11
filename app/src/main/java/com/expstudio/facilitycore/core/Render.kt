package com.expstudio.facilitycore.core

import android.graphics.Canvas
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

    /** Soft additive-looking halo built from a few stacked translucent discs. */
    fun glow(c: Canvas, cx: Float, cy: Float, radius: Float, color: Int, strength: Float = 1f) {
        var i = 4
        while (i >= 1) {
            val f = i / 4f
            fill.color = Palette.withAlpha(color, 0.10f * strength * (1f - f) + 0.05f * strength)
            c.drawCircle(cx, cy, radius * (0.45f + f * 0.75f), fill)
            i--
        }
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

    private var darkShader: RadialGradient? = null
    private var darkKey = FloatArray(3) { Float.NaN }
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Pours darkness over the screen with a soft hole around ([cx], [cy]).
     * The gradient is rebuilt only when the hole actually moves or resizes.
     */
    fun darkness(c: Canvas, widthPx: Float, heightPx: Float, cx: Float, cy: Float, radius: Float, color: Int) {
        val r = radius.coerceAtLeast(1f)
        if (darkShader == null || darkKey[0] != cx || darkKey[1] != cy || darkKey[2] != r) {
            darkShader = RadialGradient(
                cx, cy, r,
                intArrayOf(
                    Palette.withAlpha(color, 0f),
                    Palette.withAlpha(color, 0.35f),
                    Palette.withAlpha(color, 0.88f),
                    Palette.withAlpha(color, 0.97f)
                ),
                floatArrayOf(0f, 0.42f, 0.82f, 1f),
                Shader.TileMode.CLAMP
            )
            darkKey[0] = cx; darkKey[1] = cy; darkKey[2] = r
        }
        darkPaint.shader = darkShader
        c.drawRect(0f, 0f, widthPx, heightPx, darkPaint)
        darkPaint.shader = null
        // Beyond the gradient's radius CLAMP leaves the last colour, which is
        // not fully opaque, so seal the corners explicitly.
        darkPaint.color = Palette.withAlpha(color, 0.97f)
        if (cx - r > 0f) c.drawRect(0f, 0f, cx - r, heightPx, darkPaint)
        if (cx + r < widthPx) c.drawRect(cx + r, 0f, widthPx, heightPx, darkPaint)
        if (cy - r > 0f) c.drawRect(0f, 0f, widthPx, cy - r, darkPaint)
        if (cy + r < heightPx) c.drawRect(0f, cy + r, widthPx, heightPx, darkPaint)
    }

    private var vignetteShader: RadialGradient? = null
    private var vignetteKey = FloatArray(3) { Float.NaN }
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Standing screen-edge falloff; [strength] is pushed up during the chase. */
    fun vignette(c: Canvas, widthPx: Float, heightPx: Float, strength: Float, color: Int) {
        if (strength <= 0.001f) return
        val cx = widthPx * 0.5f
        val cy = heightPx * 0.5f
        val r = MathX.min(widthPx, heightPx) * 0.92f
        if (vignetteShader == null || vignetteKey[0] != cx || vignetteKey[1] != cy || vignetteKey[2] != r) {
            vignetteShader = RadialGradient(
                cx, cy, r,
                intArrayOf(Palette.withAlpha(color, 0f), Palette.withAlpha(color, 0.18f), Palette.withAlpha(color, 1f)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            vignetteKey[0] = cx; vignetteKey[1] = cy; vignetteKey[2] = r
        }
        vignettePaint.shader = vignetteShader
        vignettePaint.alpha = MathX.clamp(strength * 255f, 0f, 255f).toInt()
        c.drawRect(0f, 0f, widthPx, heightPx, vignettePaint)
        vignettePaint.shader = null
        vignettePaint.alpha = 255
    }

    fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        round(c, l, t, r, b, radius, Palette.PANEL)
        roundStroke(c, l, t, r, b, radius, Palette.PANEL_EDGE, 2f)
    }
}
