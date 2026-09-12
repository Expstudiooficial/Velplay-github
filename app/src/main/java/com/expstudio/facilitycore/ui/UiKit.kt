package com.expstudio.facilitycore.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.expstudio.facilitycore.core.Palette

/**
 * The menu is built in code rather than XML: it is a handful of stacked views
 * and this keeps the styling in one place next to the game's palette.
 */
object UiKit {

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    /** Edge-to-edge, sticky immersive, screen kept awake. */
    fun goFullscreen(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val decor = activity.window.decorView
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun rounded(fillColor: Int, strokeColor: Int, radiusDp: Float, context: Context, strokeDp: Float = 1.5f): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(fillColor)
            setStroke(dp(context, strokeDp), strokeColor)
        }

    /**
     * A pressed-steel button: dark fill, a lit edge, and a hairline of the
     * accent across the top so it reads as part of the facility rather than a
     * stock Android control.
     */
    fun button(context: Context, label: String, tint: Int = Palette.ACCENT, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            isAllCaps = true
            setTextColor(Palette.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            background = plated(context, tint)
            setPadding(dp(context, 22f), dp(context, 15f), dp(context, 22f), dp(context, 15f))
            stateListAnimator = null
            setOnClickListener { onClick() }
        }

    /** Layered drawable: body, lit top edge, and a selectable highlight. */
    fun plated(context: Context, tint: Int, radiusDp: Float = 12f): android.graphics.drawable.Drawable {
        val body = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            colors = intArrayOf(
                Palette.mix(Palette.WALL_LIT, tint, 0.16f),
                Palette.mix(Palette.WALL, Palette.VOID, 0.35f)
            )
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dp(context, 1.5f), Palette.withAlpha(tint, 0.75f))
        }
        val lip = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(Palette.withAlpha(tint, 0.30f))
        }
        val layers = android.graphics.drawable.LayerDrawable(arrayOf(body, lip))
        // The lip is a two-pixel sliver along the top of the body.
        layers.setLayerInset(1, dp(context, 6f), 0, dp(context, 6f), dp(context, 44f))
        val states = android.graphics.drawable.StateListDrawable()
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(Palette.withAlpha(tint, 0.30f))
            setStroke(dp(context, 2f), Palette.withAlpha(tint, 1f))
        }
        states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
        states.addState(intArrayOf(), layers)
        return states
    }

    fun smallButton(context: Context, label: String, tint: Int, onClick: () -> Unit): Button =
        button(context, label, tint, onClick).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(context, 12f), dp(context, 8f), dp(context, 12f), dp(context, 8f))
        }

    fun title(context: Context, text: String, sizeSp: Float = 34f, color: Int = Palette.TEXT): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.14f
            // A cold bloom behind the lettering, like everything else that
            // glows down here.
            setShadowLayer(dp(context, 14f).toFloat(), 0f, 0f, Palette.withAlpha(Palette.ACCENT, 0.55f))
        }

    fun label(context: Context, text: String, sizeSp: Float = 13f, color: Int = Palette.TEXT_DIM): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        }

    /** [transparent] lets the animated backdrop show through. */
    fun column(
        context: Context,
        gravity: Int = Gravity.CENTER_HORIZONTAL,
        transparent: Boolean = false
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            this.gravity = gravity
            if (!transparent) setBackgroundColor(Palette.VOID)
        }

    fun row(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    fun spacer(context: Context, heightDp: Float): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp))
        }

    fun lp(
        context: Context,
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        marginDp: Float = 0f
    ): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, height).apply {
        val m = dp(context, marginDp)
        setMargins(m, m, m, m)
    }

    fun dim(color: Int, factor: Float): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )
}
