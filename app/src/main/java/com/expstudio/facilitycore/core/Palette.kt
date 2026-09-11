package com.expstudio.facilitycore.core

import android.graphics.Color

/**
 * A deliberately tiny palette: the low-poly look depends on flat fills reusing
 * the same handful of values rather than per-object colours.
 */
object Palette {
    const val VOID = 0xFF05070A.toInt()
    const val BG_FAR = 0xFF0B0F14.toInt()
    const val BG_NEAR = 0xFF121822.toInt()

    const val WALL = 0xFF1B2430.toInt()
    const val WALL_LIT = 0xFF2A3646.toInt()
    const val FLOOR = 0xFF232E3C.toInt()
    const val FLOOR_EDGE = 0xFF33425A.toInt()
    const val TRIM = 0xFF3D4E68.toInt()

    const val PLAYER = 0xFFF2F5F8.toInt()
    const val PLAYER_SHADE = 0xFFBFC8D4.toInt()

    const val MONSTER = 0xFF120A18.toInt()
    const val MONSTER_CRACK = 0xFF7B2CBF.toInt()
    const val MONSTER_EYE = 0xFFFF2D3A.toInt()

    const val ACCENT = 0xFF46D2C8.toInt()
    const val ACCENT_DIM = 0xFF1E6B68.toInt()
    const val WARN = 0xFFFFB03A.toInt()
    const val BAD = 0xFFFF4B5C.toInt()
    const val GOOD = 0xFF5BE37A.toInt()

    const val TEXT = 0xFFE8EDF2.toInt()
    const val TEXT_DIM = 0xFF8A97A8.toInt()
    const val PANEL = 0xE6101720.toInt()
    const val PANEL_EDGE = 0xFF2E3C50.toInt()

    /** Wire colours used by every wiring puzzle so the language stays consistent. */
    val WIRE = intArrayOf(
        0xFFFF6B6B.toInt(),
        0xFF4DA8FF.toInt(),
        0xFFFFD166.toInt(),
        0xFF7BE495.toInt()
    )

    fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb(
            MathX.clamp(alpha * 255f, 0f, 255f).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )

    fun mix(a: Int, b: Int, t: Float): Int {
        val f = MathX.clamp(t, 0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
        )
    }
}
