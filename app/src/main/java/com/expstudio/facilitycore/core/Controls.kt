package com.expstudio.facilitycore.core

import android.graphics.Canvas
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot

/** A round on-screen button laid out in screen pixels. */
class TouchButton(var label: String) {
    var cx = 0f
    var cy = 0f
    var radius = 0f
    var visible = true
    var enabled = true
    var held = false
        private set
    private var pointerId = -1
    private var pressedEdge = false
    /** Pulses 1 -> 0 after a press so the button can flash without extra state. */
    var flash = 0f
        private set

    fun contains(x: Float, y: Float): Boolean {
        if (!visible || !enabled) return false
        // Generous hit slop: thumbs are imprecise and the buttons are safety-critical.
        return hypot(x - cx, y - cy) <= radius * 1.35f
    }

    fun press(id: Int) {
        if (pointerId != -1) return
        pointerId = id
        held = true
        pressedEdge = true
        flash = 1f
    }

    fun release(id: Int) {
        if (pointerId == id) { pointerId = -1; held = false }
    }

    fun ownsPointer(id: Int): Boolean = pointerId == id

    /** True exactly once per press. */
    fun consumePress(): Boolean {
        val p = pressedEdge
        pressedEdge = false
        return p
    }

    fun update(dt: Float) {
        if (flash > 0f) flash = (flash - dt * 3.5f).coerceAtLeast(0f)
    }

    fun reset() { pointerId = -1; held = false; pressedEdge = false; flash = 0f }
}

/**
 * Virtual gamepad: a floating stick anywhere on the left half plus action
 * buttons on the right. Buttons are claimed before the stick so an action tap
 * that lands near the divider never steals movement.
 */
class Controls {
    val jump = TouchButton("JUMP")
    val sneak = TouchButton("SNEAK")
    val interact = TouchButton("USE")
    /** Chapter 2's roll. Hidden until a chapter turns it on. */
    val dodge = TouchButton("DODGE").apply { visible = false }
    /** Chapter 3's extendable hand. Held, not tapped: most anchors take strain. */
    val reach = TouchButton("REACH").apply { visible = false }

    var moveX = 0f
        private set
    var sneakHeld = false
        private set

    var stickActive = false
        private set
    var stickOriginX = 0f
        private set
    var stickOriginY = 0f
        private set
    var stickX = 0f
        private set
    var stickY = 0f
        private set

    private var stickPointer = -1
    private var screenW = 0f
    private var screenH = 0f
    private var stickRadius = 0f

    /** Scales the whole control layer, driven by the settings screen. */
    var uiScale = 1f
    /** Mirrors the stick and the action buttons for left-handed play. */
    var leftHanded = false

    fun layout(widthPx: Int, heightPx: Int) {
        screenW = widthPx.toFloat()
        screenH = heightPx.toFloat()
        val unit = MathX.min(screenW * 0.5f, screenH) 
        val r = unit * 0.115f * uiScale
        stickRadius = unit * 0.16f * uiScale

        jump.radius = r
        sneak.radius = r * 0.86f
        interact.radius = r * 0.98f
        dodge.radius = r * 0.86f
        reach.radius = r * 0.90f

        val margin = r * 1.35f
        // Mirror every x about the screen centre when left-handed.
        fun px(fromRight: Float): Float = if (leftHanded) screenW - fromRight else fromRight
        jump.cx = px(screenW - margin)
        jump.cy = screenH - margin
        sneak.cx = px(screenW - margin - r * 2.25f)
        sneak.cy = screenH - margin * 0.72f
        interact.cx = px(screenW - margin * 0.95f)
        interact.cy = screenH - margin - r * 2.85f
        // Above SNEAK, clear of every other hit area.
        dodge.cx = px(screenW - margin - r * 2.35f)
        dodge.cy = screenH - margin - r * 2.6f
        // Left of USE and above DODGE: the hand is aimed, so it wants the
        // steadiest part of the thumb's arc, not the corner.
        reach.cx = px(screenW - margin - r * 4.35f)
        reach.cy = screenH - margin - r * 1.35f
    }

    fun reset() {
        stickPointer = -1
        stickActive = false
        moveX = 0f
        sneakHeld = false
        jump.reset(); sneak.reset(); interact.reset(); dodge.reset(); reach.reset()
    }

    fun update(dt: Float) {
        jump.update(dt); sneak.update(dt); interact.update(dt); dodge.update(dt); reach.update(dt)
        sneakHeld = sneak.held
    }

