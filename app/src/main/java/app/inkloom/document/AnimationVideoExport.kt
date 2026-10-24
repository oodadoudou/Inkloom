package app.inkloom.document

import androidx.core.graphics.scale
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.FileDescriptor
import kotlin.math.roundToInt

/**
 * MP4 (H.264) export of an animation folder, as CSP's File > Export animation > Movie: every
 * animation frame at the folder's frame rate, each cel repeated for its duration. Frames are
 * rendered like the animated GIF (paper and the other layers, never onion skin) and given to the
 * encoder as YUV with explicit timestamps, so timing is exact whatever the device's encoder.
 */
object AnimationVideoExport {
    private const val STALL_NANOS = 20_000_000_000L
    /** H.264 encoders want even sizes, many a multiple of 16. */
    fun videoSize(width: Int, pageWidth: Float, pageHeight: Float): Pair<Int, Int> {
        val w = ((width.coerceIn(160, 1920) + 8) / 16) * 16
        val h = (((w * pageHeight / pageWidth.coerceAtLeast(1f)).roundToInt() + 8) / 16 * 16).coerceIn(16, 2160)
        return w to h
    }

    /** The cel shown at each animation frame, in order. */
    fun frameCels(page: InkPage, folderId: String): List<String> {
        val folder = page.layers.firstOrNull { it.id == folderId }?.animation ?: return emptyList()
        return folder.cels.filter { cel -> page.layers.any { it.id == cel.layerId } }.flatMap { cel -> List(cel.frames) { cel.layerId } }
    }

    fun write(assets: DocumentAssetService, page: InkPage, folderId: String, width: Int, output: FileDescriptor, onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        val folder = requireNotNull(page.layers.firstOrNull { it.id == folderId }?.animation) { "The animation folder is gone" }
        val cels = frameCels(page, folderId)
        require(cels.isNotEmpty()) { "The animation has no cels" }
        val (w, h) = videoSize(width, PageCropTransform.visibleWidth(page), PageCropTransform.visibleHeight(page))
        val fps = folder.frameRate
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        // Plain planar or semi-planar YUV in a byte buffer works on every encoder; flexible images are the fallback.
        val offered = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats.toSet()
        @Suppress("DEPRECATION") val colorFormat = listOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar).firstOrNull { it in offered }
            ?: MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, (w * h * fps / 4).coerceIn(500_000, 20_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxing = false
        val info = MediaCodec.BufferInfo()
        // Each cel is rendered once; repeated frames reuse its YUV planes.
        val rendered = HashMap<String, ByteArray>()
        fun yuv(celId: String): ByteArray = rendered.getOrPut(celId) {
            val pagePixels = assets.renderPageBitmap(AnimationEdits.show(page, folderId, celId), w)
            val scaled = pagePixels.scale(w, h)
            try {
                val argb = IntArray(w * h).also { scaled.getPixels(it, 0, w, 0, 0, w, h) }
                VideoFrames.i420(argb, w, h)
            } finally { if (scaled !== pagePixels) scaled.recycle(); pagePixels.recycle() }
        }
        // An encoder that stops answering must fail the export, never hang it.
        var lastProgress = System.nanoTime()
        fun stalled() = check(System.nanoTime() - lastProgress < STALL_NANOS) { "The video encoder stopped responding" }
        var finished = false
        fun drain(endOfStream: Boolean) {
            while (!finished) {
                stalled()
                val index = codec.dequeueOutputBuffer(info, 10_000)
                if (index >= 0 || index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) lastProgress = System.nanoTime()
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { check(!muxing); track = muxer.addTrack(codec.outputFormat); muxer.start(); muxing = true }
                    index >= 0 -> {
                        val buffer = requireNotNull(codec.getOutputBuffer(index))
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxing) {
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        // The end of the stream can come out during any drain, not only the last.
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { finished = true; return }
                    }
                }
            }
        }
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            var frame = 0
            while (frame <= cels.size) {
                stalled()
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    lastProgress = System.nanoTime()
                    val pts = frame * 1_000_000L / fps
                    if (frame == cels.size) codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    else {
                        val frameYuv = yuv(cels[frame])
                        @Suppress("DEPRECATION")
                        when (colorFormat) {
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar -> requireNotNull(codec.getInputBuffer(index)).apply { clear(); put(frameYuv) }
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar -> requireNotNull(codec.getInputBuffer(index)).apply { clear(); put(VideoFrames.nv12(frameYuv, w, h)) }
                            else -> VideoFrames.fill(requireNotNull(codec.getInputImage(index)) { "The video encoder gave no input image" }, frameYuv, w, h)
                        }
                        codec.queueInputBuffer(index, 0, w * h * 3 / 2, pts, 0)
                        onProgress(frame + 1, cels.size)
                    }
                    frame++
                }
                drain(endOfStream = false)
            }
            drain(endOfStream = true)
            check(muxing) { "The video encoder produced no output" }
        } finally {
            runCatching { codec.stop() }; codec.release()
            if (muxing) runCatching { muxer.stop() }
            muxer.release()
        }
    }
}

/** RGB to YUV 4:2:0 (BT.601, limited range) and copying it into an encoder's input image whatever its plane layout. */
object VideoFrames {
    fun i420(argb: IntArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h * 3 / 2)
        val uOffset = w * h; val vOffset = uOffset + w * h / 4
        for (y in 0 until h) for (x in 0 until w) {
            val p = argb[y * w + x]
            // Transparency shows white, as on paper.
            val a = (p ushr 24 and 255) / 255f
            val r = ((p ushr 16 and 255) * a + 255 * (1 - a)); val g = ((p ushr 8 and 255) * a + 255 * (1 - a)); val b = ((p and 255) * a + 255 * (1 - a))
            out[y * w + x] = (16 + .257f * r + .504f * g + .098f * b).roundToInt().coerceIn(0, 255).toByte()
            if (y % 2 == 0 && x % 2 == 0) {
                val i = (y / 2) * (w / 2) + x / 2
                out[uOffset + i] = (128 - .148f * r - .291f * g + .439f * b).roundToInt().coerceIn(0, 255).toByte()
                out[vOffset + i] = (128 + .439f * r - .368f * g - .071f * b).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return out
    }

    /** I420 to NV12: the same luma, then interleaved U and V. */
    fun nv12(i420: ByteArray, w: Int, h: Int): ByteArray {
        val out = i420.copyOf(); val luma = w * h; val quarter = luma / 4
        for (i in 0 until quarter) { out[luma + 2 * i] = i420[luma + i]; out[luma + 2 * i + 1] = i420[luma + quarter + i] }
        return out
    }

    fun fill(image: android.media.Image, i420: ByteArray, w: Int, h: Int) {
        val planes = image.planes
        val offsets = intArrayOf(0, w * h, w * h + w * h / 4)
        for (plane in 0 until 3) {
            val pw = if (plane == 0) w else w / 2; val ph = if (plane == 0) h else h / 2
            val buffer = planes[plane].buffer; val row = planes[plane].rowStride; val pixel = planes[plane].pixelStride
            for (y in 0 until ph) for (x in 0 until pw) buffer.put(y * row + x * pixel, i420[offsets[plane] + y * pw + x])
        }
    }
}
