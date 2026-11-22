package app.inkloom.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseTest {
    private fun release(tag: String, assets: String, extra: String = "") =
        """{"tag_name":"$tag","html_url":"https://github.com/oodadoudou/Inkloom/releases/tag/$tag","body":"## New\n- Notes",$extra"assets":[$assets]}"""

    private val apk = """{"name":"Inkloom-0.253.0.apk","size":1234,"browser_download_url":"https://github.com/oodadoudou/Inkloom/releases/download/v0.253.0/Inkloom-0.253.0.apk","digest":"sha256:${"ab".repeat(32)}"}"""

    @Test fun `versions compare numerically and ignore a leading v or suffix`() {
        assertTrue(AppVersion.parse("v0.253.0")!! > AppVersion.parse("0.252.9")!!)
        assertTrue(AppVersion.parse("0.1000.0")!! > AppVersion.parse("0.999.0")!!)
        assertEquals(AppVersion.parse("1.0"), AppVersion.parse("1.0.0-debug")!!.let { AppVersion(it.parts.take(2)) })
        assertEquals(0, AppVersion.parse("1.0")!!.compareTo(AppVersion.parse("1.0.0")!!))
        assertNull(AppVersion.parse("latest"))
    }

    @Test fun `latest release with an apk asset is read with its size and checksum`() {
        val parsed = GitHubReleaseParser.parse(release("v0.253.0", apk))!!
        assertEquals("0.253.0", parsed.version.toString())
        assertEquals(1234L, parsed.apkSize)
        assertEquals("ab".repeat(32), parsed.apkSha256)
        assertTrue(parsed.apkUrl.endsWith(".apk"))
        assertEquals("## New\n- Notes", parsed.notes)
    }

    @Test fun `drafts, prereleases, releases without an apk and malformed json are not updates`() {
        assertNull(GitHubReleaseParser.parse(release("v0.253.0", apk, "\"prerelease\":true,")))
        assertNull(GitHubReleaseParser.parse(release("v0.253.0", apk, "\"draft\":true,")))
        assertNull(GitHubReleaseParser.parse(release("v0.253.0", """{"name":"notes.txt","size":1,"browser_download_url":"https://x/notes.txt"}""")))
        assertNull(GitHubReleaseParser.parse(release("nightly", apk)))
        assertNull(GitHubReleaseParser.parse("{bad"))
    }

    @Test fun `an asset served over plain http is refused`() {
        assertNull(GitHubReleaseParser.parse(release("v0.253.0", apk.replace("https://", "http://"))))
    }
}
