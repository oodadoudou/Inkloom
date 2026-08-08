package app.inkloom.document

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

class AudioNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L

    fun start() {
        check(recorder == null) { "A recording is already active" }
        val directory = File(context.filesDir, "audio-notes").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setAudioEncodingBitRate(128_000)
            mediaRecorder.setOutputFile(target.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            output = target
            startedAt = System.currentTimeMillis()
        } catch (failure: Throwable) {
            runCatching { mediaRecorder.release() }
            target.delete()
            throw failure
        }
    }

    fun stop(title: String = "Audio note"): PageElement.AudioMarker {
        val active = recorder ?: error("No recording is active")
        val target = output ?: error("Recording has no output")
        val duration = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        try {
            active.stop()
        } catch (failure: RuntimeException) {
            target.delete()
            throw IllegalStateException("The recording was too short or could not be finalized", failure)
        } finally {
            active.release()
            recorder = null
            output = null
            startedAt = 0L
        }
        return PageElement.AudioMarker(x = .18f, y = .18f, assetPath = target.absolutePath, title = title.trim().ifBlank { "Audio note" }, durationMs = duration)
    }

    fun cancel() {
        val active = recorder ?: return
        runCatching { active.stop() }
        active.release()
        recorder = null
        output?.delete()
        output = null
        startedAt = 0L
    }
}
