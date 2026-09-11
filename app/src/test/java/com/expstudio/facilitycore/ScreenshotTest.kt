package com.expstudio.facilitycore

import android.graphics.Bitmap
import android.graphics.Canvas
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.game.GameSession
import com.expstudio.facilitycore.game.Stage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Rasterises real frames to PNG so the art direction can be looked at rather
 * than imagined. Not an assertion test; it exists to make the renderer
 * inspectable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {

    private val w = 1920
    private val h = 1080

    private fun shoot(name: String, stage: Int, settle: Float = 0.6f, prepare: (GameSession) -> Unit = {}) {
        val g = Bot.session(stage)
        prepare(g)
        var t = 0f
        while (t < settle) { g.update(1f / 60f, 0f, false, false, false); t += 1f / 60f }

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        g.render(canvas, Draw(), w.toFloat(), h.toFloat())

        val out = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("wrote build/screenshots/$name.png")
    }

    @Test
    fun captureFrames() {
        // The exact room from the bug report: unpowered, hauling-cable objective.
        shoot("hub_dark", Stage.POWER_SUBFLOOR) { g ->
            g.enterRoomForTest("hub", 8.2f)
        }
        shoot("lobby_lit", Stage.LOBBY_PUZZLE)
        shoot("sealed_climb", Stage.UNLOCK_FIRST_DOOR)
        shoot("archive_chase", Stage.ELEVATOR_DENIED) { g ->
            g.enterRoomForTest("archive", 18f)
        }
        shoot("circuit_lit", Stage.PANEL_UPLOADED)
    }
}
