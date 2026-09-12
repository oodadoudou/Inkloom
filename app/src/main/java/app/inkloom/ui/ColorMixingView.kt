package app.inkloom.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ScaleGestureDetector
import kotlin.math.min
import app.inkloom.color.MixingBoard

/** Small local scratchpad; I/O never runs on the pen event path. */
internal class ColorMixingView @JvmOverloads constructor(context: Context, private val session: ColorMixingSession = ColorMixingSession(context)) : View(context) {
    val board get() = session.board
    var tool = MixingBoard.Tool.PAINT
    var ink = 0xff111111.toInt()
    var brushRadius = 12f
    var backgroundTone: Int? = 0xffeeeeea.toInt()
        set(value) { field = value; invalidate() }
    var zoom: Float
        get() = session.zoom
        private set(value) { session.zoom = value }
    private var panX: Float
        get() = session.panX * contentScale()
        set(value) { session.panX = value / contentScale() }
    private var panY: Float
        get() = session.panY * contentScale()
        set(value) { session.panY = value / contentScale() }
    private var navigating = false
    private var focusX = 0f
    private var focusY = 0f
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (navigating && session.owns(this@ColorMixingView)) zoomAt(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })
    var onPick: (Int) -> Unit = {}
    var onHistory: (Boolean, Boolean) -> Unit = { _, _ -> }
    var onStorageError: () -> Unit = {}
        set(value) { field = value; if (session.storageFailed) value() }
    private val bitmap = Bitmap.createBitmap(board.width, board.height, Bitmap.Config.ARGB_8888)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var pointer = -1
    val ready get() = session.ready
    private val displayPixels = IntArray(board.width * board.height)
    private var hoverX = -1f
    private var hoverY = -1f
    private val observer: () -> Unit = { refresh(); if (session.storageFailed) onStorageError() }
    init { isFocusable = true; refresh() }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        session.observe(observer)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val clip = canvas.save()
        canvas.clipRect(0, 0, width, height)
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
        canvas.restoreToCount(clip)
    }
    override fun onHoverEvent(event: MotionEvent): Boolean {
        hoverX = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) -1f else event.x
        hoverY = event.y; invalidate(); return true
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!ready) return true
        if (width == 0 || height == 0) return false
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            session.claim(this); pointer = -1; navigating = true
            focusX = (event.getX(0) + event.getX(1)) / 2f
            focusY = (event.getY(0) + event.getY(1)) / 2f
            parent?.requestDisallowInterceptTouchEvent(true)
            refresh()
        }
        scaler.onTouchEvent(event)
        if (navigating) {
            if (!session.owns(this)) {
                navigating = false; parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount >= 2) {
                val fx = (event.getX(0) + event.getX(1)) / 2f
                val fy = (event.getY(0) + event.getY(1)) / 2f
                panX += fx - focusX; panY += fy - focusY; focusX = fx; focusY = fy
                clampPan(); session.publish()
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                session.cancel(this); navigating = false; parent?.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
        fun x(value: Float) = boardX(value).coerceIn(0f, board.width - .001f)
        fun y(value: Float) = boardY(value).coerceIn(0f, board.height - .001f)
        fun pressure(index: Int, history: Int? = null): Float {
            val type = event.getToolType(index)
            if (type != MotionEvent.TOOL_TYPE_STYLUS && type != MotionEvent.TOOL_TYPE_ERASER) return 1f
            val value = history?.let { event.getHistoricalPressure(index, it) } ?: event.getPressure(index)
            return if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (boardX(event.x) !in 0f..board.width.toFloat() || boardY(event.y) !in 0f..board.height.toFloat()) {
                    pointer = -1
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                pointer = event.getPointerId(0); session.claim(this)
                board.begin(x(event.x), y(event.y), tool, ink, brushRadius, pressure(0))
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (index < 0 || !session.owns(this)) return true
                for (h in 0 until event.historySize) board.move(x(event.getHistoricalX(index, h)), y(event.getHistoricalY(index, h)), pressure(index, h))
                board.move(x(event.getX(index)), y(event.getY(index)), pressure(index))
            }
            MotionEvent.ACTION_UP -> {
                if (pointer < 0 || !session.owns(this)) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                val index = event.findPointerIndex(pointer).coerceAtLeast(0)
                for (h in 0 until event.historySize) board.move(x(event.getHistoricalX(index, h)), y(event.getHistoricalY(index, h)), pressure(index, h))
                // UP usually reports zero pressure after contact is lost. Retain the last
                // measured contact pressure instead of growing or cutting the stroke tip.
                if (pressure(index) > 0f) board.move(x(event.getX(index)), y(event.getY(index)), pressure(index))
                else board.move(x(event.getX(index)), y(event.getY(index)))
                if (tool == MixingBoard.Tool.PICK) {
                    val sampled = board.sample(x(event.x), y(event.y))
                    if (sampled ushr 24 != 0) onPick(backgroundTone?.let { MixingBoard.sourceOver(it, sampled, 1f) } ?: (sampled or (0xff shl 24)))
                    else backgroundTone?.let(onPick)
                }
                session.finish(this)
                pointer = -1; performClick(); parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                session.cancel(this); pointer = -1; parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        session.publish(); return true
    }
    private fun contentScale() = min(width.toFloat() / board.width, height.toFloat() / board.height).coerceAtLeast(.001f) * zoom
    private fun boardX(x: Float) = (x - width / 2f - panX) / contentScale() + board.width / 2f
    private fun boardY(y: Float) = (y - height / 2f - panY) / contentScale() + board.height / 2f
    private fun zoomAt(factor: Float, x: Float, y: Float) {
        val bx = boardX(x); val by = boardY(y)
        zoom = (zoom * factor).coerceIn(.25f, 8f)
        panX = x - width / 2f - (bx - board.width / 2f) * contentScale()
        panY = y - height / 2f - (by - board.height / 2f) * contentScale()
        clampPan(); session.publish()
    }
    private fun clampPan() {
        panX = panX.coerceIn(-board.width * contentScale() / 2f, board.width * contentScale() / 2f)
        panY = panY.coerceIn(-board.height * contentScale() / 2f, board.height * contentScale() / 2f)
    }
    fun resetViewport() { zoom = 1f; panX = 0f; panY = 0f; session.publish() }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            zoomAt(kotlin.math.exp(event.getAxisValue(MotionEvent.AXIS_VSCROLL) * .12f), event.x, event.y)
            return true
        }
        return super.onGenericMotionEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    fun undo() = session.undo()
    fun redo() = session.redo()
    fun clear() = session.clear()
    override fun onDetachedFromWindow() {
        session.cancel(this)
        session.removeObserver(observer)
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
}
