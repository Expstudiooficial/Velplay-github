package com.expstudio.facilitycore.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.expstudio.facilitycore.core.Palette
import com.expstudio.facilitycore.game.Stage
import com.expstudio.facilitycore.game.Stage2
import com.expstudio.facilitycore.save.WorldSave
import com.expstudio.facilitycore.save.WorldStore
import com.expstudio.facilitycore.update.UpdateService

/**
 * Main menu, world slots and settings. One activity swapping its content view,
 * because the three screens share the store and nothing else.
 */
class MenuActivity : AppCompatActivity() {

    private enum class Screen { MAIN, WORLDS, SETTINGS }

    private lateinit var store: WorldStore
    private var screen = Screen.MAIN
    private lateinit var container: FrameLayout
    private var backdrop: MenuBackdropView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WorldStore(this)
        container = FrameLayout(this).apply { setBackgroundColor(Palette.VOID) }
        setContentView(container)
        // The backdrop lives for the whole activity; screens are laid over it.
        backdrop = MenuBackdropView(this)
        container.addView(
            backdrop,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        UiKit.goFullscreen(this)
        show(Screen.MAIN)
        if (store.settings.autoCheckUpdates) UpdateUi.check(this, store, quiet = true)
    }

    override fun onResume() {
        super.onResume()
        UiKit.goFullscreen(this)
        // Coming back from a session may have changed progress or unlocks.
        if (screen != Screen.MAIN) show(screen)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (screen != Screen.MAIN) show(Screen.MAIN) else super.onBackPressed()
    }

