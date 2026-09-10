package app.inkloom.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AtomicFile
import android.view.MotionEvent
import android.view.View
import app.inkloom.color.MixingBoard
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Executors

/** Small local scratchpad; I/O never runs on the pen event path. */
internal class ColorMixingView(context: Context) : View(context) {
    val board = MixingBoard()
    var tool = MixingBoard.Tool.PAINT
    var ink = 0xff111111.toInt()
    var brushRadius = 12f
    var onPick: (Int) -> Unit = {}
    var onHistory: (Boolean, Boolean) -> Unit = { _, _ -> }
    var onStorageError: () -> Unit = {}
    private val bitmap = Bitmap.createBitmap(board.width, board.height, Bitmap.Config.ARGB_8888)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var pointer = -1
    private var edited = false
    var ready = false
        private set
    private val displayPixels = IntArray(board.width * board.height)
    private var hoverX = -1f
    private var hoverY = -1f
    private val saveLock = Any()
    private var pendingSave: IntArray? = null
    private var saveQueued = false
    private val storage = AtomicFile(File(context.filesDir, "color-mixing-board.bin"))
    init {
        isFocusable = true
        refresh()
        IO.execute {
            val restored = runCatching {
                if (!storage.baseFile.exists()) null else DataInputStream(storage.openRead()).use { stream ->
                    require(stream.readInt() == MAGIC)
                    require(stream.readInt() == board.width && stream.readInt() == board.height)
                    IntArray(board.width * board.height) { stream.readInt() }
                }
            }
            post {
                if (!edited) restored.getOrNull()?.let { board.restore(it); refresh() }
                ready = true
                if (restored.isFailure) onStorageError()
            }
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = android.graphics.Color.WHITE; paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        if (hoverX >= 0f) {
            val radius = if (tool == MixingBoard.Tool.PICK) 8f * resources.displayMetrics.density else brushRadius * width / board.width
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(hoverX, hoverY, radius, paint)
            paint.strokeWidth = 1f; paint.color = android.graphics.Color.DKGRAY
            canvas.drawCircle(hoverX, hoverY, radius, paint)
            if (tool == MixingBoard.Tool.PICK) {
                canvas.drawLine(hoverX - radius, hoverY, hoverX + radius, hoverY, paint)
                canvas.drawLine(hoverX, hoverY - radius, hoverX, hoverY + radius, paint)
            }
        }
    }
    override fun onHoverEvent(event: MotionEvent): Boolean {
        hoverX = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) -1f else event.x
        hoverY = event.y; invalidate(); return true
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!ready) return true
        if (width == 0 || height == 0) return false
        fun x(value: Float) = value.coerceIn(0f, width.toFloat()) * board.width / width
        fun y(value: Float) = value.coerceIn(0f, height.toFloat()) * board.height / height
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pointer = event.getPointerId(0); edited = true
                board.begin(x(event.x), y(event.y), tool, ink, brushRadius)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (index < 0) return true
                for (h in 0 until event.historySize) board.move(x(event.getHistoricalX(index, h)), y(event.getHistoricalY(index, h)))
                board.move(x(event.getX(index)), y(event.getY(index)))
            }
            MotionEvent.ACTION_UP -> {
                if (pointer < 0) return true
                board.move(x(event.x), y(event.y))
                if (tool == MixingBoard.Tool.PICK) onPick(board.sample(x(event.x), y(event.y)))
                if (board.finish()) save()
                pointer = -1; performClick(); parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                board.cancel(); pointer = -1; parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        refresh(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    fun undo() { if (board.undo()) { edited = true; refresh(); save() } }
    fun redo() { if (board.redo()) { edited = true; refresh(); save() } }
    fun clear() { if (board.clear()) { edited = true; refresh(); save() } }
    override fun onDetachedFromWindow() { board.cancel(); pointer = -1; super.onDetachedFromWindow() }
    private fun refresh() {
        board.copyPixelsTo(displayPixels)
        bitmap.setPixels(displayPixels, 0, board.width, 0, 0, board.width, board.height)
        onHistory(board.canUndo, board.canRedo); invalidate()
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
                if (result.isFailure) post { onStorageError() }
            }
        }
    }
    companion object {
        private const val MAGIC = 0x494d5831
        private val IO = Executors.newSingleThreadExecutor { work -> Thread(work, "Inkloom-mixing-store").apply { isDaemon = true } }
    }
}
