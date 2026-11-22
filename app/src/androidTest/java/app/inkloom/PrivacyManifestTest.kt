package app.inkloom

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import app.inkloom.document.OnDeviceTextRecognitionService
import com.google.android.datatransport.Encoding
import com.google.android.datatransport.Event
import com.google.android.datatransport.Transformer
import com.google.android.datatransport.cct.CCTDestination
import com.google.android.datatransport.runtime.TransportRuntime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Libraries must not start usage telemetry: the app is offline-first and sends nothing about the device. */
class PrivacyManifestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun onnxRuntimeTelemetryProviderIsRemoved() {
        assertNull(context.packageManager.resolveContentProvider("${context.packageName}.onnxruntime_telemetry_initializer", 0))
        val providers = packageInfo(PackageManager.GET_PROVIDERS).providers.orEmpty()
        assertTrue(providers.map { it.name }.toString(), providers.none { it.name.contains("Telemetry", ignoreCase = true) })
    }

    @Test fun superResolutionStartsWithTelemetryOff() {
        app.inkloom.ml.SuperResolution(context).close()
    }

    /** Network access exists only for the GitHub release check; libraries may not add more. */
    @Test fun networkAccessIsLimitedToTheUpdateCheck() {
        val requested = packageInfo(PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertTrue(requested.toString(), Manifest.permission.INTERNET in requested)
        assertTrue(requested.toString(), Manifest.permission.ACCESS_NETWORK_STATE !in requested)
        assertTrue(requested.toString(), requested.none { it.contains("WIFI") || it.contains("ACCOUNTS") || it.contains("AD_ID") })
    }

    @Test fun mlKitUsageLogsAreDroppedInsteadOfQueuedForUpload() {
        val info = packageInfo(PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS)
        val components = info.services.orEmpty().map { it.name } + info.receivers.orEmpty().map { it.name }
        assertTrue(components.toString(), components.none { it.startsWith("com.google.android.datatransport.") })
        // ML Kit sends its FIREBASE_ML_SDK logs this way; with no upload backend they are refused before being stored.
        TransportRuntime.initialize(context)
        val scheduled = CompletableFuture<Exception?>()
        TransportRuntime.getInstance().newFactory(CCTDestination.INSTANCE)
            .getTransport("FIREBASE_ML_SDK", ByteArray::class.java, Encoding.of("proto"), Transformer<ByteArray, ByteArray> { it })
            .schedule(Event.ofTelemetry(byteArrayOf(0))) { scheduled.complete(it) }
        val refusal = scheduled.get(10, TimeUnit.SECONDS)
        assertTrue(refusal.toString(), refusal?.message.orEmpty().contains("not registered"))
    }

    @Test fun bundledTextRecognitionNeedsNoNetwork() = runBlocking {
        val recognition = OnDeviceTextRecognitionService(context)
        for ((text, language) in listOf("OFFLINE OCR 123" to "en-US", "离线识别" to "zh-CN")) {
            val image = textImage(text)
            val blocks = try { withTimeout(45_000) { recognition.recognizeBitmap(image, "privacy", language) } } finally { image.recycle() }
            val found = blocks.joinToString(" ") { it.text }
            assertTrue("$language: $found", found.filterNot(Char::isWhitespace).contains(text.filterNot(Char::isWhitespace), ignoreCase = true))
        }
    }

    private fun packageInfo(flags: Int): PackageInfo = context.packageManager.getPackageInfo(context.packageName, flags)

    private fun textImage(text: String): Bitmap = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888).also { image ->
        Canvas(image).apply {
            drawColor(Color.WHITE)
            drawText(text, 90f, 230f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 96f; color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD })
        }
    }
}
