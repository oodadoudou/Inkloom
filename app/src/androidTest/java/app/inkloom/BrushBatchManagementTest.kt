package app.inkloom

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.importer.BrushGroupOperations
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class BrushBatchManagementTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val sourceBrushes = listOf(
        brush("batch-a", "Batch A", "Source", 0),
        brush("batch-b", "Batch B", "Source", 1),
        brush("target-c", "Target C", "Target", 0),
    )

    private val preloadRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit()
                    .putString("language", "ENGLISH")
                    .putBoolean("showBrushPanel", false)
                    .putBoolean("showLayerPanel", false)
                    .putBoolean("showPageRail", false)
                    .putString("brushPanelDock", "RIGHT")
                    .commit()
                val store = BrushMetadataStore(context)
                store.save(sourceBrushes)
                try {
                    base.evaluate()
                } finally {
                    store.save(emptyList())
                }
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(preloadRule).around(composeRule)

    @Test fun longPressMovesAndCopiesPresetsAcrossGroups() {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithContentDescription("Brushes").performClick()

        composeRule.onNodeWithContentDescription("Brush preset Batch A").performTouchInput { longClick() }
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNodeWithText("Move").performClick()
        composeRule.onAllNodes(hasText("Target")).onLast().performClick()
        composeRule.onNodeWithText("Move selected").performClick()

        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) { store.load().firstOrNull { it.id == "batch-a" }?.groupName == "Target" }
        assertEquals("Target", store.load().single { it.id == "batch-a" }.groupName)

        composeRule.onNodeWithContentDescription("Brush preset Batch A").performTouchInput { longClick() }
        composeRule.onNodeWithText("Copy").performClick()
        composeRule.onAllNodes(hasText("Source")).onLast().performClick()
        composeRule.onNodeWithText("Copy selected").performClick()

        composeRule.waitUntil(5_000) { store.load().size == 4 }
        val copied = store.load().single { it.name == "Batch A copy" }
        assertNotEquals("batch-a", copied.id)
        assertEquals("Source", copied.groupName)
    }

    @Test fun groupManagerPersistsUserDefinedGroupOrder() {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Manage brush groups").performClick()

        composeRule.onAllNodes(hasText("Target")).onLast().performClick()
        composeRule.onNodeWithText("Earlier").performClick()
        composeRule.onNodeWithText("Done").performClick()

        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) { BrushGroupOperations.names(store.load()).firstOrNull() == "Target" }
        assertEquals(listOf("Target", "Source"), BrushGroupOperations.names(store.load()))
    }

    private companion object {
        fun brush(id: String, name: String, group: String, order: Int) = ImportedBrush(
            id = id,
            name = name,
            format = BrushFormat.IMAGE,
            compatibility = Compatibility.COMPLETE,
            sizeBytes = 1L,
            report = emptyList(),
            groupName = group,
            sortOrder = order,
        )
    }
}
