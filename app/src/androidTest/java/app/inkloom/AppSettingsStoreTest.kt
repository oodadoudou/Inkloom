package app.inkloom

import android.content.Context
import android.content.res.Configuration
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.BuiltInWorkspaces
import app.inkloom.settings.captureToolboxWorkspace
import app.inkloom.settings.AxisShortcutBinding
import app.inkloom.settings.ButtonShortcutBinding
import app.inkloom.settings.CustomWorkspaces
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutBrushGroupTargets
import app.inkloom.settings.ShortcutTrigger
import app.inkloom.settings.StylusButtonAction
import app.inkloom.settings.ToolboxQuickSlot
import app.inkloom.settings.ToolboxQuickSlotKind
import app.inkloom.settings.ToolbarDock
import app.inkloom.settings.ToolboxAxis
import app.inkloom.settings.ToolboxAxisMode
import app.inkloom.settings.WorkspacePanelDock
import app.inkloom.settings.WorkspacePanelMode
import app.inkloom.settings.WorkspacePanelKind
import app.inkloom.settings.WorkspaceOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsStoreTest {
    @Test fun legacyWorkspaceMigratesToTheProcreateStudioOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            preferences.edit()
                .putBoolean("showPageRail", false)
                .putBoolean("showBrushPanel", false)
                .putBoolean("showLayerPanel", false)
                .putString("toolbarDock", ToolbarDock.RIGHT.name)
                .commit()

            val migrated = AppSettingsStore(context).load()

            assertEquals(BuiltInWorkspaces.snapshot(app.inkloom.settings.BuiltInWorkspace.STUDIO), migrated.captureToolboxWorkspace())
            assertEquals(2, preferences.getInt("studioWorkspaceVersion", 0))
            assertEquals(migrated, AppSettingsStore(context).load())
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun rulerGuideSurvivesReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(rulerEnabled = true, rulerCenterX = .3f, rulerCenterY = .7f, rulerAngleDegrees = -45f, rulerSnapDegrees = 5f))

            val restored = AppSettingsStore(context).load()
            assertTrue(restored.rulerEnabled)
            assertEquals(.3f, restored.rulerCenterX)
            assertEquals(.7f, restored.rulerCenterY)
            assertEquals(-45f, restored.rulerAngleDegrees)
            assertEquals(5f, restored.rulerSnapDegrees)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun gridAndAlignmentGuidesSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(gridSnapEnabled = true, gridSpacing = 96f, alignmentGuidesEnabled = true, verticalGuideX = .2f, horizontalGuideY = .8f))
            val restored = AppSettingsStore(context).load()

            assertTrue(restored.gridSnapEnabled)
            assertEquals(96f, restored.gridSpacing)
            assertTrue(restored.alignmentGuidesEnabled)
            assertEquals(.2f, restored.verticalGuideX)
            assertEquals(.8f, restored.horizontalGuideY)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun canvasGesturePreferencesSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(singleFingerDoubleTapUndo = true, twoFingerRotation = true, threeFingerTapRedo = false))

            assertTrue(AppSettingsStore(context).load().singleFingerDoubleTapUndo)
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

    @Test fun toolboxMotionMappingsSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val axes = listOf(AxisShortcutBinding(ToolboxAxis.ROTARY, ToolboxAxisMode.BRUSH_SIZE, reversed = true, sensitivity = 2f, deviceDescriptor = "dial"))
            val buttons = listOf(ButtonShortcutBinding(MotionEvent.BUTTON_BACK, ShortcutAction.PEN_GROUP, ShortcutTrigger.HOLD, deviceDescriptor = "keys"))
            AppSettingsStore(context).save(AppSettings(shortcutAxisBindings = axes, shortcutButtonBindings = buttons))

            val restored = AppSettingsStore(context).load()
            assertEquals(axes, restored.shortcutAxisBindings)
            assertEquals(buttons, restored.shortcutButtonBindings)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun stylusButtonCommandsSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(
                stylusPrimaryButton = StylusButtonAction.UNDO,
                stylusSecondaryButton = StylusButtonAction.HAND,
                stylusPrimaryTrigger = ShortcutTrigger.PRESS,
                stylusSecondaryTrigger = ShortcutTrigger.HOLD,
            ))

            val restored = AppSettingsStore(context).load()
            assertEquals(StylusButtonAction.UNDO, restored.stylusPrimaryButton)
            assertEquals(StylusButtonAction.HAND, restored.stylusSecondaryButton)
            assertEquals(ShortcutTrigger.PRESS, restored.stylusPrimaryTrigger)
            assertEquals(ShortcutTrigger.HOLD, restored.stylusSecondaryTrigger)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun namedCustomWorkspacesSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val profile = CustomWorkspaces.capture(
                "workspace-1",
                "Drawing desk",
                AppSettings(showBrushPanel = true, brushPanelDock = WorkspacePanelDock.LEFT),
                WorkspaceOrientation.LANDSCAPE,
            )
            AppSettingsStore(context).save(AppSettings(customWorkspaces = listOf(profile)))

            assertEquals(listOf(profile), AppSettingsStore(context).load().customWorkspaces)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun landscapeAndPortraitWorkspacesSurviveRotationIndependently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val landscapeContext = context.withOrientation(Configuration.ORIENTATION_LANDSCAPE)
        val portraitContext = context.withOrientation(Configuration.ORIENTATION_PORTRAIT)
        try {
            AppSettingsStore(landscapeContext).save(AppSettings(
                showPageRail = false,
                showBrushPanel = true,
                showLayerPanel = false,
                brushPanelDock = WorkspacePanelDock.FREE,
                brushPanelMode = WorkspacePanelMode.COLLAPSED,
                brushPanelLocked = true,
                rightDockStacked = true,
                rightDockActivePanel = WorkspacePanelKind.BRUSHES,
                panelTabOrder = "BRUSHES,LAYERS,PAGES",
                brushPanelXDp = 810f,
                toolbarDock = ToolbarDock.LEFT,
            ))

            val initialPortrait = AppSettingsStore(portraitContext).load()
            assertFalse(initialPortrait.showPageRail)
            assertFalse(initialPortrait.showBrushPanel)
            assertEquals(1180f, initialPortrait.brushPanelXDp)
            AppSettingsStore(portraitContext).save(initialPortrait.copy(
                showLayerPanel = true,
                brushPanelXDp = 32f,
                layerPanelDock = WorkspacePanelDock.BOTTOM,
                toolbarDock = ToolbarDock.BOTTOM,
            ))

            val landscape = AppSettingsStore(landscapeContext).load()
            assertFalse(landscape.showPageRail)
            assertTrue(landscape.showBrushPanel)
            assertFalse(landscape.showLayerPanel)
            assertEquals(810f, landscape.brushPanelXDp)
            assertEquals(ToolbarDock.LEFT, landscape.toolbarDock)
            assertEquals(WorkspacePanelMode.COLLAPSED, landscape.brushPanelMode)
            assertTrue(landscape.brushPanelLocked)
            assertTrue(landscape.rightDockStacked)
            assertEquals(WorkspacePanelKind.BRUSHES, landscape.rightDockActivePanel)
            assertEquals("BRUSHES,LAYERS,PAGES", landscape.panelTabOrder)

            val portrait = AppSettingsStore(portraitContext).load()
            assertFalse(portrait.showPageRail)
            assertFalse(portrait.showBrushPanel)
            assertTrue(portrait.showLayerPanel)
            assertEquals(32f, portrait.brushPanelXDp)
            assertEquals(WorkspacePanelDock.BOTTOM, portrait.layerPanelDock)
            assertEquals(ToolbarDock.BOTTOM, portrait.toolbarDock)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    private fun Context.withOrientation(value: Int): Context = createConfigurationContext(
        Configuration(resources.configuration).apply { orientation = value },
    )
}
