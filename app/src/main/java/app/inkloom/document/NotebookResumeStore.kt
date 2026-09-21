package app.inkloom.document

import android.content.Context

/** Per-notebook reading position. Page ids survive reorder and degrade safely after deletion. */
class NotebookResumeStore(context: Context) {
    private val preferences=context.getSharedPreferences("inkloom_notebook_resume",Context.MODE_PRIVATE)

    fun restore(document: InkDocument): String? = preferences.getString(document.id,null)
        ?.takeIf { saved -> document.pages.any { it.id==saved } }

    fun remember(documentId: String, pageId: String) {
        preferences.edit().putString(documentId,pageId).apply()
    }

    fun forget(documentId: String) { preferences.edit().remove(documentId).apply() }
}
