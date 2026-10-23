package app.inkloom

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Libraries must not start usage telemetry: the app is offline-first and sends nothing about the device. */
class PrivacyManifestTest {
    @Test fun onnxRuntimeTelemetryProviderIsRemoved() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertNull(context.packageManager.resolveContentProvider("${context.packageName}.onnxruntime_telemetry_initializer", 0))
        val providers = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
        assertTrue(providers.map { it.name }.toString(), providers.none { it.name.contains("Telemetry", ignoreCase = true) })
    }

    @Test fun superResolutionStartsWithTelemetryOff() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        app.inkloom.ml.SuperResolution(context).close()
    }
}
