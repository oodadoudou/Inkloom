package app.inkloom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkloomLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun libraryScreenLaunches() {
        composeRule.onNodeWithText("Inkloom").assertIsDisplayed()
    }

    @Test fun settingsExposeInputAndToolControls() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Draw with finger").assertIsDisplayed()
    }

    @Test fun stickyNoteDoubleTapOpensItsInkSubcanvas() {
        val noteText = "Sticky canvas test"
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithContentDescription("Note").performClick()
        composeRule.onNodeWithText("New sticky note").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput(noteText)
        composeRule.onNodeWithText("Insert").performClick()

        composeRule.onNodeWithText(noteText).performTouchInput { doubleClick() }

        composeRule.onNodeWithText("Write inside sticky note").assertIsDisplayed()
        composeRule.onNodeWithText("System pen").assertIsDisplayed()
    }

    @Test fun editorExposesUnifiedPageAndPdfSearch() {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithContentDescription("Search this note").performClick()

        composeRule.onNodeWithText("Page, note, or PDF text").assertIsDisplayed()
    }

    @Test fun editorInsertsAValidatedLinkCard() {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithContentDescription("Link").performScrollTo().performClick()
        composeRule.onNodeWithText("Insert link").assertIsDisplayed()

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Inkloom docs")
        composeRule.onAllNodes(hasSetTextAction()).onLast().performTextInput("example.com/inkloom")
        composeRule.onNodeWithText("Insert").performClick()

        composeRule.onNodeWithText("Inkloom docs").assertIsDisplayed()
        composeRule.onNodeWithText("example.com/inkloom").assertIsDisplayed()
    }
}
