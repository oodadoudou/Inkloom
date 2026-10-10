package app.inkloom

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.LayerAdjustment
import app.inkloom.document.LayerAdjustmentKind
import app.inkloom.document.LayerRole
import app.inkloom.document.PageBackground
import app.inkloom.document.PageTemplate
import app.inkloom.settings.UiLanguage
import app.inkloom.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/**
 * Every visible dialog control must stay reachable on a phone-sized window with the largest
 * common system font scale and Simplified Chinese: text, inputs and both dialog buttons.
 */
class DialogLayoutAuditTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val compact = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            fun shell(command: String) = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand(command).close()
            val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            context.getSharedPreferences("inkloom_editor_panels", android.content.Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE).edit().clear()
                .putString("language", "SIMPLIFIED_CHINESE").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false).putBoolean("showPageRail", false).commit()
            resetNotebookTestLibrary(context)
            shell("wm size 720x1280"); shell("wm density 320"); shell("settings put system font_scale 2.0")
            SystemClock.sleep(1500)
            try { base.evaluate() } finally {
                shell("settings put system font_scale 1.0"); shell("wm density reset"); shell("wm size reset")
                SystemClock.sleep(1500)
            }
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compact).around(compose)

    private val page = InkPage(width = 1600f, height = 2200f, background = PageBackground.Paper(0xFFFFFFFF))

    @Test fun commonDialogsKeepEveryControlReachableAtLargeFontInChinese() {
        assertTrue("phone-sized test window", compose.activity.resources.configuration.screenWidthDp <= 400)
        assertEquals(2f, compose.activity.resources.configuration.fontScale, .01f)
        audit("页面信息", "保存", "取消") { PageMetadataDialog(page, UiLanguage.SIMPLIFIED_CHINESE, { _, _ -> }, {}) }
        audit("选择颜色", "应用", "取消") { StandardColorPickerDialog(0xFF336699.toInt(), UiLanguage.SIMPLIFIED_CHINESE, {}, {}) }
        audit(null, "应用描边", "取消描边") { SelectionStrokeDialog(0xFF000000.toInt(), 1f, UiLanguage.SIMPLIFIED_CHINESE, { _, _, _, _ -> }, {}) }
        audit(null, "保存模板", "取消") {
            CustomPageTemplateDialog(PageTemplate(name = "A4", background = PageBackground.Paper(0xFFFFFFFF)),
                UiLanguage.SIMPLIFIED_CHINESE, {}, { _, _, _, _ -> null }, {})
        }
        audit(null, "应用", "取消") {
            AdjustmentLayerDialog(InkLayer("adjust", "Adjust", role = LayerRole.ADJUSTMENT,
                adjustment = LayerAdjustment(LayerAdjustmentKind.COLOR_BALANCE)), {}, {}, {})
        }
        audit("图像滤镜", "应用", "取消") {
            RasterFilterDialog(0xFF000000.toInt(), emptyList(), {}, null, false, { _, _ -> }, { _, _ -> }, {})
        }
        audit(null, null, "取消") { ColorManagementDialog(page, UiLanguage.SIMPLIFIED_CHINESE, false, {}, {}) }
        audit("导出页面", "导出", "取消") {
            PageRasterExportDialog(page.copy(layers = listOf(InkLayer("sketch", "Sketch", draft = true))), UiLanguage.SIMPLIFIED_CHINESE, {}, {})
        }
        compose.onNodeWithText("包含草稿图层").assertIsDisplayed()
        audit(null, null, "取消") {
            PageTemplatePickerDialog(app.inkloom.document.PageTemplateLibrary(), "doc", "添加页面", page.background,
                {}, { _, _ -> }, {}, {}, { _, _ -> }, {}, { _, _, _, _ -> null }, onSaveCurrent = {})
        }
    }

    @Test fun keyboardOpenOverATextFieldKeepsTheDialogButtonsReachable() {
        audit("页面信息", "保存", "取消", focus = "标签") { PageMetadataDialog(page, UiLanguage.SIMPLIFIED_CHINESE, { _, _ -> }, {}) }
        audit("选择颜色", "应用", "取消", focus = "十六进制颜色") {
            StandardColorPickerDialog(0xFF336699.toInt(), UiLanguage.SIMPLIFIED_CHINESE, {}, {})
        }
    }


    @Test fun editorNotebookDialogsKeepEveryControlReachableAtLargeFontInChinese() {
        // The library title is readable instead of being squeezed beside its actions.
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("你的笔记本").assertIsDisplayed()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        // Ellipsis is the truncation signal; CJK fallback glyphs at 2x can report vertical overflow.
        assertTrue("library title is not ellipsized", layouts.isNotEmpty() && !layouts.first().isLineEllipsized(layouts.first().lineCount - 1))
        compose.onNode(androidx.compose.ui.test.hasText("新建笔记") or androidx.compose.ui.test.hasContentDescription("新建笔记")).performClick()
        open("创建")
        compose.waitForIdle()
        // The vertical tool rail scrolls, so its last tool stays reachable in a short window.
        compose.onNode(androidx.compose.ui.test.hasContentDescription("激光笔")).performScrollTo().assertIsDisplayed()
        fun actions() = compose.onNode(androidx.compose.ui.test.hasContentDescription("操作")).performClick()
        actions(); check("操作", null, "完成")
        open("搜索当前笔记"); check("搜索此笔记", null, "完成")
        compose.onNodeWithText("完成").performClick(); compose.waitForIdle()
        actions(); open("书签"); check("书签", null, "完成")
        compose.onNodeWithText("完成").performClick(); compose.waitForIdle()
        actions(); open("页面大纲"); check("页面大纲", null, "完成")
    }

    @Test fun editorCanvasViewAndShareDialogsKeepEveryControlReachableAtLargeFontInChinese() {
        compose.onNode(androidx.compose.ui.test.hasText("新建笔记") or androidx.compose.ui.test.hasContentDescription("新建笔记")).performClick()
        open("创建")
        fun section(name: String) {
            compose.onNode(androidx.compose.ui.test.hasContentDescription("操作")).performClick()
            compose.onNode(androidx.compose.ui.test.hasContentDescription("Actions $name")).performClick()
            compose.waitForIdle()
        }
        fun close(label: String) {
            compose.onAllNodes(hasAnyAncestor(isDialog()) and androidx.compose.ui.test.hasText(label)).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
            compose.waitForIdle()
        }
        section("Canvas"); open("色彩管理"); check("色彩管理", null, "取消"); close("取消")
        section("Canvas"); open("画布与分辨率"); check("画布与分辨率", "应用", "取消"); close("取消")
        section("Canvas"); open("图像滤镜…"); check("图像滤镜", "应用", "取消"); close("取消")
        section("Views"); open("视图…"); check("视图", null, "完成"); close("完成")
        section("Views"); open("保存面板布局…"); check("保存面板布局", "保存", "取消"); close("取消")
        // The layout manager is offered once a named layout exists.
        section("Views"); open("保存面板布局…")
        compose.onNode(hasAnyAncestor(isDialog()) and androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("审计布局")
        close("保存")
        section("Views"); open("管理面板布局…"); check("面板布局", null, "完成"); close("完成")
        section("Share"); open("导出当前页"); check("导出页面", "导出", "取消"); close("取消")
        // Dialogs without their own scroll container that the static scan flagged.
        compose.onNode(androidx.compose.ui.test.hasContentDescription("形状")).performScrollTo().performClick()
        check("绘制形状", "绘制", "取消"); close("取消")
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("选择"))[0].performClick(); compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("选区操作")).performClick(); compose.waitForIdle()
        open("色域选择…"); check("色域选择", "选择", "取消"); close("取消")
    }

    @Test fun brushMoveAndBatchDialogsRemainReachableWithKeyboard() {
        val brush = app.inkloom.model.SystemBrushCatalog.brushes.first()
        audit("移动笔刷", "移动", "取消", focus = "分组") {
            BrushMoveToGroupDialog(brush, listOf("线稿"), {}, {})
        }
        audit("移动所选笔刷", "移动所选", "取消", focus = "目标分组") {
            BrushBatchGroupDialog(3, listOf("线稿"), BrushBatchGroupOperation.MOVE, {}, {})
        }
        audit("复制所选笔刷", "复制所选", "取消", focus = "目标分组") {
            BrushBatchGroupDialog(3, listOf("线稿"), BrushBatchGroupOperation.COPY, {}, {})
        }
    }

    @Test fun brushGroupManagerRemainsReachableWithKeyboard() {
        audit("管理分组", "重命名", "完成", focus = "重命名所选分组") {
            BrushGroupManagerDialog(listOf("线稿", "上色"), {}, { _, _ -> }, { _, _ -> }, {})
        }
    }

    @Test fun brushFailureAndHistoryListsKeepActionsReachable() {
        val brush = app.inkloom.model.SystemBrushCatalog.brushes.first()
        audit("笔刷导入问题", "完成", "全部清除") {
            BrushImportFailuresDialog(listOf(app.inkloom.importer.BrushImportFailure(
                "failure", "content://test/brush", "测试笔刷", "线稿", listOf("无法读取笔刷文件，请重新选择文件后重试。"),
                emptyList(), 0, 0, 1)), UiLanguage.SIMPLIFIED_CHINESE, {}, {}, {}, {})
        }
        audit("版本历史", "完成", "完成") {
            BrushVersionHistoryDialog(brush, listOf(app.inkloom.importer.BrushVersion("version", 0, brush)), {}, {}, {})
        }
    }

    @Test fun layerPanelDialogsRemainReachableAtLargeFont() {
        compose.onNode(androidx.compose.ui.test.hasText("新建笔记") or androidx.compose.ui.test.hasContentDescription("新建笔记")).performClick()
        open("创建")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("图层")).performScrollTo().assertIsDisplayed().performClick()
        fun menu() {
            val nodes = compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("图层操作"))
            if (nodes.fetchSemanticsNodes().isEmpty()) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/layer-audit.png").close()
                throw AssertionError(compose.onRoot(useUnmergedTree = true).printToString())
            }
            nodes[0].performClick()
        }
        menu(); open("重命名"); check("重命名图层", "保存", "取消", focus = "图层名称")
        open("取消")
        menu(); open("图层着色"); check("图层着色", "应用", "取消")
        open("取消")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("输入图层不透明度")).performClick()
        check("图层不透明度", "确定", "取消", focus = "不透明度（%）")
        open("取消")
        menu(); open("蒙版"); open("蒙版选区")
        compose.waitForIdle()
        menu(); open("蒙版"); open("蒙版渐变与阈值")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("显示蒙版渐变开关")).performClick()
        check("蒙版表达", "应用", "取消")
        open("取消")
    }

    @Test fun pagePanelDialogsRemainReachableAtLargeFont() {
        compose.onNode(androidx.compose.ui.test.hasText("新建笔记") or androidx.compose.ui.test.hasContentDescription("新建笔记")).performClick()
        open("创建")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("显示或隐藏页面栏")).performScrollTo().assertIsDisplayed().performClick()
        fun menu() {
            try { compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("页面菜单")).fetchSemanticsNodes().isNotEmpty() } }
            catch (failure: Throwable) { throw AssertionError(compose.onRoot(useUnmergedTree = true).printToString(), failure) }
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("页面菜单"))[0].performScrollTo().assertIsDisplayed().performClick()
        }
        menu(); open("页码与标签"); check("页面信息", "保存", "取消", focus = "标签")
        open("取消")
        menu(); open("裁剪页面"); check("裁剪页面", "应用", "取消")
        open("取消")
        menu(); open("页面模板"); check(null, null, "取消")
        open("取消")
    }

    @Test fun pageNavigatorCounterAndPopupTitleDoNotClipAtLargeFont() {
        compose.onNode(androidx.compose.ui.test.hasText("新建笔记") or androidx.compose.ui.test.hasContentDescription("新建笔记")).performClick()
        open("创建")
        val counter = compose.onNode(androidx.compose.ui.test.hasContentDescription("长按跳转页面"))
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        counter.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Page count fits its navigator", layouts.isNotEmpty() && !layouts.single().didOverflowHeight)
        counter.performClick()
        compose.onNodeWithText("跳转页面").assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("跳转到第 1")).assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("关闭页面跳转")).performClick()
    }

    @Test fun brushStudioTitleAndActionsFitAtLargeFont() {
        val preset = app.inkloom.brushpresetv5.BrushPresetV5Import.requirePreset(app.inkloom.model.SystemBrushCatalog.brushes.first())
        val owned = app.inkloom.brushpresetv5.OwnedBrushPreset(preset,
            app.inkloom.brushpresetv5.PresetOwnership.Factory(preset.identity.id))
        compose.runOnUiThread { compose.activity.setContent { MaterialTheme {
            BrushEditorV1Dialog(owned, app.inkloom.settings.AppSettings(language = UiLanguage.SIMPLIFIED_CHINESE), {}, {}, {}, null, {})
        } } }
        compose.waitForIdle()
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("笔刷工作室").assertIsDisplayed().performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue("Brush Studio title fits one line: " + results.map { "${it.size} lines=${it.lineCount} widthOverflow=${it.didOverflowWidth}" },
            results.isNotEmpty() && results.single().lineCount == 1 && results.single().getLineRight(0) <= results.single().size.width + 1f)
        listOf("操作", "取消", "完成").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        open("操作"); open("另存为")
        check("笔刷另存为", "保存", "取消", focus = "笔刷名称")
    }

    private fun imageFixture(): java.io.File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.cacheDir, "window-audit.png")
        val bitmap = android.graphics.Bitmap.createBitmap(128, 160, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        android.graphics.Canvas(bitmap).drawCircle(64f, 80f, 40f, android.graphics.Paint().apply { color = android.graphics.Color.BLACK })
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    @Test fun brushSourceTipSequenceRemainsReachable() {
        val file = imageFixture()
        val preset = app.inkloom.brushpresetv5.BrushPresetV5Import.requirePreset(app.inkloom.model.SystemBrushCatalog.brushes.first())
            .copy(tipTexture = app.inkloom.brushpresetv5.TipTextureSettings(assetPaths = List(12) { file.path }))
        val workspace = BrushSourceWorkspace(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            audit("来源编辑", "应用来源", "取消") {
                BrushSourceEditor(preset, BrushSourceRole.SHAPE, workspace, UiLanguage.SIMPLIFIED_CHINESE, {}, {})
            }
            open("12")
            compose.onNodeWithText("● 12").assertIsDisplayed()
        } finally { workspace.close(); file.delete() }
    }

    @Test fun settingsTabsAndShortcutSearchRemainReachableWithKeyboard() {
        compose.onNode(androidx.compose.ui.test.hasContentDescription("设置")).performClick()
        check("设置", "完成", "完成")
        for ((tab, last) in listOf("通用" to "重置工具栏位置", "手势" to "显示 V5 落笔诊断", "压感" to "重置压感设置", "备份" to "恢复资料库备份")) {
            compose.onNode(hasAnyAncestor(isDialog()) and SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
                .performScrollToNode(androidx.compose.ui.test.hasText(tab))
            open(tab)
            compose.onNode(androidx.compose.ui.test.hasContentDescription("Settings content"))
                .performScrollToNode(androidx.compose.ui.test.hasText(last))
            compose.onNodeWithText(last).assertIsDisplayed()
            compose.onNodeWithText("完成").assertIsDisplayed()
        }
        compose.onNode(hasAnyAncestor(isDialog()) and SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .performScrollToNode(androidx.compose.ui.test.hasText("快捷键"))
        open("快捷键")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Settings content"))
            .performScrollToNode(androidx.compose.ui.test.hasText("自定义快捷键"))
        open("自定义快捷键")
        check("Toolbox 快捷键", "完成", "完成", focus = "搜索操作或按键")
    }

    private fun open(text: String) {
        val node = compose.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.assertIsDisplayed().performClick()
        compose.waitForIdle()
    }

    private fun audit(title: String?, confirm: String?, dismiss: String, focus: String? = null, dialog: @Composable () -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent { MaterialTheme {
                CompositionLocalProvider(LocalUiLanguage provides UiLanguage.SIMPLIFIED_CHINESE) { dialog() }
            } }
        }
        compose.waitForIdle()
        check(title, confirm, dismiss, focus)
    }

    private fun check(title: String?, confirm: String?, dismiss: String, focus: String? = null) {
        compose.waitForIdle()
        val dialogId = compose.onAllNodes(isDialog()).fetchSemanticsNodes().last().id
        val inDialog = hasAnyAncestor(SemanticsMatcher("front dialog") { it.id == dialogId })
        title?.let {
            try { compose.onNode(inDialog and androidx.compose.ui.test.hasText(it)).assertIsDisplayed() } catch (failure: AssertionError) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/dialog-audit.png").close()
                SystemClock.sleep(500); throw failure
            }
        }
        focus?.let { label ->
            compose.onAllNodes(inDialog and (androidx.compose.ui.test.hasText(label, substring = true) or androidx.compose.ui.test.hasContentDescription(label)))[0].performClick()
            SystemClock.sleep(800); compose.waitForIdle()
        }
        val texts = compose.onAllNodes(inDialog and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        val labels = texts.fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.Text].map { text -> text.text } }.distinct()
        assertTrue("$title has content", labels.isNotEmpty())
        for (label in labels) {
            val node = compose.onAllNodes(inDialog and androidx.compose.ui.test.hasText(label))[0]
            // A chip in a horizontally scrolling row first needs its row revealed in the
            // outer vertical scroll area; scrolling only the chip cannot move that parent.
            generateSequence(node.fetchSemanticsNode().parent) { it.parent }
                .filter { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                .toList().asReversed().forEach { row ->
                    runCatching { compose.onNode(SemanticsMatcher("scroll row ${row.id}") { it.id == row.id },
                        useUnmergedTree = true).performScrollTo() }
                }
            generateSequence(node.fetchSemanticsNode().parent) { it.parent }
                .filter { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) &&
                    it.config.contains(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex) }
                .forEach { row ->
                    compose.onNode(SemanticsMatcher("lazy row ${row.id}") { it.id == row.id }, useUnmergedTree = true)
                        .performScrollToNode(androidx.compose.ui.test.hasText(label))
                }
            runCatching { node.performScrollTo() }
            try { node.assertIsDisplayed() } catch (failure: AssertionError) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/dialog-audit.png").close()
                SystemClock.sleep(500)
                throw AssertionError("[$title] '$label' is clipped or unreachable", failure)
            }
        }
        listOfNotNull(confirm, dismiss).forEach { button ->
            compose.onAllNodes(inDialog and androidx.compose.ui.test.hasText(button))
                .fetchSemanticsNodes().let { assertTrue("[$title] button $button exists", it.isNotEmpty()) }
            compose.onAllNodes(inDialog and androidx.compose.ui.test.hasText(button)).let { buttons ->
                buttons[buttons.fetchSemanticsNodes().size - 1].assertIsDisplayed()
            }
        }
    }
}
