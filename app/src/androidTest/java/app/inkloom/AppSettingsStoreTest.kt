package app.inkloom

import android.content.Context
import android.content.res.Configuration
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.AxisShortcutBinding
import app.inkloom.settings.ButtonShortcutBinding
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutBrushGroupTargets
import app.inkloom.settings.ShortcutTrigger
import app.inkloom.settings.StylusButtonAction
import app.inkloom.settings.ToolboxQuickSlot
import app.inkloom.settings.ToolboxQuickSlotKind
import app.inkloom.settings.ToolbarDock
import app.inkloom.settings.ToolboxAxis
import app.inkloom.settings.ToolboxAxisMode
import app.inkloom.settings.ReferenceTarget
import app.inkloom.settings.LibraryLayout
import app.inkloom.settings.FillTargetMode
import app.inkloom.settings.FillExpansionShape
import app.inkloom.settings.GradientPresetLibrary
import app.inkloom.document.GradientDefinition
import app.inkloom.document.GradientColorStop
import app.inkloom.document.CorrectLineMode
import app.inkloom.model.EditorTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsStoreTest {
    @Test fun liquifyForceDistortionAndMomentumSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(liquifyStrengthScale = 1.4f,
                liquifyDistortion = .45f, liquifyMomentum = .7f))
            val restored = AppSettingsStore(context).load()
            assertEquals(1.4f, restored.liquifyStrengthScale)
            assertEquals(.45f, restored.liquifyDistortion)
            assertEquals(.7f, restored.liquifyMomentum)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun namedGradientPresetsSurviveReloadWithTransparentStops() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
        preferences.edit().clear().commit()
        try {
            val library = GradientPresetLibrary().add("Transparent ink",
                GradientDefinition(stops = listOf(GradientColorStop(0f, 0x00000000),
                    GradientColorStop(1f, 0xFF112233L))), "ink")
            AppSettingsStore(context).save(AppSettings(gradientPresets = library))
            assertEquals(library, AppSettingsStore(context).load().gradientPresets)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun fillTargetAndItsIndependentColorSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(
                fillTargetMode = FillTargetMode.SPECIFIED_COLOR,
                fillTargetColor = 0xFFCA7733.toInt(),
                fillExpansionShape = FillExpansionShape.RECTANGLE,
                fillAntiAlias = false,
            ))
            val restored = AppSettingsStore(context).load()
            assertEquals(FillTargetMode.SPECIFIED_COLOR, restored.fillTargetMode)
            assertEquals(0xFFCA7733.toInt(), restored.fillTargetColor)
            assertEquals(FillExpansionShape.RECTANGLE, restored.fillExpansionShape)
            assertFalse(restored.fillAntiAlias)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun eyedropperSourceAndPaperSettingSurviveReloadWithoutChangingFillSource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(
                referenceTarget = ReferenceTarget.REFERENCE_LAYERS,
                eyedropperReferenceTarget = ReferenceTarget.ACTIVE_LAYER,
                eyedropperIncludePaper = false,
            ))
            val restored = AppSettingsStore(context).load()
            assertEquals(ReferenceTarget.REFERENCE_LAYERS, restored.referenceTarget)
            assertEquals(ReferenceTarget.ACTIVE_LAYER, restored.eyedropperReferenceTarget)
            assertFalse(restored.eyedropperIncludePaper)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun persistentDrawingSelectionSurvivesReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(
                AppSettings(
                    persistentDrawingTool = EditorTool.PEN,
                    persistentBrushPresetId = "system-hb-pencil",
                ),
            )
            val restored = AppSettingsStore(context).load()
            assertEquals(EditorTool.PEN, restored.persistentDrawingTool)
            assertEquals("system-hb-pencil", restored.persistentBrushPresetId)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun libraryLayoutSurvivesReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(libraryLayout = LibraryLayout.COMPACT_GRID))
            assertEquals(LibraryLayout.COMPACT_GRID, AppSettingsStore(context).load().libraryLayout)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun legacyGlobalCorrectionKeysAreDeletedByTheV5HardCut() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
        preferences.edit().clear()
            .putInt("studioWorkspaceVersion", 2)
            .putInt("inputGesturesVersion", 2)
            .putFloat("stabilization", .64f)
            .putFloat("postCorrection", .72f)
            .commit()
        try {
            val store = AppSettingsStore(context)
            store.load()
            assertFalse(preferences.contains("stabilization"))
            assertFalse(preferences.contains("postCorrection"))
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun correctLineSubtoolSurvivesReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(correctLineMode = CorrectLineMode.SIMPLIFY, correctLineAmount = .78f))

            val restored = AppSettingsStore(context).load()
            assertEquals(CorrectLineMode.SIMPLIFY, restored.correctLineMode)
            assertEquals(.78f, restored.correctLineAmount)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun fillAndAutoSelectReferenceTargetSurvivesReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(referenceTarget = ReferenceTarget.REFERENCE_LAYERS))
            assertEquals(ReferenceTarget.REFERENCE_LAYERS, AppSettingsStore(context).load().referenceTarget)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun rulerGuideSurvivesReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
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

    @Test fun symmetryGuideSurvivesReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(symmetryMode = app.inkloom.model.SymmetryMode.RADIAL_6, symmetryCenterX = .27f, symmetryCenterY = .68f, symmetryAngleDegrees = -32f))
            val restored = AppSettingsStore(context).load()
            assertEquals(app.inkloom.model.SymmetryMode.RADIAL_6, restored.symmetryMode)
            assertEquals(.27f, restored.symmetryCenterX)
            assertEquals(.68f, restored.symmetryCenterY)
            assertEquals(-32f, restored.symmetryAngleDegrees)
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
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
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

    @Test fun selectionStudioPositionSurvivesReload() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val preferences=context.getSharedPreferences("inkloom_settings",Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            AppSettingsStore(context).save(AppSettings(selectionStudioXDp=214f,selectionStudioYDp=468f))
            val restored=AppSettingsStore(context).load()
            assertEquals(214f,restored.selectionStudioXDp)
            assertEquals(468f,restored.selectionStudioYDp)
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun toolboxBrushGroupTargetsSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
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
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
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
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
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
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
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

    @Test fun pressureResponseCurveSurvivesReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
        context.getSharedPreferences("inkloom_editor_panels",0).edit().clear().commit()
        preferences.edit().clear().commit()
        try {
            val curve = app.inkloom.document.PressureResponseCurve.FIRM
            AppSettingsStore(context).save(AppSettings(pressureResponseCurve = curve))
            assertEquals(curve, AppSettingsStore(context).load().pressureResponseCurve)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun landscapeAndPortraitTreesAndToolRailsSurviveRotationIndependently() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val settings=context.getSharedPreferences("inkloom_settings",0)
        val panels=context.getSharedPreferences("inkloom_editor_panels",0)
        settings.edit().clear().commit();panels.edit().clear().commit()
        val landscape=context.withOrientation(Configuration.ORIENTATION_LANDSCAPE)
        val portrait=context.withOrientation(Configuration.ORIENTATION_PORTRAIT)
        try {
            val landStore=app.inkloom.settings.EditorWorkspaceStore(landscape)
            val land=landStore.load().layout.copy(open=setOf(app.inkloom.settings.EditorPanel.BRUSHES),locked=true)
            landStore.updateLayout(land)
            AppSettingsStore(landscape).save(AppSettings(toolbarDock=ToolbarDock.LEFT))
            val portStore=app.inkloom.settings.EditorWorkspaceStore(portrait)
            val port=portStore.load().layout.copy(open=setOf(app.inkloom.settings.EditorPanel.LAYERS),locked=false)
            portStore.updateLayout(port)
            AppSettingsStore(portrait).save(AppSettings(toolbarDock=ToolbarDock.BOTTOM))
            assertEquals(land,landStore.load().layout);assertEquals(ToolbarDock.LEFT,AppSettingsStore(landscape).load().toolbarDock)
            assertEquals(port,portStore.load().layout);assertEquals(ToolbarDock.BOTTOM,AppSettingsStore(portrait).load().toolbarDock)
        }finally{settings.edit().clear().commit();panels.edit().clear().commit()}
    }

    private fun Context.withOrientation(value: Int): Context = createConfigurationContext(
        Configuration(resources.configuration).apply { orientation = value },
    )
}
