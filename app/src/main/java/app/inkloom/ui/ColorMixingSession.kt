package app.inkloom.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import app.inkloom.color.MixingBoard
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Executors

/** Main-thread model and a single coalescing writer, independent of palette View lifetimes. */
internal class ColorMixingSession(context: Context) {
    val board = MixingBoard()
    var zoom = 1f
    // Document-space offset keeps the same board location centered when the palette changes size.
    var panX = 0f
    var panY = 0f
    var ready = false
        private set
    var storageFailed = false
        private set
    private var owner: Any? = null
    private val observers = mutableSetOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val storage = AtomicFile(File(context.filesDir, "color-mixing-board.bin"))
    private val saveLock = Any()
    private var pendingSave: IntArray? = null
    private var saveQueued = false

    init {
        IO.execute {
            val restored = runCatching {
                if (!storage.baseFile.exists()) null else DataInputStream(storage.openRead()).use { stream ->
                    require(stream.readInt() == MAGIC)
                    require(stream.readInt() == board.width && stream.readInt() == board.height)
                    IntArray(board.width * board.height) { stream.readInt() }
                }
            }
            main.post {
                restored.getOrNull()?.let(board::restore)
                ready = true
                storageFailed = restored.isFailure
                publish()
            }
        }
    }

    fun observe(observer: () -> Unit) { observers.add(observer); observer() }
    fun removeObserver(observer: () -> Unit) { observers.remove(observer) }
    fun publish() { observers.toList().forEach { it() } }
    fun owns(candidate: Any) = owner === candidate
    fun claim(candidate: Any) {
        board.cancel()
        owner = candidate
        publish()
    }
    fun cancel(candidate: Any) {
        if (!owns(candidate)) return
        owner = null
        board.cancel()
        publish()
    }
    fun finish(candidate: Any) {
        if (!owns(candidate)) return
        owner = null
        if (board.finish()) save()
        publish()
    }
    fun undo() = edit { board.undo() }
    fun redo() = edit { board.redo() }
    fun clear() = edit { board.clear() }
    private inline fun edit(operation: () -> Boolean) {
        if (!ready) return
        owner = null
        if (operation()) save()
        publish()
    }
    private fun save() {
        synchronized(saveLock) {
            pendingSave = board.snapshot()
            if (saveQueued) return
            saveQueued = true
        }
        IO.execute {
            while (true) {
                val pixels = synchronized(saveLock) {
                    pendingSave.also { pendingSave = null; if (it == null) saveQueued = false }
                } ?: break
                val result = runCatching {
                    val output = storage.startWrite()
                    try {
                        val stream = DataOutputStream(java.io.BufferedOutputStream(output))
                        stream.writeInt(MAGIC); stream.writeInt(board.width); stream.writeInt(board.height)
                        pixels.forEach(stream::writeInt); stream.flush(); storage.finishWrite(output)
                    } catch (error: Exception) { storage.failWrite(output); throw error }
                }
                if (result.isFailure) main.post { storageFailed = true; publish() }
            }
        }
    }
    companion object {
        private const val MAGIC = 0x494d5831
        private val IO = Executors.newSingleThreadExecutor { work -> Thread(work, "Inkloom-mixing-store").apply { isDaemon = true } }
    }
}
