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
    suspend fun recognizeImage(file: File, sourceId: String, languageTag: String): List<RecognizedTextBlock> {
        require(file.isFile) { "Image source is unavailable" }
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        val width = boundsOptions.outWidth.coerceAtLeast(1)
        val height = boundsOptions.outHeight.coerceAtLeast(1)
        return recognize(InputImage.fromFilePath(context, Uri.fromFile(file)), width, height, sourceId, languageTag)
    }

    suspend fun recognizeBitmap(bitmap: android.graphics.Bitmap, sourceId: String, languageTag: String): List<RecognizedTextBlock> =
        recognize(InputImage.fromBitmap(bitmap, 0), bitmap.width, bitmap.height, sourceId, languageTag)

    private suspend fun recognize(image: InputImage, width: Int, height: Int, sourceId: String, languageTag: String): List<RecognizedTextBlock> {
        val recognizer = if (languageTag.startsWith("zh", ignoreCase = true))
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        else TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            recognizer.process(image).await().textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val text = line.text.trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
                val rect = line.boundingBox ?: return@mapNotNull null
                RecognizedTextBlock(text = text, bounds = PdfTextBounds(
                    rect.left.toFloat().div(width).coerceIn(0f, 1f), rect.top.toFloat().div(height).coerceIn(0f, 1f),
                    rect.right.toFloat().div(width).coerceIn(0f, 1f), rect.bottom.toFloat().div(height).coerceIn(0f, 1f)),
                    source = RecognizedTextSource.IMAGE_OCR, sourceIds = listOf(sourceId), languageTag = languageTag)
            }
        } finally { recognizer.close() }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
        addOnFailureListener { failure -> if (continuation.isActive) continuation.resumeWithException(failure) }
        addOnCanceledListener { continuation.cancel() }
    }
}
