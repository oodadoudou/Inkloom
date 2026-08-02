package app.inkloom

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.importer.SutBrushParser
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SutSprayRotationParserTest {
    @Test fun sutRandomAnglesRequireTheirFlagsAndSprayUsesIndependentDirection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "sut-spray-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(root, "synthetic.sut")
            createSyntheticSut(source)
            val fallback = ImportedBrush(name = "Fallback", format = BrushFormat.SUT, compatibility = Compatibility.EXTRACTED, sizeBytes = source.length(), report = emptyList())

            val brush = SutBrushParser(File(root, "assets")).enrich(source, "sample", fallback)

            assertEquals("Particle test", brush.name)
            assertEquals(0f, brush.randomRotationDegrees, .0001f)
            assertEquals(17f, brush.sprayParticleRotationDegrees, .0001f)
            assertFalse(brush.sprayParticleFollowStylusOrientation)
            assertTrue(brush.sprayParticleFollowStrokeDirection)
            assertEquals(72f, brush.sprayParticleRandomRotationDegrees, .0001f)
            assertEquals(0f, brush.dualRandomRotationDegrees, .0001f)
            assertEquals(-25f, brush.dualSprayParticleRotationDegrees, .0001f)
            assertTrue(brush.dualSprayParticleFollowStylusOrientation)
            assertFalse(brush.dualSprayParticleFollowStrokeDirection)
            assertEquals(90f, brush.dualSprayParticleRandomRotationDegrees, .0001f)
            assertEquals(14, brush.parserVersion)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun importedBrushStoreRoundTripsParticleDirectionSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("inkloom_brushes", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        try {
            val original = ImportedBrush(
                id = "particle-brush",
                name = "Particles",
                format = BrushFormat.SUT,
                compatibility = Compatibility.HIGH,
                sizeBytes = 42,
                report = emptyList(),
                spray = true,
                sprayParticleRotationDegrees = 33f,
                sprayParticleFollowStrokeDirection = true,
                sprayParticleRandomRotationDegrees = 54f,
                dualBrush = true,
                dualSpray = true,
                dualSprayParticleRotationDegrees = -12f,
                dualSprayParticleFollowStylusOrientation = true,
                dualSprayParticleRandomRotationDegrees = 81f,
            )
            val store = BrushMetadataStore(context)

            store.save(listOf(original))
            val restored = store.load().single()

            assertEquals(33f, restored.sprayParticleRotationDegrees, .0001f)
            assertTrue(restored.sprayParticleFollowStrokeDirection)
            assertEquals(54f, restored.sprayParticleRandomRotationDegrees, .0001f)
            assertEquals(-12f, restored.dualSprayParticleRotationDegrees, .0001f)
            assertTrue(restored.dualSprayParticleFollowStylusOrientation)
            assertEquals(81f, restored.dualSprayParticleRandomRotationDegrees, .0001f)
        } finally {
            context.getSharedPreferences("inkloom_brushes", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun createSyntheticSut(file: File) {
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database.use { db ->
            db.execSQL("CREATE TABLE Node (NodeName TEXT NOT NULL, NodeVariantID INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE Variant (VariantID INTEGER PRIMARY KEY, Opacity REAL, BrushSize REAL, BrushUseSpray INTEGER, BrushRotation REAL, BrushRotationEffector INTEGER, BrushRotationRandomScale REAL, BrushRotationInSpray REAL, BrushRotationEffectorInSpray INTEGER, BrushRotationRandomInSpray REAL, UseDualBrush INTEGER, DualRotation REAL, DualRotationEffector INTEGER, DualRotationRandomScale REAL, DualUseSpray INTEGER, DualRotationInSpray REAL, DualRotationEffectorInSpray INTEGER, DualRotationRandomInSpray REAL)")
            db.insertOrThrow("Node", null, ContentValues().apply { put("NodeName", "Particle test"); put("NodeVariantID", 1) })
            db.insertOrThrow("Variant", null, ContentValues().apply {
                put("VariantID", 1)
                put("Opacity", 100)
                put("BrushSize", 20)
                put("BrushUseSpray", 1)
                put("BrushRotation", 5)
                put("BrushRotationEffector", 3)
                put("BrushRotationRandomScale", 100)
                put("BrushRotationInSpray", 17)
                put("BrushRotationEffectorInSpray", 0x83)
                put("BrushRotationRandomInSpray", 40)
                put("UseDualBrush", 1)
                put("DualRotation", 8)
                put("DualRotationEffector", 3)
                put("DualRotationRandomScale", 100)
                put("DualUseSpray", 1)
                put("DualRotationInSpray", -25)
                put("DualRotationEffectorInSpray", 0x81)
                put("DualRotationRandomInSpray", 50)
            })
        }
    }
}
