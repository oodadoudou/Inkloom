package app.inkloom.settings

import android.graphics.BitmapFactory
import kotlin.math.roundToInt

/** Deterministic, bounded dominant-color extraction for palette creation. */
object ImagePaletteExtractor {
    private const val MAX_DECODE_SIDE = 1024
    private const val MAX_SAMPLES = 24_000

    fun extractEncoded(bytes: ByteArray, colorCount: Int = 12): List<Int> {
        require(bytes.isNotEmpty()) { "Image file is empty" }
        require(bytes.size <= ColorSetImport.MAX_FILE_BYTES) { "Image file exceeds 16 MB" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a readable image" }
        var sample = 1
        while (bounds.outWidth / sample > MAX_DECODE_SIDE || bounds.outHeight / sample > MAX_DECODE_SIDE) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })) {
            "The selected image could not be decoded"
        }
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            extract(pixels, colorCount)
        } finally {
            bitmap.recycle()
        }
    }

    fun extract(pixels: IntArray, colorCount: Int = 12): List<Int> {
        require(colorCount in 2..32) { "Palette size must be between 2 and 32" }
        val stride = (pixels.size / MAX_SAMPLES).coerceAtLeast(1)
        val bins = linkedMapOf<Int, Bin>()
        var index = 0
        while (index < pixels.size) {
            val color = pixels[index]
            val alpha = color ushr 24 and 0xff
            if (alpha >= 64) {
                val red = color ushr 16 and 0xff
                val green = color ushr 8 and 0xff
                val blue = color and 0xff
                val key = (red shr 3 shl 10) or (green shr 3 shl 5) or (blue shr 3)
                val bin = bins.getOrPut(key) { Bin() }
                bin.red += red; bin.green += green; bin.blue += blue; bin.weight++
            }
            index += stride
        }
        require(bins.isNotEmpty()) { "Image contains no visible colors" }
        val points = bins.values.map { Point(it.red / it.weight.toDouble(), it.green / it.weight.toDouble(), it.blue / it.weight.toDouble(), it.weight) }
        val count = minOf(colorCount, points.size)
        val centers = mutableListOf(points.maxBy { it.weight }.withoutWeight())
        while (centers.size < count) {
            val next = points.maxBy { point -> centers.minOf { distanceSquared(point, it) } * point.weight }
            centers += next.withoutWeight()
        }
        val assignments = IntArray(points.size)
        repeat(14) {
            points.forEachIndexed { pointIndex, point -> assignments[pointIndex] = centers.indices.minBy { distanceSquared(point, centers[it]) } }
            centers.indices.forEach { centerIndex ->
                var weight = 0L; var red = 0.0; var green = 0.0; var blue = 0.0
                points.forEachIndexed { pointIndex, point ->
                    if (assignments[pointIndex] == centerIndex) {
                        weight += point.weight; red += point.red * point.weight; green += point.green * point.weight; blue += point.blue * point.weight
                    }
                }
                if (weight > 0) centers[centerIndex] = Point(red / weight, green / weight, blue / weight, weight.toInt())
            }
        }
        val clusterWeights = LongArray(centers.size)
        points.forEachIndexed { pointIndex, point -> clusterWeights[assignments[pointIndex]] += point.weight.toLong() }
        return centers.indices.sortedByDescending { clusterWeights[it] }.map { centers[it].toArgb() }
            .fold(mutableListOf()) { result, color ->
                if (result.none { colorDistanceSquared(it, color) < 144 }) result += color
                result
            }
    }

    private data class Bin(var red: Long = 0, var green: Long = 0, var blue: Long = 0, var weight: Int = 0)
    private data class Point(val red: Double, val green: Double, val blue: Double, val weight: Int) {
        fun withoutWeight() = copy(weight = 0)
        fun toArgb() = (0xff shl 24) or (red.roundToInt().coerceIn(0, 255) shl 16) or
            (green.roundToInt().coerceIn(0, 255) shl 8) or blue.roundToInt().coerceIn(0, 255)
    }
    private fun distanceSquared(first: Point, second: Point): Double {
        val red = first.red - second.red; val green = first.green - second.green; val blue = first.blue - second.blue
        return red * red * .24 + green * green * .68 + blue * blue * .08
    }
    private fun colorDistanceSquared(first: Int, second: Int): Int {
        val red = (first ushr 16 and 0xff) - (second ushr 16 and 0xff)
        val green = (first ushr 8 and 0xff) - (second ushr 8 and 0xff)
        val blue = (first and 0xff) - (second and 0xff)
        return red * red + green * green + blue * blue
    }
}
