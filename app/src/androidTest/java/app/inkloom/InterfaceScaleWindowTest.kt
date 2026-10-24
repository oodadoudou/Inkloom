package app.inkloom

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.settings.InterfaceScale
import app.inkloom.ui.theme.InterfaceScaling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The interface size applies to the whole activity, including its dialog windows, and changes take effect at once. */
class InterfaceScaleWindowTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)

    private fun density(scenario: ActivityScenario<MainActivity>): Pair<Float, Float> {
        var activity = 0f; var dialog = 0f
        scenario.onActivity { current: Activity ->
            activity = current.resources.displayMetrics.density
            dialog = Dialog(current).context.resources.displayMetrics.density
        }
        return activity to dialog
    }

    @Test fun aChosenSizeScalesTheActivityAndItsDialogsAndChangingItAppliesImmediately() {
        val base = context.resources.displayMetrics.density
        val automatic = InterfaceScaling.resolve(context, InterfaceScale.AUTO)
        check(preferences.edit().putFloat(InterfaceScale.PREFERENCE_KEY, .7f).commit())
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val (activity, dialog) = density(scenario)
                assertEquals(base * .7f, activity, .01f)
                assertEquals(activity, dialog, .001f)
                assertEquals(.7f, InterfaceScaling.applied, .001f)
                // The application context, used to measure the screen, keeps Android's own size.
                assertEquals(base, context.resources.displayMetrics.density, .001f)

                // Choosing automatic sizing recreates the activity at the measured size.
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    check(preferences.edit().putFloat(InterfaceScale.PREFERENCE_KEY, InterfaceScale.AUTO).commit())
                }
                val deadline = SystemClock.uptimeMillis() + 10_000
                while (SystemClock.uptimeMillis() < deadline && kotlin.math.abs(density(scenario).first - base * automatic) > .01f) {
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    SystemClock.sleep(100)
                }
                assertEquals(base * automatic, density(scenario).first, .01f)
                assertTrue(kotlin.math.abs(InterfaceScaling.applied - automatic) < .001f)
            }
        } finally {
            preferences.edit().remove(InterfaceScale.PREFERENCE_KEY).commit()
        }
    }
}
