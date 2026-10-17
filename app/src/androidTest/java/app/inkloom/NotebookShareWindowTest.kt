package app.inkloom

import android.accessibilityservice.AccessibilityService
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.FileDocumentStore
import app.inkloom.document.InkDocument
import app.inkloom.document.InkPage
import app.inkloom.ui.InkloomViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.File

/** Notebooks can be shared to other apps or exported from the Library without opening them. */
class NotebookShareWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2).commit()
            resetNotebookTestLibrary(context)
            File(context.cacheDir, "shared").deleteRecursively()
            FileDocumentStore(context.filesDir).save(InkDocument(id = "share-doc", title = "Field notes: week 1/2", updatedAt = 9_000,
                pages = (1..2).map { InkPage(id = "share-page-$it", title = "Page $it") }))
            try { base.evaluate() } finally { File(context.cacheDir, "shared").deleteRecursively() }
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    private fun openMenu() {
        val card = hasAnyAncestor(hasContentDescription("Notebook Field notes: week 1/2. Long press to move."))
        compose.onNode(hasContentDescription("Document actions") and card).performClick()
    }

    @Test fun libraryMenuOffersShareAndExportForThatNotebook() {
        openMenu()
        compose.onNodeWithText("Share PDF…").assertIsDisplayed()
        compose.onNodeWithText("Export PDF…").assertIsDisplayed()
        compose.onNodeWithText("Export Inkloom package…").assertIsDisplayed()
    }

    @Test fun sharingWritesAReadablePdfWithASafeNameAndOpensTheShareSheet() {
        openMenu()
        compose.onNodeWithText("Share PDF…").performClick()
        val shared = File(compose.activity.cacheDir, "shared")
        compose.waitUntil(15_000) { shared.walkTopDown().any { it.isFile && it.length() > 0 } }
        val pdf = shared.walkTopDown().single { it.isFile }
        assertEquals("Field notes_ week 1_2.pdf", pdf.name)
        val bytes = pdf.readBytes()
        assertTrue("A PDF header", String(bytes, 0, 5) == "%PDF-")
        assertEquals("Both pages exported", 2, Regex("/Type\\s*/Page[^s]").findAll(String(bytes, Charsets.ISO_8859_1)).count())
        // The share target reads it through the app's FileProvider; this fails if the folder is not exposed.
        val uri = FileProvider.getUriForFile(compose.activity, "${compose.activity.packageName}.files", pdf)
        compose.activity.contentResolver.openInputStream(uri)!!.use { assertEquals(bytes.size, it.readBytes().size) }
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test fun backgroundPdfExportWritesEveryPage() {
        val model = ViewModelProvider(compose.activity)[InkloomViewModel::class.java]
        val file = File(compose.activity.cacheDir, "shared/test-export.pdf").apply { parentFile!!.mkdirs() }
        var result: Result<Unit>? = null
        compose.runOnUiThread { model.writeDocumentPdf("share-doc", file) { result = it } }
        compose.waitUntil(15_000) { result != null }
        assertTrue(result!!.isSuccess)
        assertEquals(2, Regex("/Type\\s*/Page[^s]").findAll(String(file.readBytes(), Charsets.ISO_8859_1)).count())
    }
}
