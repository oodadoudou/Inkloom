package app.inkloom

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.importer.BrushImportFailureStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushImportFailureStoreTest {
    @Test fun failuresPersistUpdateWithoutDuplicatesAndCanBeRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = "brush-import-failure-test-${System.nanoTime()}"
        context.getSharedPreferences(preferences, Context.MODE_PRIVATE).edit().clear().commit()
        try {
            val store = BrushImportFailureStore(context, preferences)
            val first = store.record(
                sourceUri = "content://fixture/brush.sut",
                displayName = "brush.sut",
                targetGroup = "Ink / Pens",
                reasons = listOf("decode failed", "decode failed"),
                finalStates = listOf("FAILED_DECODE"),
                attemptedAt = 100L,
            )
            val updated = store.record(
                sourceUri = "content://fixture/brush.sut",
                displayName = "brush.sut",
                targetGroup = "Ink/Pens",
                reasons = listOf("asset missing"),
                finalStates = listOf("FAILED_ASSET"),
                attemptedAt = 200L,
            )

            val persisted = BrushImportFailureStore(context, preferences).load().single()
            assertEquals(first.id, updated.id)
            assertEquals(2, persisted.attemptCount)
            assertEquals(100L, persisted.createdAt)
            assertEquals(200L, persisted.lastAttemptAt)
            assertEquals("Ink/Pens", persisted.targetGroup)
            assertEquals(listOf("asset missing"), persisted.reasons)

            store.remove(first.id)
            assertTrue(store.load().isEmpty())
        } finally {
            context.getSharedPreferences(preferences, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
