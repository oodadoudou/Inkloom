package app.inkloom

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.InkDocument
import app.inkloom.document.InkPage
import app.inkloom.document.NotebookResumeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookResumeStoreTest {
    @Test fun deletingPagesRetainsTheVisibleNeighborAndPersistsItAcrossReopening() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val store = app.inkloom.document.FileDocumentStore(application.filesDir)
        val suffix = System.nanoTime().toString()
        val pages = (1..5).map { InkPage(id = "delete-$suffix-$it") }
        val document = InkDocument(id = "delete-note-$suffix", title = "Removal navigation", pages = pages)
        val other = InkDocument(id = "delete-other-$suffix", title = "Other", pages = pages.map { it.copy(id = "other-${it.id}") })
        store.save(document); store.save(other)
        val holder = androidx.lifecycle.ViewModelStore()
        val owner = object : androidx.lifecycle.ViewModelStoreOwner { override val viewModelStore = holder }
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync {
                val model = androidx.lifecycle.ViewModelProvider(owner,
                    androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application))[app.inkloom.ui.InkloomViewModel::class.java]
                model.select(document.id); model.selectPage(pages[2].id)
                model.deletePage(document.id, pages[0].id)
                assertEquals(pages[2].id, model.currentPage()?.id)
                model.deletePage(other.id, other.pages[1].id)
                assertEquals(pages[2].id, model.currentPage()?.id)
                model.deletePage(document.id, pages[2].id)
                assertEquals(pages[3].id, model.currentPage()?.id)
                model.select(other.id); model.select(document.id)
                assertEquals(pages[3].id, model.currentPage()?.id)
                model.deletePage(document.id, pages[3].id)
                assertEquals(pages[4].id, model.currentPage()?.id)
                model.deletePage(document.id, pages[4].id)
                assertEquals(pages[1].id, model.currentPage()?.id)
                model.deletePage(document.id, pages[1].id)
                assertEquals("The last page is retained", pages[1].id, model.currentPage()?.id)
                assertEquals(pages[1].id, NotebookResumeStore(application).restore(requireNotNull(store.get(document.id))))
            }
        } finally {
            instrumentation.runOnMainSync { holder.clear() }
            store.deletePermanently(document.id); store.deletePermanently(other.id)
        }
    }

    @Test fun restoresEachNotebooksLastExistingPageAndRejectsDeletedPages() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("inkloom_notebook_resume",android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val store=NotebookResumeStore(context)
        val first=InkPage(id="resume-a")
        val second=InkPage(id="resume-b")
        val document=InkDocument(id="resume-document",title="Resume",pages=listOf(first,second))
        store.remember(document.id,second.id)
        assertEquals(second.id,store.restore(document))
        assertNull(store.restore(document.copy(pages=listOf(first))))
        store.forget(document.id)
        assertNull(store.restore(document))
    }
}
