package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Camera
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.sin

/** Wall panel that opens the intake breaker puzzle and, once solved, a gate. */
class BreakerPanel(box: Box, private val gateId: String) : Prop(box) {
    var solved = false

    override fun interactLabel(g: GameSession): String = if (solved) "" else "REROUTE"

    override fun onInteract(g: GameSession) {
        if (solved) return
        g.openOverlay(BreakerPuzzle(g.puzzleSeed)) { success ->
            if (success) {
                solved = true
                g.onBreakersSolved(gateId)
            }
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        d.round(c, l, t, r, b, cam.s(0.08f), Palette.WALL_LIT)
        d.roundStroke(c, l, t, r, b, cam.s(0.08f), Palette.TRIM, 2f)
        val tint = if (solved) Palette.GOOD else Palette.WARN
        var i = 0
        while (i < 3) {
            val y = t + (b - t) * (0.25f + i * 0.25f)
            val on = solved || i == 1
            d.line(c, l + cam.s(0.1f), y, r - cam.s(0.1f), y,
                Palette.withAlpha(if (on) tint else Palette.BAD, 0.8f), cam.s(0.05f))
            i++
        }
        val pulse = if (solved) 1f else 0.5f + 0.5f * sin(g.time * 4f)
        d.glow(c, (l + r) * 0.5f, t - cam.s(0.18f), cam.s(0.24f), tint, pulse)
    }
}

/** The key pack, lying where the last technician dropped it. */
class KeyPackItem(box: Box) : Prop(box) {
    var taken = false

    override fun interactLabel(g: GameSession): String = if (taken) "" else "TAKE"

    override fun onInteract(g: GameSession) {
        if (taken) return
        taken = true
        g.onKeyPackTaken()
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        if (taken) return
        val bob = sin(g.time * 2.2f) * cam.s(0.04f)
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t) + bob; val b = cam.sy(box.b) + bob
        d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(0.5f), Palette.ACCENT, 0.6f)
        d.round(c, l, t, r, b, cam.s(0.06f), Palette.WALL_LIT)
        d.roundStroke(c, l, t, r, b, cam.s(0.06f), Palette.ACCENT, 2.5f)
        d.rect(c, l + cam.s(0.06f), t + cam.s(0.06f), r - cam.s(0.06f), t + cam.s(0.16f),
            Palette.withAlpha(Palette.ACCENT, 0.7f))
        d.circle(c, (l + r) * 0.5f, b - cam.s(0.12f), cam.s(0.05f), Palette.BAD)
    }
}

/** Heavy feeder cable the player hauls between rooms. */
class CableCoil(box: Box, val id: String) : Prop(box) {
    var held = false
    var consumed = false
    /** Where the run is anchored. The cable visibly pays out from here. */
    val anchorX: Float = box.cx
    val anchorY: Float = box.cy

    // A held coil sits in the player's hands, so it would always be the
    // nearest prop and would hide the station's THROW prompt behind it.
    override fun interactLabel(g: GameSession): String = when {
        consumed || held -> ""
        g.carriedCable != null -> ""
        else -> "GRAB"
    }

    override fun onInteract(g: GameSession) {
        if (consumed || held) return
        g.grabCable(this)
    }

