package app.inkloom.ui

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.inkloom.document.ShareFileName
import java.io.File

/** What a notebook's ⋮ menu or the editor's Share section can produce. */
internal enum class NotebookOutput { SHARE_PDF, EXPORT_PDF, EXPORT_PACKAGE }

/**
 * Hands finished files to Android's share sheet. Files are written to a private cache folder that
 * FileProvider exposes read-only to the chosen app; shares older than a day are pruned.
 */
internal object ShareSheet {
    private const val DIRECTORY = "shared"
    private const val KEEP_MILLIS = 24L * 60 * 60 * 1000

    /** A fresh, readable destination such as `shared/1739…/Notebook title.pdf`. */
    fun newFile(context: Context, title: String, extension: String): File {
        val root = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val now = System.currentTimeMillis()
        root.listFiles()?.filter { now - it.lastModified() > KEEP_MILLIS }?.forEach { it.deleteRecursively() }
        // One folder per share keeps the readable name while never overwriting a file still being sent.
        val folder = File(root, "$now-${System.nanoTime()}").apply { mkdirs() }
        return File(folder, ShareFileName.of(title, extension))
    }

    fun open(context: Context, file: File, mime: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, file.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        // Inkloom itself accepts shared PDFs and images; offering it would just re-import the file.
        val chooser = Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, app.inkloom.MainActivity::class.java)))
        context.startActivity(chooser)
    }
}
