package app.inkloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.ShortcutBrushGroupTargets
import app.inkloom.settings.ToolboxQuickSlot
import app.inkloom.settings.ToolboxQuickSlotKind
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

    @Test fun toolboxQuickSlotsSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val slot = ToolboxQuickSlot(ToolboxQuickSlotKind.TOOL_PRESET, "Red pen", "brush-1", size = 17f, opacity = .7f, colorArgb = 0xFFAA0000.toInt())
            AppSettingsStore(context).save(AppSettings(toolboxQuickSlots = listOf(slot)))

            assertEquals(slot, AppSettingsStore(context).load().toolboxQuickSlots.first())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
