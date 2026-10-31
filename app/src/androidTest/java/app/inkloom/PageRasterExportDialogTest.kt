package app.inkloom

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.PageRasterExportOptions
import app.inkloom.document.RasterFileFormat
import app.inkloom.settings.UiLanguage
import app.inkloom.ui.PageRasterExportDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PageRasterExportDialogTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun includeDraftsAppliesToInterchangeAndIsNotOfferedForTheLayeredPackage() {
        var exported: PageRasterExportOptions? = null
        val page = InkPage(width = 64f, height = 64f, layers = listOf(InkLayer("sketch", "Sketch", draft = true)))
        compose.runOnUiThread {
            compose.activity.setContent { MaterialTheme { PageRasterExportDialog(page, UiLanguage.ENGLISH, {}, { exported = it }) } }
        }
        compose.onNodeWithText("Include draft layers").performClick()
        compose.onNodeWithText("PNG").performClick(); compose.onNodeWithText("PSD").performClick()
        compose.onNodeWithText("Export").performClick()
        assertEquals(RasterFileFormat.PSD, exported?.format); assertTrue(exported!!.includeDraftLayers)
        // Switching to the layered package after checking the box must not carry the option over.
        compose.onNodeWithText("PSD").performClick(); compose.onNodeWithText("Layered PNG").performClick()
        compose.onNodeWithText("Include draft layers").assertDoesNotExist()
        compose.onNodeWithText("Export").performClick()
        assertEquals(RasterFileFormat.LAYERED_PNG, exported?.format); assertFalse(exported!!.includeDraftLayers)
    }
}
