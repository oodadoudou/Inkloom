package app.inkloom

import app.inkloom.settings.RasterFilterPreset
import app.inkloom.ui.RasterFilterThumbnails
import org.junit.Assert.*
import org.junit.Test

/** Every filter kind renders a distinct, cached preset thumbnail. */
class FilterThumbnailTest {
    @Test fun everyKindRendersAThumbnailAndDifferentFiltersLookDifferent() {
        val fingerprints = (0..RasterFilterPreset.LAST_KIND).associateWith { kind ->
            val bitmap = requireNotNull(RasterFilterThumbnails.render(RasterFilterPreset(name = "k$kind", kind = kind))) { "kind $kind" }
            assertEquals(72, bitmap.width); assertEquals(72, bitmap.height)
            IntArray(72 * 72).also { bitmap.getPixels(it, 0, 72, 0, 0, 72, 72) }.contentHashCode()
        }
        val blur = fingerprints.getValue(16); val mosaic = fingerprints.getValue(9); val invert = fingerprints.getValue(RasterFilterPreset.FIRST_TONAL_KIND + 5)
        assertEquals(3, setOf(blur, mosaic, invert).size)
        // Names and favourites do not change the picture, so the cached thumbnail is reused.
        val a = RasterFilterThumbnails.render(RasterFilterPreset(name = "A", kind = 16))
        val b = RasterFilterThumbnails.render(RasterFilterPreset(name = "B", favorite = true, kind = 16))
        assertSame(a, b)
    }
}
