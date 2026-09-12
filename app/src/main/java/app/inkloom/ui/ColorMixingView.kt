package app.inkloom.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AtomicFile
import android.view.MotionEvent
import android.view.View
import android.view.ScaleGestureDetector
import kotlin.math.min
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
    var backgroundTone: Int? = 0xffeeeeea.toInt()
        set(value) { field = value; invalidate() }
    var zoom = 1f
        private set
    private var panX = 0f
    private var panY = 0f
    private var navigating = false
    private var focusX = 0f
    private var focusY = 0f
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomAt(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })
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
        val tone = backgroundTone
        if (tone != null) canvas.drawColor(tone) else {
            val cell = 8f * resources.displayMetrics.density
            for (y in 0..(height / cell).toInt()) for (x in 0..(width / cell).toInt()) {
                paint.color = if ((x + y) % 2 == 0) 0xffc5c5c5.toInt() else 0xffeeeeee.toInt()
                canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint)
            }
        }
        paint.color = android.graphics.Color.WHITE
        val scale = contentScale()
        val left = width / 2f - board.width * scale / 2f + panX
        val top = height / 2f - board.height * scale / 2f + panY
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + board.width * scale, top + board.height * scale), paint)
        if (hoverX >= 0f) {
            val radius = if (tool == MixingBoard.Tool.PICK) 8f * resources.displayMetrics.density else brushRadius * contentScale()
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
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            board.cancel(); pointer = -1; navigating = true
            focusX = (event.getX(0) + event.getX(1)) / 2f
            focusY = (event.getY(0) + event.getY(1)) / 2f
            parent?.requestDisallowInterceptTouchEvent(true)
            refresh()
        }
        scaler.onTouchEvent(event)
        if (navigating) {
            if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount >= 2) {
                val fx = (event.getX(0) + event.getX(1)) / 2f
                val fy = (event.getY(0) + event.getY(1)) / 2f
                panX += fx - focusX; panY += fy - focusY; focusX = fx; focusY = fy
                clampPan(); invalidate()
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                navigating = false; parent?.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
        fun x(value: Float) = boardX(value).coerceIn(0f, board.width - .001f)
        fun y(value: Float) = boardY(value).coerceIn(0f, board.height - .001f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (boardX(event.x) !in 0f..board.width.toFloat() || boardY(event.y) !in 0f..board.height.toFloat()) {
                    pointer = -1
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
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
                if (pointer < 0) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                board.move(x(event.x), y(event.y))
                if (tool == MixingBoard.Tool.PICK) {
                    val sampled = board.sample(x(event.x), y(event.y))
                    if (sampled ushr 24 != 0) onPick(backgroundTone?.let { MixingBoard.sourceOver(it, sampled, 1f) } ?: (sampled or (0xff shl 24)))
                    else backgroundTone?.let(onPick)
                }
                if (board.finish()) save()
                pointer = -1; performClick(); parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                board.cancel(); pointer = -1; parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        refresh(); return true
    }
    private fun contentScale() = min(width.toFloat() / board.width, height.toFloat() / board.height).coerceAtLeast(.001f) * zoom
    private fun boardX(x: Float) = (x - width / 2f - panX) / contentScale() + board.width / 2f
    private fun boardY(y: Float) = (y - height / 2f - panY) / contentScale() + board.height / 2f
    private fun zoomAt(factor: Float, x: Float, y: Float) {
        val bx = boardX(x); val by = boardY(y)
        zoom = (zoom * factor).coerceIn(.25f, 8f)
        panX = x - width / 2f - (bx - board.width / 2f) * contentScale()
        panY = y - height / 2f - (by - board.height / 2f) * contentScale()
        clampPan(); invalidate()
    }
    private fun clampPan() {
        panX = panX.coerceIn(-board.width * contentScale() / 2f, board.width * contentScale() / 2f)
        panY = panY.coerceIn(-board.height * contentScale() / 2f, board.height * contentScale() / 2f)
    }
    fun resetViewport() { zoom = 1f; panX = 0f; panY = 0f; invalidate() }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            zoomAt(kotlin.math.exp(event.getAxisValue(MotionEvent.AXIS_VSCROLL) * .12f), event.x, event.y)
            return true
        }
        return super.onGenericMotionEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    fun undo() { if (board.undo()) { edited = true; refresh(); save() } }
    fun redo() { if (board.redo()) { edited = true; refresh(); save() } }
    fun clear() { if (board.clear()) { edited = true; refresh(); save() } }
    override fun onDetachedFromWindow() {
        board.cancel()
        pointer = -1; navigating = false; hoverX = -1f
        parent?.requestDisallowInterceptTouchEvent(false)
        // This view survives color-tab changes. The cached bitmap must reflect the
        // rolled-back board even when detaching did not first deliver ACTION_CANCEL.
        refresh()
        super.onDetachedFromWindow()
    }
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
