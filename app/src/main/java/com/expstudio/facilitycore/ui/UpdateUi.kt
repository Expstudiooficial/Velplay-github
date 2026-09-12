package com.expstudio.facilitycore.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.expstudio.facilitycore.core.Palette
import com.expstudio.facilitycore.save.WorldStore
import com.expstudio.facilitycore.update.ReleaseInfo
import com.expstudio.facilitycore.update.UpdateResult
import com.expstudio.facilitycore.update.UpdateService

/**
 * The user-facing half of the updater: a check, a summary of what is new, a
 * download with progress, and a hand-off to the system installer.
 */
object UpdateUi {

    private val main = Handler(Looper.getMainLooper())

    /**
     * [quiet] is used for the launch check: it says nothing unless there is
     * actually something new, and only nags once per version.
     */
    fun check(activity: Activity, store: WorldStore, quiet: Boolean) {
        if (!quiet) Toast.makeText(activity, "Checking for updates...", Toast.LENGTH_SHORT).show()
        Thread {
            val result = UpdateService.check(activity)
            main.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                when (result) {
                    is UpdateResult.Available -> {
                        if (quiet && store.settings.lastSeenUpdate >= result.release.versionCode) return@post
                        store.settings.lastSeenUpdate = result.release.versionCode
                        offer(activity, result.release)
                    }
                    is UpdateResult.UpToDate ->
                        if (!quiet) {
                            Toast.makeText(
                                activity,
                                "You are on the latest version (${UpdateService.currentVersionName(activity)}).",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    is UpdateResult.Failed ->
                        if (!quiet) {
                            Toast.makeText(activity, "Update check failed: ${result.reason}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        }.start()
    }

    private fun offer(activity: Activity, release: ReleaseInfo) {
        val body = StringBuilder()
        if (release.description.isNotEmpty()) body.append(release.description).append("\n\n")
        if (release.changelog.isNotEmpty()) {
            for (line in release.changelog.take(8)) body.append("  •  ").append(line).append('\n')
            body.append('\n')
        }
        body.append("Version ${release.versionName}  ·  ${release.sizeLabel()}")
        if (release.releasedAt.isNotEmpty()) body.append("  ·  ${release.releasedAt}")
        body.append("\n\nYour worlds are kept — this installs over the current version.")

        AlertDialog.Builder(activity)
            .setTitle(release.title)
            .setMessage(body.toString())
            .setPositiveButton("Download & install") { _, _ -> startDownload(activity, release) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startDownload(activity: Activity, release: ReleaseInfo) {
        // A small dialog of our own rather than ProgressDialog, which is gone.
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        val caption = TextView(activity).apply {
            text = release.sizeLabel()
            setTextColor(Palette.TEXT_DIM)
            gravity = Gravity.CENTER
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (resources.displayMetrics.density * 22f).toInt()
            setPadding(pad, pad, pad, pad / 2)
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(caption, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Downloading ${release.versionName}")
            .setView(body)
            .setCancelable(false)
            .show()

        Thread {
            val file = UpdateService.download(activity, release) { fraction ->
                if (fraction >= 0f) {
                    main.post {
                        bar.isIndeterminate = false
                        bar.progress = (fraction * 100).toInt()
                        caption.text = "${(fraction * 100).toInt()}%  of  ${release.sizeLabel()}"
                    }
                }
            }
            main.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                try { dialog.dismiss() } catch (t: Throwable) { /* window already gone */ }
                if (file == null) {
                    Toast.makeText(
                        activity,
                        "The download failed or did not match its checksum. Try again later.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@post
                }
                if (!UpdateService.canInstall(activity)) {
                    AlertDialog.Builder(activity)
                        .setTitle("One permission needed")
                        .setMessage(
                            "Android needs permission to install apps from Facility Core. " +
                                "Turn it on, then press Install again."
                        )
                        .setPositiveButton("Open settings") { _, _ ->
                            UpdateService.unknownSourcesIntent(activity)?.let {
                                try { activity.startActivity(it) } catch (t: Throwable) { /* no such screen */ }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return@post
                }
                if (!UpdateService.install(activity, file)) {
                    Toast.makeText(activity, "Could not open the installer.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
