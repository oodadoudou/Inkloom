package app.inkloom.document

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OnDeviceTextRecognitionService(private val context: Context) {
    suspend fun recognizeHandwriting(page: InkPage, languageTag: String): List<RecognizedTextBlock> {
        val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            ?: error("No handwriting model is available for $languageTag")
        val model = DigitalInkRecognitionModel.builder(identifier).build()
        val manager = RemoteModelManager.getInstance()
        if (!manager.isModelDownloaded(model).await()) {
            manager.download(model, DownloadConditions.Builder().build()).await()
        }
        val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
        return try {
            HandwritingIndex.groups(page.strokes).mapNotNull { group ->
                val ink = Ink.builder().apply {
                    group.strokes.forEach { sourceStroke ->
                        val stroke = Ink.Stroke.builder()
                        sourceStroke.points.forEach { point ->
                            stroke.addPoint(Ink.Point.create(point.x * page.width, point.y * page.height, point.timeMs))
                        }
                        addStroke(stroke.build())
                    }
                }.build()
                val result = recognizer.recognize(ink).await()
                result.candidates.firstOrNull()?.text?.trim()?.takeIf(String::isNotBlank)?.let { text ->
                    RecognizedTextBlock(
                        text = text,
                        bounds = group.bounds,
                        source = RecognizedTextSource.HANDWRITING,
                        sourceIds = group.strokeIds,
                        languageTag = languageTag,
                    )
                }
            }
        } finally {
            recognizer.close()
        }
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