    override fun update(g: GameSession, dt: Float) {
        if (held) {
            // Ride on the player's hands so the throw target check stays honest.
            val hx = g.player.handX()
            val hy = g.player.handY()
            box.set(hx - 0.34f, hy - 0.30f, hx + 0.34f, hy + 0.30f)
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        if (consumed) return
        val cx = cam.sx(box.cx)
        val cy = cam.sy(box.cy)
        val rr = cam.s(0.34f)
        d.circle(c, cx, cy, rr, Palette.mix(Palette.WALL_LIT, Palette.VOID, 0.35f))
        d.circleStroke(c, cx, cy, rr * 0.92f, Palette.WARN, cam.s(0.06f))
        d.circleStroke(c, cx, cy, rr * 0.62f, Palette.withAlpha(Palette.WARN, 0.6f), cam.s(0.05f))
        d.circle(c, cx, cy, rr * 0.24f, Palette.withAlpha(Palette.VOID, 0.8f))
        if (!held) d.glow(c, cx, cy, rr * 1.5f, Palette.WARN, 0.35f)
    }
}

/**
 * Wall socket the cable gets thrown into. Mounted high on purpose: the throw is
 * the beat, not the walk.
 */
class ConnectionStation(
    box: Box,
    val id: String,
    /** Awards a carryable rather than powering a floor. */
    val yieldsShard: Boolean = false,
    /** What the prompt says when that carryable is ready to take. */
    val shardLabel: String = "TAKE SHARD"
) : Prop(box) {
    var connected = false
    /** Where the seated cable runs back to, so it can be drawn for good. */
    var anchorX = Float.NaN
    var anchorY = Float.NaN
    var shardTaken = false
    /** 0 -> 1 while the thrown cable flies up to the socket. */
    var throwAnim = 0f
    private var throwing = false
    private var fromX = 0f
    private var fromY = 0f

    override val reach: Float = 2.6f

    override fun interactLabel(g: GameSession): String = when {
        throwing -> ""
        connected && yieldsShard && !shardTaken -> shardLabel
        connected -> ""
        g.carriedCable != null -> "THROW"
        else -> ""
    }

    override fun onInteract(g: GameSession) {
        if (throwing) return
        if (connected) {
            if (yieldsShard && !shardTaken) {
                shardTaken = true
                g.onShardTaken()
            }
            return
        }
        val cable = g.carriedCable ?: return
        fromX = cable.box.cx
        fromY = cable.box.cy
        anchorX = cable.anchorX
        anchorY = cable.anchorY
        throwing = true
        throwAnim = 0f
        g.onCableThrown(this)
    }

    override fun update(g: GameSession, dt: Float) {
        if (throwing) {
            throwAnim += dt * 1.5f
            if (throwAnim >= 1f) {
                throwAnim = 1f
                throwing = false
                connected = true
                g.onStationConnected(this)
            }
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        val tint = if (connected) Palette.GOOD else Palette.WARN

        // The seated run, drawn back to wherever the coil was lifted from.
        if (connected && !anchorX.isNaN()) {
            com.expstudio.facilitycore.core.Art.cable(
                c, d,
                cam.sx(anchorX), cam.sy(anchorY),
                (l + r) * 0.5f, (t + b) * 0.5f,
                cam.s(0.55f), cam.s(0.12f)
            )
        }

        com.expstudio.facilitycore.core.Art.plate(
            c, d, l, t, r, b,
            Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.45f),
            Palette.mix(Palette.WALL, Palette.VOID, 0.4f),
            Palette.mix(Palette.TRIM, tint, 0.3f),
            cam.scale, box.l
        )
        d.roundStroke(c, l, t, r, b, cam.s(0.08f), Palette.withAlpha(tint, 0.8f), 2.5f)
        // Three sockets; they fill in as the cable seats.
        var i = 0
        while (i < 3) {
            val cx = l + (r - l) * (0.25f + i * 0.25f)
            val cy = (t + b) * 0.5f
            d.circle(c, cx, cy, cam.s(0.09f), Palette.withAlpha(Palette.VOID, 0.85f))
            if (connected) d.circle(c, cx, cy, cam.s(0.06f), tint)
            i++
        }
        if (connected) d.glow(c, (l + r) * 0.5f, (t + b) * 0.5f, cam.s(0.7f), tint, 0.8f)

        if (throwing) {
            // Arc the cable up to the socket.
            val p = MathX.smoothStep(throwAnim)
            val tx = box.cx
            val ty = box.cy
            val cx = cam.sx(MathX.lerp(fromX, tx, p))
            val arc = -1.4f * (1f - (2f * p - 1f) * (2f * p - 1f))
            val cy = cam.sy(MathX.lerp(fromY, ty, p) + arc)
            d.line(c, cam.sx(fromX), cam.sy(fromY), cx, cy, Palette.withAlpha(Palette.WARN, 0.8f), cam.s(0.07f))
            d.circle(c, cx, cy, cam.s(0.18f), Palette.WARN)
        }

        if (connected && yieldsShard && !shardTaken) {
            val bob = sin(g.time * 3f) * cam.s(0.06f)
            val sx = (l + r) * 0.5f
            val sy = b + cam.s(0.55f) + bob
            d.glow(c, sx, sy, cam.s(0.4f), Palette.ACCENT, 0.9f)
            d.poly(
                c,
                floatArrayOf(
                    sx, sy - cam.s(0.20f),
                    sx + cam.s(0.16f), sy,
                    sx, sy + cam.s(0.20f),
                    sx - cam.s(0.16f), sy
                ),
                Palette.ACCENT
            )
        }
    }
}

/** Archive panel that writes the elevator's key into the pack. */
class ArchivePanel(box: Box) : Prop(box) {
    var uploading = false
    var uploadTime = 0f
    var done = false
    var packRetrieved = false

