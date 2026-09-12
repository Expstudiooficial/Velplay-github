package com.expstudio.facilitycore.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** What the manifest at [UpdateService.MANIFEST_URL] describes. */
data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val description: String,
    val sizeMb: Double,
    val releasedAt: String,
    val apkUrl: String,
    val sha256: String,
    val changelog: List<String>
) {
    fun sizeLabel(): String = if (sizeMb >= 1000) String.format("%.2f GB", sizeMb / 1024.0)
    else String.format("%.1f MB", sizeMb)
}

sealed class UpdateResult {
    data class UpToDate(val current: Int) : UpdateResult()
    data class Available(val release: ReleaseInfo) : UpdateResult()
    data class Failed(val reason: String) : UpdateResult()
}

/**
 * Checks a small JSON manifest in the repo for a newer build, downloads the APK
 * and hands it to the system installer.
 *
 * Every build is signed with the same key that lives in the repo, so an update
 * installs straight over the existing game and keeps its worlds.
 */
object UpdateService {

    /**
     * Where the update manifest lives. Kept in one place so it is easy to move.
     *
     * This has to be readable without credentials — the app ships to phones and
     * must not carry a token. While the repository is private, raw.github
     * returns 404 to everyone and the check will report that it could not
     * reach the server; making the repository public, or pointing this at any
     * public static host, is all it needs.
     */
    const val MANIFEST_URL =
        "https://raw.githubusercontent.com/Expstudiooficial/Velplay-github/main/latest.json"

    private const val TIMEOUT_MS = 15_000

    fun currentVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else info.versionCode
    } catch (t: Throwable) {
        0
    }

    fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (t: Throwable) {
        "?"
    }

    /** Blocking; call from a worker thread. */
    fun check(context: Context): UpdateResult {
        return try {
            val text = fetch(MANIFEST_URL)
            val json = JSONObject(text)
            val release = ReleaseInfo(
                versionCode = json.optInt("versionCode", 0),
                versionName = json.optString("versionName", "?"),
                title = json.optString("title", "Update"),
                description = json.optString("description", ""),
                sizeMb = json.optDouble("sizeMb", 0.0),
                releasedAt = json.optString("releasedAt", ""),
                apkUrl = json.optString("apkUrl", ""),
                sha256 = json.optString("sha256", ""),
                changelog = json.optJSONArray("changelog").toStringList()
            )
            val current = currentVersionCode(context)
            when {
                release.apkUrl.isEmpty() -> UpdateResult.Failed("The update manifest has no download link.")
                release.versionCode > current -> UpdateResult.Available(release)
                else -> UpdateResult.UpToDate(current)
            }
        } catch (t: Throwable) {
            UpdateResult.Failed(t.message ?: "Could not reach the update server.")
        }
    }

    /** Blocking download; [onProgress] receives 0..1, or -1 when the size is unknown. */
    fun download(context: Context, release: ReleaseInfo, onProgress: (Float) -> Unit): File? {
        return try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Keep one file around at a time; these are megabytes.
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "facility-core-${release.versionCode}.apk")

            val connection = open(release.apkUrl)
            val total = connection.contentLength.toLong()
            connection.inputStream.use { input ->
                FileOutputStream(out).use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        sink.write(buffer, 0, read)
                        written += read
                        onProgress(if (total > 0) (written.toFloat() / total) else -1f)
                    }
                }
            }
            connection.disconnect()
            if (out.length() <= 0L) return null
            // Refuse anything that is not byte-for-byte what the manifest
            // described. This file is about to be handed to the installer.
            if (release.sha256.isNotEmpty() && !digestMatches(out, release.sha256)) {
                out.delete()
                return null
            }
            out
        } catch (t: Throwable) {
            null
        }
    }

    private fun digestMatches(file: File, expected: String): Boolean = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        hex.equals(expected.trim(), ignoreCase = true)
    } catch (t: Throwable) {
        false
    }

    /**
     * True when the system will let us launch an install. On Oreo and above the
     * user has to grant "install unknown apps" to this app specifically.
     */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun unknownSourcesIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        } else {
            null
        }

    fun install(context: Context, apk: File): Boolean = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (t: Throwable) {
        false
    }

    // ---- plumbing --------------------------------------------------------

    private fun open(url: String): HttpURLConnection {
        var current = url
        var redirects = 0
        while (true) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "FacilityCore-Updater")
            }
            val code = connection.responseCode
            // Redirects are not followed across protocols automatically, and
            // release downloads almost always redirect.
            if (code in 300..399 && redirects < 5) {
                val next = connection.getHeaderField("Location")
                connection.disconnect()
                if (next.isNullOrEmpty()) throw IllegalStateException("Redirect without a target.")
                current = next
                redirects++
                continue
            }
            if (code !in 200..299) {
                connection.disconnect()
                throw IllegalStateException("Server returned $code.")
            }
            return connection
        }
    }

    private fun fetch(url: String): String {
        val connection = open(url)
        try {
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) out.add(optString(i, ""))
        return out.filter { it.isNotEmpty() }
    }
}
