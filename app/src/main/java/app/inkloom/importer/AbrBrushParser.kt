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
        val settings = AbrDescriptorDecoder.decode(source)
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
                defaultSize = settings?.diameterPixels?.coerceIn(1f, 400f) ?: fallback.defaultSize,
                spacing = settings?.spacingPercent?.coerceIn(.01f, 2f) ?: fallback.spacing,
                hardness = settings?.hardnessPercent?.coerceIn(0f, 1f) ?: fallback.hardness,
                thickness = settings?.roundnessPercent?.coerceIn(.05f, 2f) ?: fallback.thickness,
                rotationDegrees = settings?.angleDegrees ?: fallback.rotationDegrees,
                parserVersion = CURRENT_VERSION,
                report = listOf(
                    "ABR 6.x sampled tip ${index + 1}/${samples.size} extracted at ${sample.width}×${sample.height}",
                    if (sample.compressed) "Photoshop PackBits/RLE mask decoded" else "Photoshop raw grayscale mask decoded",
                    if (settings != null) {
                        val parts = mutableListOf<String>()
                        settings.spacingPercent?.let { parts += "spacing ${"%.1f".format(it)}%" }
                        settings.hardnessPercent?.let { parts += "hardness ${"%.1f".format(it)}%" }
                        settings.roundnessPercent?.let { parts += "roundness ${"%.1f".format(it)}%" }
                        settings.angleDegrees?.let { parts += "angle ${"%.1f".format(it)}°" }
                        settings.diameterPixels?.let { parts += "diameter ${"%.1f".format(it)}px" }
                        "ABR brush descriptor decoded: ${parts.joinToString(", ")}"
                    } else "ABR brush descriptor absent; using defaults",
                    "Opacity mask mapped without combining unrelated tips into one stroke",
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