    /** Returns true when the event was consumed by the control layer. */
    fun onTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                claim(e.getPointerId(idx), e.getX(idx), e.getY(idx))
            }
            MotionEvent.ACTION_MOVE -> {
                var i = 0
                while (i < e.pointerCount) {
                    val id = e.getPointerId(i)
                    if (id == stickPointer) updateStick(e.getX(i), e.getY(i))
                    i++
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = e.actionIndex
                release(e.getPointerId(idx))
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    private fun claim(id: Int, x: Float, y: Float) {
        if (jump.contains(x, y)) { jump.press(id); return }
        if (reach.contains(x, y)) { reach.press(id); return }
        if (dodge.contains(x, y)) { dodge.press(id); return }
        if (sneak.contains(x, y)) { sneak.press(id); return }
        if (interact.contains(x, y)) { interact.press(id); return }
        val onStickSide = if (leftHanded) x > screenW * 0.45f else x < screenW * 0.55f
        if (stickPointer == -1 && onStickSide) {
            stickPointer = id
            stickActive = true
            stickOriginX = x
            stickOriginY = y
            stickX = x
            stickY = y
            moveX = 0f
        }
    }

    private fun updateStick(x: Float, y: Float) {
        stickX = x
        stickY = y
        val dx = x - stickOriginX
        // Let the origin trail the thumb so long drags never run out of travel.
        if (abs(dx) > stickRadius) {
            stickOriginX = x - Math.signum(dx) * stickRadius
        }
        val nx = MathX.clamp((x - stickOriginX) / stickRadius, -1f, 1f)
        // Small dead zone stops idle thumb jitter from nudging the player.
        moveX = if (abs(nx) < 0.18f) 0f else (nx - Math.signum(nx) * 0.18f) / 0.82f
    }

    private fun release(id: Int) {
        jump.release(id); sneak.release(id); interact.release(id); dodge.release(id); reach.release(id)
        if (id == stickPointer) {
            stickPointer = -1
            stickActive = false
            moveX = 0f
        }
    }

    fun draw(c: Canvas, d: Draw) {
        if (stickActive) {
            d.circleStroke(c, stickOriginX, stickOriginY, stickRadius, Palette.withAlpha(Palette.TEXT, 0.22f), 3f)
            val dx = MathX.clamp(stickX - stickOriginX, -stickRadius, stickRadius)
            val dy = MathX.clamp(stickY - stickOriginY, -stickRadius, stickRadius)
            d.circle(c, stickOriginX + dx, stickOriginY + dy, stickRadius * 0.42f, Palette.withAlpha(Palette.TEXT, 0.30f))
        }
        drawButton(c, d, jump, Palette.ACCENT)
        drawButton(c, d, sneak, Palette.TEXT_DIM)
        drawButton(c, d, interact, Palette.WARN)
        drawButton(c, d, dodge, Palette.GOOD)
        drawButton(c, d, reach, Palette.ACCENT)
        // A ring around REACH when something is actually in range, so the player
        // learns where anchors are without a single word of instruction.
        if (reach.visible && reachTargeted) {
            d.circleStroke(c, reach.cx, reach.cy, reach.radius * 1.20f,
                Palette.withAlpha(Palette.ACCENT, 0.55f), 4f)
        }
        // A sweep around DODGE showing the cooldown refilling.
        if (dodge.visible && dodgeCharge < 1f) {
            d.circleStroke(
                c, dodge.cx, dodge.cy, dodge.radius * 1.18f,
                Palette.withAlpha(Palette.GOOD, 0.25f + 0.4f * dodgeCharge), 4f
            )
        }
    }

    /** 0..1, mirrored from the player so the button can show its cooldown. */
    var dodgeCharge = 1f
    /** True when the hand has something to grab; mirrored from the session. */
    var reachTargeted = false

    private fun drawButton(c: Canvas, d: Draw, b: TouchButton, tint: Int) {
        if (!b.visible) return
        val alpha = if (b.enabled) 1f else 0.35f
        val pressBoost = if (b.held) 0.22f else b.flash * 0.18f
        d.circle(c, b.cx, b.cy, b.radius, Palette.withAlpha(0xFF000000.toInt(), 0.28f * alpha))
        d.circle(c, b.cx, b.cy, b.radius, Palette.withAlpha(tint, (0.14f + pressBoost) * alpha))
        d.circleStroke(c, b.cx, b.cy, b.radius, Palette.withAlpha(tint, (0.55f + pressBoost) * alpha), 3f)
        d.textCentered(c, b.label, b.cx, b.cy, b.radius * 0.42f, Palette.withAlpha(Palette.TEXT, 0.92f * alpha), true)
    }
}
