package app.inkloom.document

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OnDeviceTextRecognitionService(private val context: Context) {
    suspend fun recognizeHandwriting(@Suppress("UNUSED_PARAMETER") page: InkPage, @Suppress("UNUSED_PARAMETER") languageTag: String): List<RecognizedTextBlock> {
        error("Paint Engine V5 stores handwriting as raster pixels; vector-ink recognition is unavailable.")
    }

    suspend fun recognizeImage(file: File, sourceId: String, languageTag: String): List<RecognizedTextBlock> {
        require(file.isFile) { "Image source is unavailable" }
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        val width = boundsOptions.outWidth.coerceAtLeast(1)
        val height = boundsOptions.outHeight.coerceAtLeast(1)
        val recognizer = if (languageTag.startsWith("zh", ignoreCase = true)) {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        } else {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        return try {
            val result = recognizer.process(InputImage.fromFilePath(context, Uri.fromFile(file))).await()
            result.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
                val text = line.text.trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
                val rect = line.boundingBox ?: return@mapNotNull null
                RecognizedTextBlock(
                    text = text,
                    bounds = PdfTextBounds(
                        left = rect.left.toFloat().div(width).coerceIn(0f, 1f),
                        top = rect.top.toFloat().div(height).coerceIn(0f, 1f),
                        right = rect.right.toFloat().div(width).coerceIn(0f, 1f),
                        bottom = rect.bottom.toFloat().div(height).coerceIn(0f, 1f),
                    ),
                    source = RecognizedTextSource.IMAGE_OCR,
                    sourceIds = listOf(sourceId),
                    languageTag = languageTag,
                )
            }
        } finally {
            recognizer.close()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
        addOnFailureListener { failure -> if (continuation.isActive) continuation.resumeWithException(failure) }
        addOnCanceledListener { continuation.cancel() }
    }
}
