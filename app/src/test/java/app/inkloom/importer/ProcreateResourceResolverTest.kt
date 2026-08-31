package app.inkloom.importer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProcreateResourceResolverTest {
    @Test fun exactLocalResourcePackWinsWithoutChangingBytes() = withTempDirectory { root ->
        val pack = File(root, "pack/nested").apply { mkdirs() }
        val source = File(pack, "Brush-Preset-Hard.png").apply { writeBytes(byteArrayOf(1, 7, 3, 9)) }
        val output = File(root, "resolved.png")

        val result = ProcreateResourceResolver(File(root, "pack"), allowProceduralApproximation = true)
            .resolve(null, "brush-preset-hard.PNG", ProcreateResourceRole.SHAPE, output)

        assertEquals(ProcreateResourceState.EXACT_LOCAL_RESOURCE_PACK, result.state)
        assertEquals(output, result.file)
        assertArrayEquals(source.readBytes(), output.readBytes())
    }

    @Test fun missingResourceRemainsExplicitWhenApproximationIsDisabled() = withTempDirectory { root ->
        val result = ProcreateResourceResolver()
            .resolve(null, "Brush-Preset-Grunge.jpg", ProcreateResourceRole.GRAIN, File(root, "grain.png"))

        assertEquals(ProcreateResourceState.UNSUPPORTED_MISSING_RESOURCE, result.state)
        assertNull(result.file)
    }

    @Test fun proceduralSubstituteIsDeterministicAndExplicitlyApproximate() = withTempDirectory { root ->
        val first = File(root, "first.png")
        val second = File(root, "second.png")
        val resolver = ProcreateResourceResolver(allowProceduralApproximation = true)

        val a = resolver.resolve(null, "Brush-Preset-Grunge.jpg", ProcreateResourceRole.GRAIN, first)
        val b = resolver.resolve(null, "Brush-Preset-Grunge.jpg", ProcreateResourceRole.GRAIN, second)

        assertEquals(ProcreateResourceState.APPROXIMATED_RESOURCE, a.state)
        assertEquals(ProcreateResourceState.APPROXIMATED_RESOURCE, b.state)
        assertArrayEquals(first.readBytes(), second.readBytes())
        assertTrue(first.readBytes().take(8).toByteArray().contentEquals(PNG_SIGNATURE))
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("inkloom-procreate-resource").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        )
    }
}
