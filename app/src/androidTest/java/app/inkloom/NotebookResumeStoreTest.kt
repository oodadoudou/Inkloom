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
