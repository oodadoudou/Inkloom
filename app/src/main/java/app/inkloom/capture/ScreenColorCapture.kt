package app.inkloom.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ED-34, CSP's Obtain screen color by hiding windows, on Android: one frame of the whole display
 * is captured with the user's screen-capture consent, kept in memory only, and shown frozen in
 * Inkloom so a colour can be tapped. The projection stops as soon as the frame is taken.
 */
object ScreenColorCapture {
    private val frameState = MutableStateFlow<Bitmap?>(null)
    private val waitingState = MutableStateFlow(false)
    /** The captured display frame waiting to be picked from, if any. */
    val frame: StateFlow<Bitmap?> = frameState.asStateFlow()
    /** True while Inkloom is hidden and waits for the user to tap the notification. */
    val waiting: StateFlow<Boolean> = waitingState.asStateFlow()

    internal fun deliver(bitmap: Bitmap) { frameState.value?.recycle(); frameState.value = bitmap; waitingState.value = false }
    internal fun setWaiting(waiting: Boolean) { waitingState.value = waiting }

    /** The picker is done with the frame. */
    fun clear() { frameState.value?.recycle(); frameState.value = null }

    /** Starts capture after consent; [hidden] waits for the notification instead of capturing now. */
    fun start(context: Context, resultCode: Int, data: Intent, hidden: Boolean) {
        val intent = Intent(context, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_START)
            .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode).putExtra(ScreenCaptureService.EXTRA_DATA, data)
            .putExtra(ScreenCaptureService.EXTRA_HIDDEN, hidden)
        context.startForegroundService(intent)
        if (hidden) setWaiting(true)
    }

    /** Takes the frame now (split screen), once Inkloom's own dialogs have closed. */
    fun captureNow(context: Context) {
        context.startService(Intent(context, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_CAPTURE))
    }

    fun cancel(context: Context) {
        setWaiting(false)
        context.startService(Intent(context, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
    }

    /** The consent intent; Android 14+ is asked for the whole display, not a single app. */
    fun consentIntent(context: Context): Intent {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        return if (Build.VERSION.SDK_INT >= 34) manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
        else manager.createScreenCaptureIntent()
    }
}

/** Holds the screen projection for one capture; required by Android to run in the foreground. */
class ScreenCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_CAPTURE -> handler.postDelayed({ capture() }, SHADE_SETTLE_MS)
            else -> stop()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        val hidden = intent.getBooleanExtra(EXTRA_HIDDEN, false)
        val notification = notification(hidden)
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
        val manager = getSystemService(MediaProjectionManager::class.java)
        val granted = data?.let { runCatching { manager.getMediaProjection(intent.getIntExtra(EXTRA_RESULT_CODE, 0), it) }.getOrNull() }
        if (granted == null) { stop(); return }
        projection = granted
        granted.registerCallback(object : MediaProjection.Callback() { override fun onStop() { release() } }, handler)
        val metrics = resources.displayMetrics
        // The whole display, including system bars, so frame pixels match screen positions.
        val real = android.graphics.Point(metrics.widthPixels, metrics.heightPixels)
        if (Build.VERSION.SDK_INT >= 30) getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds.let { real.set(it.width(), it.height()) }
        else @Suppress("DEPRECATION") getSystemService(DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY).getRealSize(real)
        val imageReader = ImageReader.newInstance(real.x, real.y, PixelFormat.RGBA_8888, 2)
        reader = imageReader
        display = granted.createVirtualDisplay("Inkloom screen colour", imageReader.width, imageReader.height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, handler)
    }

    private fun capture(attempt: Int = 0) {
        val image = reader?.acquireLatestImage()
        if (image == null) {
            if (attempt < 20 && reader != null) handler.postDelayed({ capture(attempt + 1) }, 50) else stop()
            return
        }
        try {
            val plane = image.planes[0]
            val padded = androidx.core.graphics.createBitmap(plane.rowStride / plane.pixelStride, image.height)
            padded.copyPixelsFromBuffer(plane.buffer)
            val frame = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
            if (frame !== padded) padded.recycle()
            ScreenColorCapture.deliver(frame)
        } catch (failure: RuntimeException) {
            Log.w("InkloomCapture", "Screen frame could not be read", failure)
        } finally {
            image.close()
            stop()
        }
    }

    private fun notification(hidden: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Screen colour", NotificationManager.IMPORTANCE_LOW))
        val pick = PendingIntent.getActivity(this, 1, Intent(this, ScreenColorCaptureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, 2, Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val chinese = resources.configuration.locales[0].language == "zh"
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(if (chinese) "Inkloom 屏幕取色" else "Inkloom screen colour")
            .setContentText(if (!hidden) (if (chinese) "正在读取屏幕…" else "Reading the screen…")
                else if (chinese) "打开要取色的画面后点按此处" else "Open what you want to pick from, then tap here")
            .setContentIntent(pick)
            .addAction(Notification.Action.Builder(null, if (chinese) "取消" else "Cancel", cancel).build())
            .setOngoing(true)
            .build()
    }

    private fun stop() {
        release()
        ScreenColorCapture.setWaiting(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun release() {
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
    }

    override fun onDestroy() { release(); super.onDestroy() }

    internal companion object {
        const val ACTION_START = "app.inkloom.capture.START"
        const val ACTION_CAPTURE = "app.inkloom.capture.CAPTURE"
        const val ACTION_STOP = "app.inkloom.capture.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_HIDDEN = "hidden"
        const val CHANNEL = "screen-colour"
        const val NOTIFICATION_ID = 40
        const val SHADE_SETTLE_MS = 450L
    }
}

/**
 * Opened from the notification while Inkloom is hidden. It shows nothing, so the frame captured
 * behind it is the app the user opened; then it brings Inkloom back with that frame.
 */
class ScreenColorCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_CAPTURE))
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, app.inkloom.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            finish()
        }, ScreenCaptureService.SHADE_SETTLE_MS + 600L)
    }
}
