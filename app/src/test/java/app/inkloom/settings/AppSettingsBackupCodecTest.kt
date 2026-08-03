package app.inkloom.settings

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsBackupCodecTest {
    @Test fun `round trip preserves input tools workspace shortcuts quick slots and palette`() {
        val settings = AppSettings(
            language = UiLanguage.SIMPLIFIED_CHINESE,
            fingerDrawing = true,
            showPageRail = false,
            showBrushPanel = true,
            showLayerPanel = true,
            brushPanelDock = WorkspacePanelDock.FREE,
            layerPanelDock = WorkspacePanelDock.BOTTOM,
            brushPanelMode = WorkspacePanelMode.COLLAPSED,
            layerPanelMode = WorkspacePanelMode.ICON_ONLY,
            brushPanelLocked = true,
            bottomDockStacked = true,
            bottomDockActivePanel = WorkspacePanelKind.LAYERS,
            freeDockStacked = true,
            freeDockActivePanel = WorkspacePanelKind.BRUSHES,
            panelTabOrder = "LAYERS,BRUSHES,PAGES",
            brushPanelXDp = 333f,
            toolbarDock = ToolbarDock.LEFT,
            singleFingerDoubleTapUndo = true,
            twoFingerRotation = true,
            rulerEnabled = true,
            rulerCenterX = .37f,
            rulerCenterY = .62f,
            rulerAngleDegrees = 45f,
            rulerSnapDegrees = 5f,
            stylusPrimaryButton = StylusButtonAction.UNDO,
            stylusSecondaryButton = StylusButtonAction.HAND,
            stylusPrimaryTrigger = ShortcutTrigger.PRESS,
            stylusSecondaryTrigger = ShortcutTrigger.HOLD,
            pressureSensitivity = 1.7f,
            pressureMinimumInput = .1f,
            pressureMaximumInput = .8f,
            penSize = 17f,
            eraserPreset = EraserPreset.SOFT_EDGE,
            selectionMode = SelectionMode.INTERSECT,
            shortcutBindings = listOf(ShortcutBinding(ShortcutAction.QUICK_SLOT_1, ShortcutChord(KeyEvent.KEYCODE_F12))),
            shortcutAxisBindings = listOf(AxisShortcutBinding(ToolboxAxis.ROTARY, ToolboxAxisMode.BRUSH_SIZE, sensitivity = 2f, deviceDescriptor = "dial")),
            shortcutButtonBindings = listOf(ButtonShortcutBinding(android.view.MotionEvent.BUTTON_BACK, ShortcutAction.HAND_TOOL, ShortcutTrigger.HOLD, deviceDescriptor = "dial")),
            shortcutBrushGroupTargets = ShortcutBrushGroupTargets("Pens", "Pencils", "Brushes"),
            toolboxQuickSlots = ToolboxQuickSlots.normalize(listOf(ToolboxQuickSlot(ToolboxQuickSlotKind.TOOL_PRESET, "Red pen", size = 12f, colorArgb = 0xFFAA0000.toInt()))),
        ).recordWorkspaceFor(WorkspaceOrientation.LANDSCAPE).copy(
            showPageRail = true,
            toolbarDock = ToolbarDock.BOTTOM,
        ).recordWorkspaceFor(WorkspaceOrientation.PORTRAIT)
        val customWorkspace = CustomWorkspaces.capture("workspace-1", "Desk", settings, WorkspaceOrientation.PORTRAIT)
        val settingsWithWorkspace = settings.copy(customWorkspaces = listOf(customWorkspace))
        val palette = listOf(0xFF112233.toInt(), 0xFF445566.toInt())

        val restored = AppSettingsBackupCodec.decode(AppSettingsBackupCodec.encode(settingsWithWorkspace, palette)).getOrThrow()

        assertEquals(settingsWithWorkspace, restored.settings)
        assertEquals(palette, restored.colorPalette)
    }

    @Test fun `decode rejects malformed oversized and unknown settings payloads`() {
        assertTrue(AppSettingsBackupCodec.decode("not json".toByteArray()).isFailure)
        assertTrue(AppSettingsBackupCodec.decode(ByteArray(AppSettingsBackupCodec.MAX_BYTES + 1)).isFailure)
        assertTrue(AppSettingsBackupCodec.decode("""{"format":"inkloom-app-settings","version":99,"settings":{}}""".toByteArray()).isFailure)
    }

    @Test fun `decode clamps hostile numeric settings`() {
        val original = AppSettingsBackupCodec.encode(AppSettings(), emptyList()).toString(Charsets.UTF_8)
        val hostile = original.replace("\"penSize\": 5.0", "\"penSize\": 99999.0").replace("\"pressureSensitivity\": 1.0", "\"pressureSensitivity\": -9.0")

        val restored = AppSettingsBackupCodec.decode(hostile.toByteArray()).getOrThrow().settings

        assertEquals(400f, restored.penSize)
        assertEquals(.25f, restored.pressureSensitivity)
    }
}