    private fun show(next: Screen) {
        screen = next
        // Keep the backdrop; only the screen on top of it changes.
        while (container.childCount > 1) container.removeViewAt(1)
        val view = when (next) {
            Screen.MAIN -> buildMain()
            Screen.WORLDS -> buildWorlds()
            Screen.SETTINGS -> buildSettings()
        }
        container.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    // ---- main ------------------------------------------------------------

    private fun buildMain(): View {
        val root = UiKit.column(this, Gravity.CENTER, transparent = true)
        root.setPadding(UiKit.dp(this, 32f), UiKit.dp(this, 24f), UiKit.dp(this, 32f), UiKit.dp(this, 24f))

        root.addView(UiKit.title(this, "FACILITY CORE", 44f))
        root.addView(
            UiKit.label(this, "CHAPTER 1 — SUBFLOOR 0", 13f, Palette.ACCENT),
            UiKit.lp(this, marginDp = 6f)
        )
        root.addView(UiKit.spacer(this, 28f))

        val width = UiKit.dp(this, 260f)
        root.addView(
            UiKit.button(this, "Play") { show(Screen.WORLDS) },
            UiKit.lp(this, width, ViewGroup.LayoutParams.WRAP_CONTENT, 6f)
        )
        root.addView(
            UiKit.button(this, "Settings", Palette.TEXT_DIM) { show(Screen.SETTINGS) },
            UiKit.lp(this, width, ViewGroup.LayoutParams.WRAP_CONTENT, 6f)
        )
        root.addView(
            UiKit.button(this, "Quit", Palette.BAD) { finish() },
            UiKit.lp(this, width, ViewGroup.LayoutParams.WRAP_CONTENT, 6f)
        )

        root.addView(UiKit.spacer(this, 24f))
        root.addView(UiKit.label(this, "EXP Studio", 11f, UiKit.dim(Palette.TEXT_DIM, 0.7f)))
        return root
    }

    // ---- worlds ----------------------------------------------------------

    private fun buildWorlds(): View {
        val root = UiKit.column(this, Gravity.START, transparent = true)
        root.setBackgroundColor(Palette.withAlpha(Palette.VOID, 0.72f))
        root.setPadding(UiKit.dp(this, 24f), UiKit.dp(this, 18f), UiKit.dp(this, 24f), UiKit.dp(this, 18f))

        val worlds = store.list()

        val header = UiKit.row(this)
        header.addView(UiKit.title(this, "WORLDS", 26f))
        header.addView(
            UiKit.label(this, "  ${worlds.size} / ${WorldStore.MAX_SLOTS} slots", 13f, Palette.TEXT_DIM),
            UiKit.lp(this)
        )
        val spacer = View(this)
        header.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(UiKit.smallButton(this, "Back", Palette.TEXT_DIM) { show(Screen.MAIN) })
        root.addView(header, UiKit.lp(this, ViewGroup.LayoutParams.MATCH_PARENT))

        root.addView(UiKit.spacer(this, 12f))
        root.addView(
            UiKit.button(this, if (worlds.size >= WorldStore.MAX_SLOTS) "All slots used" else "Create world") {
                if (store.isFull()) toast("All ${WorldStore.MAX_SLOTS} slots are used. Delete one first.")
                else promptCreate()
            },
            UiKit.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f)
        )
        root.addView(UiKit.spacer(this, 12f))

        val scroll = ScrollView(this)
        val list = UiKit.column(this, Gravity.START)
        if (worlds.isEmpty()) {
            list.addView(
                UiKit.label(this, "No worlds yet. Create one to enter the facility.", 14f),
                UiKit.lp(this, marginDp = 12f)
            )
        } else {
            for (world in worlds) list.addView(worldRow(world))
        }
        scroll.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun worldRow(world: WorldSave): View {
        val card = UiKit.column(this, Gravity.START)
        card.background = UiKit.plated(
            this, if (world.completed) Palette.GOOD else Palette.PANEL_EDGE, 14f
        )
        card.setPadding(UiKit.dp(this, 14f), UiKit.dp(this, 12f), UiKit.dp(this, 14f), UiKit.dp(this, 12f))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = UiKit.dp(this, 10f)
        card.layoutParams = lp

        val name = UiKit.title(this, world.name, 18f)
        name.letterSpacing = 0.04f
        card.addView(name)

        val finalStage = if (world.chapter >= 2) Stage2.COMPLETE else Stage.COMPLETE
        val percent = ((world.stage.toFloat() / finalStage) * 100f).toInt().coerceIn(0, 100)
        val objective = if (world.chapter >= 2) Stage2.objectiveFor(world.stage)
        else Stage.objectiveFor(world.stage)
        val status = if (world.completed) "Complete" else "$percent% — $objective"
        card.addView(
            UiKit.label(this, "Chapter ${world.chapter}  ·  $status", 12f),
            UiKit.lp(this, marginDp = 0f)
        )
        card.addView(
            UiKit.label(
                this,
                "Played ${formatDuration(world.playSeconds)}  ·  ${
                    DateUtils.getRelativeTimeSpanString(world.lastPlayedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                }",
                11f, UiKit.dim(Palette.TEXT_DIM, 0.8f)
            )
        )

        val actions = UiKit.row(this)
        actions.addView(UiKit.smallButton(this, "Play", Palette.ACCENT) { launch(world) }, UiKit.lp(this, marginDp = 4f))
        actions.addView(UiKit.smallButton(this, "Rename", Palette.TEXT_DIM) { promptRename(world) }, UiKit.lp(this, marginDp = 4f))
        actions.addView(UiKit.smallButton(this, "Copy", Palette.TEXT_DIM) { duplicate(world) }, UiKit.lp(this, marginDp = 4f))
        actions.addView(UiKit.smallButton(this, "Delete", Palette.BAD) { promptDelete(world) }, UiKit.lp(this, marginDp = 4f))
        card.addView(actions, UiKit.lp(this, ViewGroup.LayoutParams.MATCH_PARENT))
        return card
    }

    private fun formatDuration(seconds: Long): String {
        val m = seconds / 60
        return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
    }

    private fun nameField(initial: String): EditText = EditText(this).apply {
        setText(initial)
        setSelection(text.length)
        hint = "World name"
        setTextColor(Palette.TEXT)
        setHintTextColor(Palette.TEXT_DIM)
        filters = arrayOf(InputFilter.LengthFilter(WorldStore.MAX_NAME_LENGTH))
        setSingleLine()
        setPadding(UiKit.dp(this@MenuActivity, 20f), UiKit.dp(this@MenuActivity, 12f),
            UiKit.dp(this@MenuActivity, 20f), UiKit.dp(this@MenuActivity, 12f))
    }

    private fun promptCreate() {
        val input = nameField(suggestName())
        val chapters = ArrayList<String>()
        chapters.add("Chapter 1 — Subfloor 0")
        val ch2 = store.settings.chapter2Unlocked
        if (ch2) chapters.add("Chapter 2 — Subfloor 1")
        var chosenChapter = 1

        val wrapper = UiKit.column(this, Gravity.START)
        wrapper.setPadding(UiKit.dp(this, 20f), UiKit.dp(this, 8f), UiKit.dp(this, 20f), 0)
        wrapper.addView(input, UiKit.lp(this, ViewGroup.LayoutParams.MATCH_PARENT))
        val chapterLabel = UiKit.label(this, chapters[0], 13f, Palette.ACCENT)
        wrapper.addView(chapterLabel, UiKit.lp(this, marginDp = 8f))
        if (ch2) {
            wrapper.addView(UiKit.smallButton(this, "Switch chapter", Palette.TEXT_DIM) {
                chosenChapter = if (chosenChapter == 1) 2 else 1
                chapterLabel.text = chapters[chosenChapter - 1]
            })
        } else {
            wrapper.addView(UiKit.label(this, "Finish Chapter 1 to unlock Chapter 2.", 11f))
        }

        AlertDialog.Builder(this)
            .setTitle("New world")
            .setView(wrapper)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                when {
                    name.isBlank() -> toast("Give the world a name.")
                    store.nameTaken(name) -> toast("That name is already used.")
                    else -> {
                        val created = store.create(name, chosenChapter)
                        if (created == null) toast("Could not create the world.")
                        else { show(Screen.WORLDS); launch(created) }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun suggestName(): String {
        val existing = store.list().map { it.name.lowercase() }.toSet()
        var i = 1
        while (i < 500) {
            val candidate = "Descent $i"
            if (candidate.lowercase() !in existing) return candidate
            i++
        }
        return "Descent"
    }

    private fun promptRename(world: WorldSave) {
        val input = nameField(world.name)
        val wrapper = FrameLayout(this).apply {
            setPadding(UiKit.dp(this@MenuActivity, 20f), UiKit.dp(this@MenuActivity, 8f), UiKit.dp(this@MenuActivity, 20f), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename world")
            .setView(wrapper)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString()
                when {
                    name.isBlank() -> toast("Give the world a name.")
                    store.nameTaken(name, world.id) -> toast("That name is already used.")
                    !store.rename(world.id, name) -> toast("Could not rename the world.")
                    else -> show(Screen.WORLDS)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun duplicate(world: WorldSave) {
        if (store.isFull()) { toast("All ${WorldStore.MAX_SLOTS} slots are used."); return }
        if (store.duplicate(world.id) == null) toast("Could not copy the world.") else show(Screen.WORLDS)
    }

    private fun promptDelete(world: WorldSave) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"${world.name}\"?")
            .setMessage("This erases its progress. It cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                store.delete(world.id)
                show(Screen.WORLDS)
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    private fun launch(world: WorldSave) {
        startActivity(Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_WORLD_ID, world.id))
    }

    // ---- settings --------------------------------------------------------

    private fun buildSettings(): View {
        val root = UiKit.column(this, Gravity.START, transparent = true)
        root.setBackgroundColor(Palette.withAlpha(Palette.VOID, 0.82f))
        root.setPadding(UiKit.dp(this, 24f), UiKit.dp(this, 18f), UiKit.dp(this, 24f), UiKit.dp(this, 18f))

        val header = UiKit.row(this)
        header.addView(UiKit.title(this, "SETTINGS", 26f))
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(UiKit.smallButton(this, "Back", Palette.TEXT_DIM) { show(Screen.MAIN) })
        root.addView(header, UiKit.lp(this, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(UiKit.spacer(this, 10f))

        val scroll = ScrollView(this)
        val list = UiKit.column(this, Gravity.START)

        list.addView(section("AUDIO"))
        list.addView(toggle("Sound effects", store.settings.sfxEnabled) { store.settings.sfxEnabled = it })
        list.addView(toggle("Ambience", store.settings.ambienceEnabled) { store.settings.ambienceEnabled = it })

        list.addView(section("FEEL"))
        list.addView(toggle("Haptics", store.settings.hapticsEnabled) { store.settings.hapticsEnabled = it })
        list.addView(
            stepper(
                "Screen shake",
                { percent(store.settings.shakeAmount) },
                { store.settings.shakeAmount -= 0.25f },
                { store.settings.shakeAmount += 0.25f }
            )
        )
        list.addView(
            stepper(
                "Control size",
                { percent(store.settings.controlScale) },
                { store.settings.controlScale -= 0.1f },
                { store.settings.controlScale += 0.1f }
            )
        )
        list.addView(toggle("Left-handed layout", store.settings.leftHanded) { store.settings.leftHanded = it })

        list.addView(section("VIDEO"))
        list.addView(
            stepper(
                "Brightness",
                { percent(store.settings.brightness * 2f) },
                { store.settings.brightness -= 0.1f },
                { store.settings.brightness += 0.1f }
            )
        )
        list.addView(toggle("Effects & particles", store.settings.effectsEnabled) { store.settings.effectsEnabled = it })
        list.addView(toggle("Show FPS", store.settings.showFps) { store.settings.showFps = it })

        list.addView(section("HELP"))
        list.addView(toggle("Contextual hints", store.settings.hintsEnabled) { store.settings.hintsEnabled = it })
        list.addView(
            UiKit.label(
                this,
                "Left half of the screen is a floating stick — touch anywhere and drag.\n" +
                    "JUMP, SNEAK and DODGE sit on the right; USE lights up when something is in reach.\n" +
                    "Walk into a crate and tap JUMP to pull yourself up. If a gap is too low to walk\n" +
                    "through, the game will tell you to hold SNEAK.",
                12f
            ),
            UiKit.lp(this, marginDp = 4f)
        )

        list.addView(section("UPDATES"))
        list.addView(
            UiKit.label(
                this,
                "Installed: ${UpdateService.currentVersionName(this)} " +
                    "(build ${UpdateService.currentVersionCode(this)})",
                13f, Palette.TEXT
            )
        )
        list.addView(
            UiKit.button(this, "Check for updates", Palette.ACCENT) {
                UpdateUi.check(this, store, quiet = false)
            },
            UiKit.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 6f)
        )
        list.addView(toggle("Check on launch", store.settings.autoCheckUpdates) { store.settings.autoCheckUpdates = it })
        list.addView(
            UiKit.label(
                this,
                "Updates install over this version and keep every world.",
                11f
            ),
            UiKit.lp(this, marginDp = 2f)
        )

        list.addView(UiKit.spacer(this, 12f))
        val unlock = if (store.settings.chapter2Unlocked) "Chapter 2 world creation: unlocked"
        else "Chapter 2 world creation: locked"
        list.addView(
            UiKit.label(this, unlock, 12f, if (store.settings.chapter2Unlocked) Palette.GOOD else Palette.TEXT_DIM)
        )
        list.addView(UiKit.spacer(this, 20f))

        scroll.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun percent(value: Float): String = "${(value * 100).toInt()}%"

    private fun section(title: String): View {
        val label = UiKit.label(this, title, 12f, Palette.ACCENT)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = UiKit.dp(this, 16f)
        lp.bottomMargin = UiKit.dp(this, 2f)
        label.layoutParams = lp
        return label
    }

    /** A labelled value with minus and plus buttons. */
    private fun stepper(label: String, read: () -> String, minus: () -> Unit, plus: () -> Unit): View {
        val row = UiKit.row(this)
        val text = UiKit.label(this, "$label  ${read()}", 14f, Palette.TEXT)
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(UiKit.smallButton(this, "−", Palette.TEXT_DIM) {
            minus()
            text.text = "$label  ${read()}"
        }, UiKit.lp(this, marginDp = 4f))
        row.addView(UiKit.smallButton(this, "+", Palette.TEXT_DIM) {
            plus()
            text.text = "$label  ${read()}"
        }, UiKit.lp(this, marginDp = 4f))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = UiKit.dp(this, 6f)
        row.layoutParams = lp
        return row
    }

    private fun toggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val row = UiKit.row(this)
        val text: TextView = UiKit.label(this, label, 14f, Palette.TEXT)
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        var value = initial
        lateinit var button: android.widget.Button
        button = UiKit.smallButton(this, if (value) "ON" else "OFF", if (value) Palette.GOOD else Palette.TEXT_DIM) {
            value = !value
            onChange(value)
            button.text = if (value) "ON" else "OFF"
            button.background = UiKit.rounded(
                Palette.withAlpha(if (value) Palette.GOOD else Palette.TEXT_DIM, 0.14f),
                Palette.withAlpha(if (value) Palette.GOOD else Palette.TEXT_DIM, 0.7f),
                14f, this
            )
        }
        row.addView(button)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = UiKit.dp(this, 6f)
        row.layoutParams = lp
        return row
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
