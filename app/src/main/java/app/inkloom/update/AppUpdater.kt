package app.inkloom.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import app.inkloom.BuildConfig
import app.inkloom.document.readBoundedBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Checks GitHub for a newer release, downloads its APK and hands it to the system installer. */
class AppUpdater(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("inkloom_updates", Context.MODE_PRIVATE)
    private val directory = File(this.context.cacheDir, "updates")

    /** The latest release when it is newer than this build and the user has not skipped it. */
    suspend fun findUpdate(): AppRelease? = withContext(Dispatchers.IO) {
        val current = AppVersion.parse(BuildConfig.VERSION_NAME) ?: return@withContext null
        val skipped = preferences.getString(SKIPPED_KEY, null)?.let(AppVersion::parse)
        val release = runCatching { GitHubReleaseParser.parse(get(LATEST_RELEASE_URL)) }
            .onFailure { Log.i(TAG, "Update check failed", it) }.getOrNull()
        release?.takeIf { it.version > current && (skipped == null || it.version > skipped) }
    }

    /** Hides this version until a newer one is published. */
    fun skip(release: AppRelease) {
        preferences.edit().putString(SKIPPED_KEY, release.version.toString()).apply()
    }

    /** Downloads and verifies the APK, reporting progress from 0 to 1. */
    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        require(release.apkSize in 1..MAX_APK_BYTES) { "Unexpected update size" }
        directory.deleteRecursively()
        check(directory.mkdirs() || directory.isDirectory) { "Could not prepare the download" }
        val partial = File(directory, "Inkloom-${release.version}.apk.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = open(release.apkUrl)
        try {
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > release.apkSize) throw IOException("Update is larger than announced")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(total.toFloat() / release.apkSize)
                    }
                    if (total != release.apkSize) throw IOException("Update download was incomplete")
                }
            }
        } finally {
            connection.disconnect()
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (release.apkSha256 != null && hash != release.apkSha256) {
            partial.delete()
            throw IOException("Update failed its integrity check")
        }
        val apk = File(directory, "Inkloom-${release.version}.apk")
        check(partial.renameTo(apk)) { "Could not save the update" }
        verifyPackage(apk, release)
        apk
    }

    /** Opens the system installer; Android asks once to allow installs from Inkloom. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun verifyPackage(apk: File, release: AppRelease) {
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0)
        if (info?.packageName != context.packageName || AppVersion.parse(info.versionName.orEmpty()) != release.version) {
            apk.delete()
            throw IOException("The downloaded file is not this Inkloom release")
        }
    }

    private fun get(url: String): String {
        val connection = open(url)
        try {
            return connection.inputStream.use { it.readBoundedBytes(MAX_RELEASE_JSON_BYTES, "Release information is too large") }
                .toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Inkloom-Android")
        val status = connection.responseCode
        if (status !in 200..299) {
            connection.disconnect()
            throw IOException("GitHub answered $status")
        }
        return connection
    }

    companion object {
        /** Instrumented tests turn this off so no dialog covers the screens they drive. */
        @Volatile var checksEnabled = true
        private const val TAG = "InkloomUpdate"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/oodadoudou/Inkloom/releases/latest"
        private const val SKIPPED_KEY = "skippedVersion"
        private const val TIMEOUT_MILLIS = 15_000
        private const val MAX_RELEASE_JSON_BYTES = 1024 * 1024
        private const val MAX_APK_BYTES = 512L * 1024 * 1024
    }
}