    override val reach: Float = 1.7f

    override fun interactLabel(g: GameSession): String = when {
        uploading -> ""
        done && !packRetrieved -> "TAKE PACK"
        done -> ""
        g.circuitAssembled -> "INSERT"
        else -> ""
    }

    override fun onInteract(g: GameSession) {
        if (uploading) return
        if (done) {
            if (!packRetrieved) {
                packRetrieved = true
                g.onPackRetrieved()
            }
            return
        }
        if (!g.circuitAssembled) return
        uploading = true
        uploadTime = 0f
        g.onUploadStarted()
    }

    override fun update(g: GameSession, dt: Float) {
        if (uploading) {
            uploadTime += dt
            if (uploadTime >= UPLOAD_SECONDS) {
                uploading = false
                done = true
                g.onUploadFinished()
            }
        }
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        d.round(c, l, t, r, b, cam.s(0.1f), Palette.WALL_LIT)
        d.roundStroke(c, l, t, r, b, cam.s(0.1f), Palette.TRIM, 2.5f)

        val screenL = l + cam.s(0.14f)
        val screenR = r - cam.s(0.14f)
        val screenT = t + cam.s(0.14f)
        val screenB = b - cam.s(0.5f)
        d.rect(c, screenL, screenT, screenR, screenB, Palette.withAlpha(Palette.VOID, 0.9f))

        when {
            uploading -> {
                val p = (uploadTime / UPLOAD_SECONDS).coerceIn(0f, 1f)
                // Scrolling data rows plus a progress bar.
                var i = 0
                while (i < 6) {
                    val rowY = screenT + (screenB - screenT) * ((i / 6f + g.time * 0.35f) % 1f)
                    val wdt = (screenR - screenL) * (0.25f + 0.6f * ((i * 37 % 10) / 10f))
                    d.rect(c, screenL + cam.s(0.08f), rowY, screenL + cam.s(0.08f) + wdt, rowY + cam.s(0.05f),
                        Palette.withAlpha(Palette.ACCENT, 0.5f))
                    i++
                }
                d.rect(c, screenL + cam.s(0.08f), screenB - cam.s(0.22f),
                    screenL + cam.s(0.08f) + (screenR - screenL - cam.s(0.16f)) * p, screenB - cam.s(0.10f), Palette.ACCENT)
                d.textCentered(c, "WRITING KEY ${(p * 100).toInt()}%", (l + r) * 0.5f, screenT - cam.s(0.24f),
                    cam.s(0.24f), Palette.ACCENT, true)
                d.glow(c, (l + r) * 0.5f, (screenT + screenB) * 0.5f, cam.s(0.9f), Palette.ACCENT, 0.7f)
            }
            done -> {
                d.textCentered(c, "KEY WRITTEN", (l + r) * 0.5f, (screenT + screenB) * 0.5f, cam.s(0.26f), Palette.GOOD, true)
                if (!packRetrieved) {
                    val bob = sin(g.time * 2.5f) * cam.s(0.05f)
                    d.glow(c, (l + r) * 0.5f, b + cam.s(0.5f) + bob, cam.s(0.45f), Palette.ACCENT, 0.9f)
                    d.round(c, (l + r) * 0.5f - cam.s(0.2f), b + cam.s(0.3f) + bob,
                        (l + r) * 0.5f + cam.s(0.2f), b + cam.s(0.7f) + bob, cam.s(0.06f), Palette.WALL_LIT)
                    d.roundStroke(c, (l + r) * 0.5f - cam.s(0.2f), b + cam.s(0.3f) + bob,
                        (l + r) * 0.5f + cam.s(0.2f), b + cam.s(0.7f) + bob, cam.s(0.06f), Palette.ACCENT, 2f)
                }
            }
            else -> {
                val ready = g.circuitAssembled
                d.textCentered(c, if (ready) "INSERT CIRCUIT" else "CIRCUIT MISSING",
                    (l + r) * 0.5f, (screenT + screenB) * 0.5f, cam.s(0.22f),
                    if (ready) Palette.WARN else Palette.TEXT_DIM, true)
                // Slot.
                d.rect(c, (l + r) * 0.5f - cam.s(0.26f), screenB + cam.s(0.10f),
                    (l + r) * 0.5f + cam.s(0.26f), screenB + cam.s(0.20f),
                    Palette.withAlpha(if (ready) Palette.WARN else Palette.TEXT_DIM, 0.8f))
            }
        }
    }

