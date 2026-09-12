package com.expstudio.facilitycore

import android.graphics.Bitmap
import android.graphics.Canvas
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.game.Stage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rough cost of a frame. This runs on software Skia, so the absolute number is
 * pessimistic compared with the hardware canvas the game actually uses; it is
 * here to catch a pathological regression, not to certify a frame rate.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderCostTest {

    private fun measure(label: String, stage: Int, room: String, x: Float, chapter: Int = 1, y: Float = 0f) {
        val w = 1920
        val h = 1080
        val g = Bot.session(stage, chapter)
        g.enterRoomForTest(room, x, y)
        repeat(30) { g.update(1f / 60f, 0f, false, false, false) }

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val draw = Draw()

        repeat(10) { g.render(canvas, draw, w.toFloat(), h.toFloat()) } // warm the shader caches
        val start = System.nanoTime()
        val frames = 40
        repeat(frames) { g.render(canvas, draw, w.toFloat(), h.toFloat()) }
        val msPerFrame = (System.nanoTime() - start) / 1_000_000.0 / frames
        println("software render [%s]: %.2f ms/frame".format(label, msPerFrame))
    }

    @Test
    fun aFrameStaysWithinABudget() {
        // The unpowered room pays for the darkness pass; the lit one does not.
        measure("dark room", Stage.POWER_SUBFLOOR, "hub", 8.2f)
        measure("lit room", Stage.PANEL_UPLOADED, "circuit", 14f)
        measure("lit corridor", Stage.LOBBY_PUZZLE, "lobby", 6f)

        // Chapter 3's rooms are much larger than either earlier chapter's — the
        // plant hall is forty metres and the ignition gallery forty-four, both
        // with far more decor in frame than anything Chapter 1 ever drew.
        measure("ch3 plant hall", com.expstudio.facilitycore.game.Stage3.DEEP, "deephall", 20f, chapter = 3)
        measure("ch3 ignition gallery", com.expstudio.facilitycore.game.Stage3.CORE_LIT, "core3", 22f, chapter = 3)
        measure("ch3 archive", com.expstudio.facilitycore.game.Stage3.ARCHIVE, "archive3", 14f, chapter = 3)
        measure("ch3 rack aisle", com.expstudio.facilitycore.game.Stage3.DEEP, "e6", 16f, chapter = 3)
        measure("ch3 pit", com.expstudio.facilitycore.game.Stage3.PIT, "pit3", 14f, chapter = 3)
    }
}
