package app.inkloom

import android.content.Context

/** Start each UI scenario with its own notebook checkpoints, recovery journal and folder index. */
internal fun resetNotebookTestLibrary(context: Context) {
    check(context.filesDir.resolve("documents").deleteRecursively())
    context.filesDir.resolve("journal.jsonl").let { check(!it.exists() || it.delete()) }
    context.filesDir.resolve("library.json").let { check(!it.exists() || it.delete()) }
}
