package app.inkloom

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.importer.BrushVersionHistoryStore
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushVersionHistoryStoreTest {
    @Test fun historyPersistsDeduplicatesBoundsRestoresAndClearsSnapshots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = "brush-history-test-${System.nanoTime()}"
        context.getSharedPreferences(preferences, Context.MODE_PRIVATE).edit().clear().commit()
        try {
            val original = ImportedBrush(
                id = "brush-1",
                name = "Original preset",
                format = BrushFormat.PROCREATE,
                compatibility = Compatibility.BASIC,
                sizeBytes = 123,
                report = listOf("fixture"),
                groupName = "Inking",
                defaultSize = 8f,
                defaultOpacity = .7f,
                spacing = .2f,
                grainOffsetJitter = true,
                strokeRotationJitterDegrees = 180f,
            )
            val store = BrushVersionHistoryStore(context, preferences)

            store.record(original, 100L)
            store.record(original, 101L)
            assertEquals(1, store.versions(original.id).size)

            for (index in 1..24) store.record(original.copy(defaultSize = index.toFloat()), 101L + index)
            val persisted = BrushVersionHistoryStore(context, preferences).versions(original.id)

            assertEquals(BrushVersionHistoryStore.MAX_VERSIONS_PER_BRUSH, persisted.size)
            assertEquals(24f, persisted.first().snapshot.defaultSize, .0001f)
            assertEquals(5f, persisted.last().snapshot.defaultSize, .0001f)

            val current = original.copy(name = "Current preset", defaultSize = 99f, favorite = true, hidden = true, lastUsedAt = 999L)
            val restored = store.restore(current, persisted.last())
            assertEquals(current.id, restored.id)
            assertEquals("Original preset", restored.name)
            assertEquals("Inking", restored.groupName)
            assertEquals(5f, restored.defaultSize, .0001f)
            assertTrue(restored.grainOffsetJitter)
            assertEquals(180f, restored.strokeRotationJitterDegrees, .0001f)
            assertTrue(restored.favorite)
            assertTrue(restored.hidden)
            assertEquals(999L, restored.lastUsedAt)

            store.delete(persisted.first().versionId)
            assertEquals(19, store.versions(original.id).size)
            store.clear(original.id)
            assertTrue(store.versions(original.id).isEmpty())
        } finally {
            context.getSharedPreferences(preferences, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
