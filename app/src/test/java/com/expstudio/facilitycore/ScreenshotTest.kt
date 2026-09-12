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

    private fun shoot(
        name: String,
        stage: Int,
        settle: Float = 0.6f,
        chapter: Int = 1,
        prepare: (GameSession) -> Unit = {}
    ) {
        val g = Bot.session(stage, chapter)
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

    /** Chapter 3: the rooms that carry the chapter's look and its mechanic. */
    @Test
    fun captureChapter3Frames() {
        shoot("ch3_archive", com.expstudio.facilitycore.game.Stage3.ARCHIVE, chapter = 3, settle = 1.2f) { g ->
            g.enterRoomForTest("archive3", 12f, -4f)
        }
        shoot("ch3_span", com.expstudio.facilitycore.game.Stage3.SHAFT, chapter = 3, settle = 0.9f) { g ->
            g.reach.unlocked = true
            g.enterRoomForTest("deepspan", 8f, -1.9f)
        }
        // The hand mid-stretch, which is the chapter's whole silhouette.
        shoot("ch3_reach", com.expstudio.facilitycore.game.Stage3.SHAFT, chapter = 3, settle = 0.25f) { g ->
            g.reach.unlocked = true
            g.enterRoomForTest("shaft3", 4.5f, -3.4f)
            g.update(1f / 60f, 0f, false, false, false, false, true, true)
        }
        shoot("ch3_core", com.expstudio.facilitycore.game.Stage3.CORE_LIT, chapter = 3, settle = 1.0f) { g ->
            g.enterRoomForTest("core3", 20f, 0f)
        }
        shoot("ch3_pit", com.expstudio.facilitycore.game.Stage3.PIT, chapter = 3, settle = 0.9f) { g ->
            g.enterRoomForTest("pit3", 11f, 0f)
        }
        shoot("ch3_boiler", com.expstudio.facilitycore.game.Stage3.DEEP, chapter = 3, settle = 0.9f) { g ->
            g.enterRoomForTest("e13", 12f, 0f)
        }
    }

    /** Frames from the closing scene, which has no GameSession behind it. */
    @Test
    fun captureEndingFrames() {
        val scene = com.expstudio.facilitycore.game.ElevatorScene()
        val shots = listOf(
            "ending_1_descent" to 0.30f,
            "ending_2_impact" to 0.575f,
            "ending_3_tear" to 0.745f,
            "ch2_1_hands" to 0.875f,
            "ch2_2_widen" to 0.925f,
            "ch2_3_drop" to 0.975f
        )
        val finale = com.expstudio.facilitycore.game.SmelterScene()
        for ((name, p) in listOf("ch2_end_1_slam" to 0.30f, "ch2_end_2_fall" to 0.62f)) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            finale.draw(Canvas(bmp), Draw(), w.toFloat(), h.toFloat(), 2.4f, p)
            val dir = File("build/screenshots").apply { mkdirs() }
            FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val out = File("build/screenshots").apply { mkdirs() }
        for ((name, p) in shots) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val mode = if (name.startsWith("ch2")) com.expstudio.facilitycore.game.ElevatorScene.Mode.CH2_OPENING
            else com.expstudio.facilitycore.game.ElevatorScene.Mode.CH1_ENDING
            scene.draw(canvas, Draw(), w.toFloat(), h.toFloat(), 3.2f, p, mode)
            FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** The menu's living backdrop, at two moments. */
    @Test
    fun captureMenuFrames() {
        val backdrop = com.expstudio.facilitycore.ui.MenuBackdrop()
        val out = File("build/screenshots").apply { mkdirs() }
        for ((name, t) in listOf("menu_1" to 3.0f, "menu_2_watched" to 8.0f)) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            backdrop.draw(Canvas(bmp), w.toFloat(), h.toFloat(), t)
            FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
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
        // The collapsed bulkhead that stranded a player: it has to read as a
        // crawl from across the room, at a run.
        shoot("maze2_crawl", Stage.PANEL_UPLOADED) { g ->
            g.enterRoomForTest("maze2", 3.4f)
        }

        // ---- Chapter 2 ----
        shoot("ch2_car_fight", com.expstudio.facilitycore.game.Stage2.LIFT_FIGHT, settle = 1.4f, chapter = 2)
        shoot("ch2_hall", com.expstudio.facilitycore.game.Stage2.MEET_PLAYERR, chapter = 2) { g ->
            g.enterRoomForTest("hall", 14f)
            g.monster.kind = com.expstudio.facilitycore.game.Monster.Kind.PLAYERR
            g.monster.mode = com.expstudio.facilitycore.game.Monster.Mode.ALERTED
            g.monster.place("hall", 20f, 0f, -1)
        }
        shoot("ch2_smelter_duct", com.expstudio.facilitycore.game.Stage2.SMELTER_DOOR, chapter = 2) { g ->
            g.enterRoomForTest("vent2", 22f)
        }
        shoot("ch2_feed", com.expstudio.facilitycore.game.Stage2.FEED_ROOM, chapter = 2) { g ->
            g.enterRoomForTest("feed", 6f)
        }
    }
}
