package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReferenceImageStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun imagesAreCopiedListedInOrderAndRemoved() {
        val store = ReferenceImageStore(folder.root)
        store.add("Pose", "png", "first".byteInputStream())
        val images = store.add("Light", "jpeg", "second".byteInputStream())
        assertEquals(listOf("Pose", "Light"), images.map { it.name })
        assertEquals("second", store.file(images[1]).readText())
        assertEquals(images, ReferenceImageStore(folder.root).list())
        val left = store.remove(images[0].id)
        assertEquals(listOf("Light"), left.map { it.name })
        assertFalse(store.file(images[0]).exists())
    }

    @Test fun anOversizedImageLeavesTheListUnchanged() {
        val store = ReferenceImageStore(folder.root)
        store.add("Small", "png", "ok".byteInputStream())
        val huge = object : java.io.InputStream() {
            var left = ReferenceImageStore.MAX_BYTES + 1
            override fun read(): Int = if (left-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len.toLong(), left).toInt(); left -= n; return n
            }
        }
        assertTrue(runCatching { store.add("Huge", "png", huge) }.isFailure)
        assertEquals(listOf("Small"), store.list().map { it.name })
        assertEquals("no partial file is left", 2, folder.root.resolve("reference-images").listFiles()!!.size)
    }
}
