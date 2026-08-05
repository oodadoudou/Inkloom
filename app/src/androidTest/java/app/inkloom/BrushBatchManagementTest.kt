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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.importer.BrushGroupOperations
import app.inkloom.importer.BrushGroupStore
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
        brush("nested-d", "Nested Ink", "Source/Ink/Pens", 0),
    )

    private val preloadRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit()
                    .putString("language", "ENGLISH")
                    .putInt("studioWorkspaceVersion", 2)
                    .putBoolean("showBrushPanel", false)
                    .putBoolean("showLayerPanel", false)
                    .putBoolean("showPageRail", false)
                    .putString("brushPanelDock", "RIGHT")
                    .remove("workspaceLayouts")
                    .commit()
                val store = BrushMetadataStore(context)
                store.save(sourceBrushes)
                val groupStore = BrushGroupStore(context)
                groupStore.save(emptyList())
                try {
                    base.evaluate()
                } finally {
                    store.save(emptyList())
                    groupStore.save(emptyList())
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

        composeRule.waitUntil(5_000) { store.load().size == 5 }
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
        assertEquals(listOf("Target", "Source", "Source/Ink/Pens"), BrushGroupOperations.names(store.load()))
    }

    @Test fun selectedGroupBecomesTheBatchImportDestination() {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithContentDescription("Brushes").performClick()

        composeRule.onNodeWithText("Source").performClick()

        composeRule.onNodeWithContentDescription("Add brush or group").performClick()
        composeRule.onNodeWithText("Import into Source").assertIsDisplayed()
    }

    @Test fun nestedGroupTreeCollapsesAndFiltersAnEntireSubtree() {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithContentDescription("Brushes").performClick()

        composeRule.onNodeWithText("Ink").assertIsDisplayed()
        composeRule.onNodeWithText("Pens").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Collapse group Source").performScrollTo()
        composeRule.onNodeWithContentDescription("Collapse group Source").performClick()
        assertEquals(0, composeRule.onAllNodes(hasText("Ink")).fetchSemanticsNodes().size)
        composeRule.onNodeWithContentDescription("Expand group Source").performClick()
        composeRule.onNodeWithText("Ink").performScrollTo().performClick()

        composeRule.onNodeWithText("Nested Ink").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add brush or group").performClick()
        composeRule.onNodeWithText("Import into Source/Ink").assertIsDisplayed()
    }

    @Test fun groupManagerCreatesAndPersistsAnEmptyNestedGroup() {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Manage brush groups").performClick()

        composeRule.onNodeWithContentDescription("New brush group path").performTextInput("Empty/Erasers")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithText("Done").performClick()

        val groups = BrushGroupStore(composeRule.activity).load()
        assertEquals(listOf("Source", "Target", "Source/Ink/Pens", "Empty/Erasers"), groups)
        assertEquals(0, BrushMetadataStore(composeRule.activity).load().count { it.groupName == "Empty/Erasers" })
    }

    @Test fun createsAnEditableBrushInsideTheSelectedUserGroup() {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithText("Source").performClick()

        composeRule.onNodeWithContentDescription("Add brush or group").performClick()
        composeRule.onNodeWithText("Create blank brush").performClick()
        composeRule.onNodeWithText("Brush Studio").assertIsDisplayed()
        val nameField = composeRule.onAllNodes(hasText("New brush")).onLast()
        nameField.performTextClearance()
        composeRule.onAllNodes(hasText("Brush name")).onLast().performTextInput("My soft brush")
        composeRule.onNodeWithText("Save preset").performClick()

        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) { store.load().any { it.name == "My soft brush" } }
        assertEquals("Source", store.load().single { it.name == "My soft brush" }.groupName)
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
