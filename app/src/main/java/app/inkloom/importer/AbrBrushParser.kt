package app.inkloom.importer

import android.graphics.Bitmap
import android.graphics.Color
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import java.io.File
import java.util.UUID

class AbrBrushParser(private val assetDirectory: File) {
    init { assetDirectory.mkdirs() }

    fun enrich(source: File, hash: String, fallback: ImportedBrush): List<ImportedBrush> = runCatching {
        val samples = AbrSampleDecoder.decode(source)
        val outputDirectory = File(assetDirectory, hash).apply { mkdirs() }
        samples.mapIndexed { index, sample ->
            val output = File(outputDirectory, "tip-${(index + 1).toString().padStart(3, '0')}.png")
            if (!output.isFile) writeTip(sample, output)
            fallback.copy(
                id = if (index == 0) fallback.id else UUID.randomUUID().toString(),
                name = if (samples.size == 1) fallback.name else "${fallback.name} ${index + 1}",
                compatibility = Compatibility.EXTRACTED,
                tipPreviewPath = output.absolutePath,
                tipPreviewPaths = listOf(output.absolutePath),
                parserVersion = CURRENT_VERSION,
                report = listOf(
                    "ABR 6.x sampled tip ${index + 1}/${samples.size} extracted at ${sample.width}×${sample.height}",
                    if (sample.compressed) "Photoshop PackBits/RLE mask decoded" else "Photoshop raw grayscale mask decoded",
                    "Opacity mask mapped without combining unrelated tips into one stroke",
                    "ABR dynamics and proprietary painting-engine behavior remain on safe Inkloom defaults",
                    "Original source retained with SHA-256 integrity hash",
                ),
            )
        }
    }.getOrElse { failure ->
        listOf(fallback.copy(
            compatibility = Compatibility.BASIC,
            parserVersion = CURRENT_VERSION,
            report = (fallback.report + "ABR tip extraction failed safely: ${failure.message ?: failure::class.java.simpleName}").distinct(),
        ))
    }

    private fun writeTip(sample: AbrSample, output: File) {
        val pixels = IntArray(sample.opacity.size) { index ->
            // Inkloom consumes black as paint and white as transparent; ABR stores the inverse.
            val luminance = 255 - (sample.opacity[index].toInt() and 0xff)
            Color.rgb(luminance, luminance, luminance)
        }
        val bitmap = Bitmap.createBitmap(pixels, sample.width, sample.height, Bitmap.Config.ARGB_8888)
        try {
            output.outputStream().use { stream -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
        } finally {
            bitmap.recycle()
        }
    }

    companion object { const val CURRENT_VERSION = 1 }
}
