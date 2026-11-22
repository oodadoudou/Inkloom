package app.inkloom.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A published GitHub release that carries an installable APK. */
data class AppRelease(
    val version: AppVersion,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkSize: Long,
    /** Lower-case hex SHA-256 of the APK when GitHub reports one. */
    val apkSha256: String?,
)

/** Numeric dotted version ("v0.253.0", "0.253.0-beta" → 0.253.0); missing parts count as 0. */
data class AppVersion(val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (index in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(index) { 0 }.compareTo(other.parts.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    override fun toString() = parts.joinToString(".")

    companion object {
        private val NUMBERS = Regex("""^[vV]?(\d{1,6}(?:\.\d{1,6}){0,3})""")

        fun parse(text: String): AppVersion? = NUMBERS.find(text.trim())?.groupValues?.get(1)
            ?.split('.')?.map(String::toInt)?.let(::AppVersion)
    }
}

/** Reads the GitHub "latest release" JSON; anything without a usable APK is not an update. */
object GitHubReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val SHA256 = Regex("^sha256:([0-9a-f]{64})$")

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tag: String,
        @SerialName("html_url") val pageUrl: String = "",
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String,
        val size: Long = 0,
        @SerialName("browser_download_url") val url: String,
        val digest: String? = null,
    )

    fun parse(text: String): AppRelease? {
        val release = runCatching { json.decodeFromString(Release.serializer(), text) }.getOrNull() ?: return null
        if (release.draft || release.prerelease) return null
        val version = AppVersion.parse(release.tag) ?: return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) && it.url.startsWith("https://") }
            ?: return null
        return AppRelease(version, release.body.orEmpty().trim(), release.pageUrl, apk.url, apk.size,
            apk.digest?.lowercase()?.let { SHA256.find(it)?.groupValues?.get(1) })
    }
}
