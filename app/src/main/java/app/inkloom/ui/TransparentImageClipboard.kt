package app.inkloom.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import app.inkloom.engine.v5.surface.RasterSelectionClipboard
import app.inkloom.engine.v5.surface.RasterSelectionContent
import app.inkloom.engine.v5.surface.PixelTileFormat
import java.io.File
import java.io.FileOutputStream

/** Bridges Inkloom's bounded raster clipboard with Android's transparent image clipboard. */
internal object TransparentImageClipboard {
    private const val MIME = "image/png"
    private const val DIRECTORY = "selection-clipboard"
    private const val LABEL = "Inkloom transparent selection"

    fun publish(context: Context, clipboard: RasterSelectionClipboard): Boolean = runCatching {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        directory.listFiles()?.forEach(File::delete)
        val file = File(directory, "inkloom-selection-${System.currentTimeMillis()}.png")
        val bitmap = RasterSelectionContent.toBitmap(clipboard)
        try {
            FileOutputStream(file).use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
        } finally {
            bitmap.recycle()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val data = ClipData.newUri(context.contentResolver, LABEL, uri)
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(data)
        true
    }.getOrDefault(false)

    fun read(
        context: Context,
        destinationWidth: Int,
        destinationHeight: Int,
        format: PixelTileFormat,
        sourceLeft: Int = 0,
        sourceTop: Int = 0,
    ): RasterSelectionClipboard? = runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = manager.primaryClip ?: return null
        val description = manager.primaryClipDescription ?: return null
        if (!description.hasMimeType("image/*") && !description.hasMimeType(MIME)) return null
        val uri = clip.getItemAt(0).uri ?: return null
        context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)?.let { bitmap ->
            try {
                RasterSelectionContent.fromBitmap(bitmap, destinationWidth, destinationHeight, format, sourceLeft, sourceTop)
            } finally {
                bitmap.recycle()
            }
        }
    }.getOrNull()

    fun ownsPrimaryClip(context: Context): Boolean {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return manager.primaryClipDescription?.label?.toString() == LABEL
    }
}
