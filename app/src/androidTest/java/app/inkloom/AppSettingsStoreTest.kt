package app.inkloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.ShortcutBrushGroupTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsStoreTest {
    @Test fun canvasGesturePreferencesSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(twoFingerRotation = true, threeFingerTapRedo = false))

            assertTrue(AppSettingsStore(context).load().twoFingerRotation)
            assertFalse(AppSettingsStore(context).load().threeFingerTapRedo)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun toolboxBrushGroupTargetsSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val targets = ShortcutBrushGroupTargets("Favorites/Pens", "Favorites/Pencils", "Paint/Brushes")
            AppSettingsStore(context).save(AppSettings(shortcutBrushGroupTargets = targets))

            assertEquals(targets, AppSettingsStore(context).load().shortcutBrushGroupTargets)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
