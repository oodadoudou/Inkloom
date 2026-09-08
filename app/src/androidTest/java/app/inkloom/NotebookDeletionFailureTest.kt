package app.inkloom

import android.content.Context
import android.system.Os
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.FileDocumentStore
import app.inkloom.document.InkDocument
import app.inkloom.settings.DrawingColorStore
import app.inkloom.settings.DrawingColors
import app.inkloom.ui.InkloomViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File

@RunWith(AndroidJUnit4::class)
class NotebookDeletionFailureTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val note = InkDocument(title = "Deletion retry fixture", trashedAt = 1L)
    private val colors = DrawingColors(0x8044aaff.toInt(), 0xffeeddaa.toInt())
    private val preload = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit()
                    .putString("language", "ENGLISH").commit()
                val directory = File(context.filesDir, "documents").apply { mkdirs() }
                val mode = Os.stat(directory.path).st_mode and 511
                val store = FileDocumentStore(context.filesDir)
                store.save(note)
                DrawingColorStore(context).save(note.id, colors)
                try { base.evaluate() } finally {
                    Os.chmod(directory.path, mode)
                    store.deletePermanently(note.id)
                    DrawingColorStore(context).remove(note.id)
                }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(preload).around(compose)

    @Test fun deniedDeleteKeepsTheVisibleNoteColorsAndSelectionUntilSuccessfulRetry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "documents")
        val checkpoint = File(directory, "${note.id}.json")
        val before = checkpoint.readBytes()
        lateinit var model: InkloomViewModel
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[InkloomViewModel::class.java]
            model.select(note.id)
        }
        val selection = model.selectionRuntime.stateFor(note.id, note.pages.single().id, 32, 32)
        compose.onAllNodesWithText("Trash").onFirst().performClick()
        compose.onNodeWithText(note.title).assertIsDisplayed()
        Os.chmod(directory.path, 320) // 0500: readable/searchable, but deletion is denied.
        assertFalse("The fault must actually deny writes", directory.canWrite())
        deleteFromVisibleCard()
        compose.onNodeWithText("Couldn’t delete notebook").assertIsDisplayed()
        assertTrue(model.state.value.documents.any { it.id == note.id })
        assertEquals(note.id, model.state.value.selectedDocumentId)
        assertSame(selection, model.selectionRuntime.stateFor(note.id, note.pages.single().id, 32, 32))
        assertEquals(colors, DrawingColorStore(context).load(note.id))
        assertArrayEquals(before, checkpoint.readBytes())
        assertEquals(note, FileDocumentStore(context.filesDir).get(note.id))
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText(note.title).assertIsDisplayed()

        Os.chmod(directory.path, 448) // 0700 restores deletion for this app-owned fixture.
        deleteFromVisibleCard()
        compose.waitUntil(5_000) { model.state.value.documents.none { it.id == note.id } }
        assertFalse(checkpoint.exists())
        assertNull(FileDocumentStore(context.filesDir).get(note.id))
        assertNull(model.state.value.selectedDocumentId)
        assertEquals(DrawingColors(), DrawingColorStore(context).load(note.id))
        assertEquals(0, model.selectionRuntime.pageStateCount())
    }

    private fun deleteFromVisibleCard() {
        compose.onNodeWithContentDescription("Document actions").performClick()
        compose.onNodeWithText("Delete permanently").performClick()
        compose.waitForIdle()
    }
}
