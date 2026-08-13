package app.inkloom

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.importer.SutBrushParser
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SutPostCorrectionParserTest {
    @Test fun enabledAndDisabledCspPostCorrectionProgramsRetainTheirExactSwitches() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "sut-post-correction-${System.nanoTime()}").apply { mkdirs() }
        try {
            val parser = SutBrushParser(File(root, "assets"))
            val enabled = parser.enrich(
                create(root, "enabled", use = true, amount = 37, speed = true, scale = false, bezier = true, sharp = true),
                "enabled",
                fallback("Enabled"),
            )
            assertTrue(enabled.postCorrectionEnabled)
            assertEquals(.37f, enabled.postCorrection, .0001f)
            assertTrue(enabled.postCorrectionAdjustBySpeed)
            assertFalse(enabled.postCorrectionAdjustByScale)
            assertTrue(enabled.postCorrectionBezier)
            assertTrue(enabled.correctionSharpAngles)
            assertTrue(enabled.sizeSyncViewScale)
            assertTrue(enabled.report.any { it.contains("Post correction 37") && it.contains("quadratic Bezier") })
            assertTrue(enabled.report.any { it.contains("Specify by size on screen enabled") })

            val disabled = parser.enrich(
                create(root, "disabled", use = false, amount = 62, speed = true, scale = true, bezier = false, sharp = false),
                "disabled",
                fallback("Disabled"),
            )
            assertFalse(disabled.postCorrectionEnabled)
            assertEquals("The disabled SUT amount must remain editable", .62f, disabled.postCorrection, .0001f)
            assertTrue(disabled.report.any { it.contains("disabled") && it.contains("stale stored amount 62") })
            assertEquals(35, disabled.parserVersion)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun create(
        root: File,
        name: String,
        use: Boolean,
        amount: Int,
        speed: Boolean,
        scale: Boolean,
        bezier: Boolean,
        sharp: Boolean,
    ): File = File(root, "$name.sut").also { source ->
        SQLiteDatabase.openOrCreateDatabase(source, null).use { database ->
            database.execSQL("CREATE TABLE Node (NodeName TEXT NOT NULL, NodeVariantID INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE Variant (VariantID INTEGER PRIMARY KEY, Opacity REAL, BrushSize REAL, BrushSizeSyncViewScale INTEGER, BrushUseRevision INTEGER, BrushRevision INTEGER, BrushRevisionBySpeed INTEGER, BrushRevisionByViewScale INTEGER, BrushRevisionBezier INTEGER, BrushSharpenCorner INTEGER)")
            database.insertOrThrow("Node", null, ContentValues().apply { put("NodeName", name); put("NodeVariantID", 1) })
            database.insertOrThrow("Variant", null, ContentValues().apply {
                put("VariantID", 1); put("Opacity", 100); put("BrushSize", 12); put("BrushSizeSyncViewScale", 1)
                put("BrushUseRevision", if (use) 1 else 0); put("BrushRevision", amount)
                put("BrushRevisionBySpeed", if (speed) 1 else 0); put("BrushRevisionByViewScale", if (scale) 1 else 0)
                put("BrushRevisionBezier", if (bezier) 1 else 0); put("BrushSharpenCorner", if (sharp) 1 else 0)
            })
        }
    }

    private fun fallback(name: String) = ImportedBrush(
        name = name,
        format = BrushFormat.SUT,
        compatibility = Compatibility.EXTRACTED,
        sizeBytes = 1,
        report = emptyList(),
    )
}
