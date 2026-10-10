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
        compose.onNode(androidx.compose.ui.test.hasText("新建笔记") or androidx.compose.ui.test.hasContentDescription("新建笔记")).performClick()
        open("创建")
        compose.waitForIdle()
        fun actions() = compose.onNode(androidx.compose.ui.test.hasContentDescription("操作")).performClick()
        actions(); check("操作", null, "完成")
        open("搜索当前笔记"); check("搜索此笔记", null, "完成")
        compose.onNodeWithText("完成").performClick(); compose.waitForIdle()
        actions(); open("书签"); check("书签", null, "完成")
        compose.onNodeWithText("完成").performClick(); compose.waitForIdle()
        actions(); open("页面大纲"); check("页面大纲", null, "完成")
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
        title?.let {
            try { compose.onNodeWithText(it).assertIsDisplayed() } catch (failure: AssertionError) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/dialog-audit.png").close()
                SystemClock.sleep(500); throw failure
            }
        }
        focus?.let { label ->
            compose.onAllNodes(androidx.compose.ui.test.hasText(label, substring = true) or androidx.compose.ui.test.hasContentDescription(label))[0].performClick()
            SystemClock.sleep(800); compose.waitForIdle()
        }
        val texts = compose.onAllNodes(hasAnyAncestor(isDialog()) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        val count = texts.fetchSemanticsNodes().size
        assertTrue("$title has content", count > 0)
        for (index in 0 until count) {
            val node = texts[index]
            val label = node.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
            runCatching { node.performScrollTo() }
            try { node.assertIsDisplayed() } catch (failure: AssertionError) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/dialog-audit.png").close()
                SystemClock.sleep(500)
                throw AssertionError("[$title] '$label' is clipped or unreachable", failure)
            }
        }
        listOfNotNull(confirm, dismiss).forEach { button ->
            compose.onAllNodes(hasAnyAncestor(isDialog()) and androidx.compose.ui.test.hasText(button))
                .fetchSemanticsNodes().let { assertTrue("[$title] button $button exists", it.isNotEmpty()) }
            compose.onAllNodes(hasAnyAncestor(isDialog()) and androidx.compose.ui.test.hasText(button)).let { buttons ->
                buttons[buttons.fetchSemanticsNodes().size - 1].assertIsDisplayed()
            }
        }
    }
}