    companion object { const val UPLOAD_SECONDS = 4.2f }
}

/** Elevator car: the chapter's goal and its mid-point wall. */
class Elevator(box: Box) : Prop(box) {
    var doorsOpen = false
    var doorAmount = 0f
        private set
    var descending = false

    override val reach: Float = 1.8f

    override fun interactLabel(g: GameSession): String = when {
        g.stage >= Stage.ENDING -> ""
        doorsOpen -> "DESCEND"
        else -> "USE"
    }

    override fun onInteract(g: GameSession) {
        if (g.stage >= Stage.ENDING) return
        if (doorsOpen) { g.onElevatorBoard(); return }
        g.onElevatorUse(this)
    }

    override fun update(g: GameSession, dt: Float) {
        doorAmount = MathX.moveToward(doorAmount, if (doorsOpen) 1f else 0f, dt * 0.8f)
    }

    override fun draw(c: Canvas, d: Draw, cam: Camera, g: GameSession) {
        val l = cam.sx(box.l); val r = cam.sx(box.r)
        val t = cam.sy(box.t); val b = cam.sy(box.b)
        // Shaft recess.
        d.rect(c, l, t, r, b, Palette.withAlpha(Palette.VOID, 0.92f))
        d.roundStroke(c, l, t, r, b, cam.s(0.05f), Palette.TRIM, 3f)

        if (doorAmount > 0.02f) {
            // Interior visible through the opening.
            d.rect(c, l + cam.s(0.1f), t + cam.s(0.1f), r - cam.s(0.1f), b, Palette.mix(Palette.WALL_LIT, Palette.ACCENT_DIM, 0.25f))
        }
        val half = (r - l) * 0.5f
        val slide = half * doorAmount
        d.rect(c, l, t, l + half - slide, b, Palette.mix(Palette.TRIM, Palette.WALL_LIT, 0.4f))
        d.rect(c, r - half + slide, t, r, b, Palette.mix(Palette.TRIM, Palette.WALL_LIT, 0.4f))
        d.line(c, l + half - slide, t, l + half - slide, b, Palette.withAlpha(Palette.VOID, 0.8f), 3f)
        d.line(c, r - half + slide, t, r - half + slide, b, Palette.withAlpha(Palette.VOID, 0.8f), 3f)

        // Call plate.
        val px = r + cam.s(0.35f)
        val py = t + cam.s(0.85f)
        val tint = if (g.keyPackHasElevator) Palette.GOOD else Palette.BAD
        d.round(c, px - cam.s(0.14f), py - cam.s(0.22f), px + cam.s(0.14f), py + cam.s(0.22f), cam.s(0.05f), Palette.WALL_LIT)
        d.circle(c, px, py, cam.s(0.08f), tint)
        d.glow(c, px, py, cam.s(0.28f), tint, 0.7f)

        d.textCentered(c, "SUBFLOOR 1", (l + r) * 0.5f, t - cam.s(0.35f), cam.s(0.28f),
            Palette.withAlpha(Palette.TEXT_DIM, 0.9f), true)
    }
}

