package app.inkloom.importer

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProcreateResourceResolverAndroidTest {
    @Test fun generatedShapeAndGrainDecodeOnAndroid() {
        val root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "procreate-resource-android-${System.nanoTime()}",
        ).apply { mkdirs() }
        try {
            val resolver = ProcreateResourceResolver(allowProceduralApproximation = true)
            val shape = resolver.resolve(
                null, "Brush-Preset-Ink-Sponge.png", ProcreateResourceRole.SHAPE,
                File(root, "shape.png"),
            )
            val grain = resolver.resolve(
                null, "Brush-Preset-Grunge.jpg", ProcreateResourceRole.GRAIN,
                File(root, "grain.png"),
            )
            assertEquals(ProcreateResourceState.APPROXIMATED_RESOURCE, shape.state)
            assertEquals(ProcreateResourceState.APPROXIMATED_RESOURCE, grain.state)
            listOf(shape.file, grain.file).forEach { file ->
                assertTrue(file?.isFile == true && file.length() > 0L)
                val bitmap = BitmapFactory.decodeFile(file!!.absolutePath)
                assertNotNull(bitmap)
                assertEquals(96, bitmap.width)
                assertEquals(96, bitmap.height)
                bitmap.recycle()
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
