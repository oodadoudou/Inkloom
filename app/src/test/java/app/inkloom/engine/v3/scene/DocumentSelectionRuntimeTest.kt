package app.inkloom.engine.v3.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSelectionRuntimeTest {
    @Test fun oneDocumentOwnsIndependentPageSelections() {
        val runtime = DocumentSelectionRuntime(tileSize = 64)
        val first = runtime.stateFor("document", "page-1", 1600, 2263)
        val second = runtime.stateFor("document", "page-2", 1600, 2263)
        first.surface().beginWrite("lasso").run { setAlpha(40, 50, 255); commit().close() }

        assertEquals(255, first.snapshot().alphaAt(40, 50))
        assertEquals(0, second.snapshot().alphaAt(40, 50))
        assertEquals(2, runtime.pageStateCount())
        assertEquals(64L * 64, runtime.retainedByteSize())
    }

    @Test fun recompositionGetsTheSameDocumentOwnedState() {
        val runtime = DocumentSelectionRuntime()
        val first = runtime.stateFor("document", "page", 1600, 2263)
        val rebound = runtime.stateFor("document", "page", 1600, 2263)

        assertSame(first, rebound)
    }

    @Test fun geometryChangeReplacesAnIncompatiblePageState() {
        val runtime = DocumentSelectionRuntime()
        val before = runtime.stateFor("document", "page", 1600, 2263)
        before.surface().beginWrite("old crop").run { setAlpha(4, 5, 255); commit().close() }
        val after = runtime.stateFor("document", "page", 2263, 1600)

        assertNotSame(before, after)
        assertTrue(after.isEmpty)
        assertEquals(2263, after.logicalWidth)
        assertEquals(1600, after.logicalHeight)
    }

    @Test fun documentDeletionReleasesAllTransientSelectionMemory() {
        val runtime = DocumentSelectionRuntime(tileSize = 64)
        listOf("one", "two").forEach { page ->
            runtime.stateFor("document", page, 512, 512).surface().beginWrite("fixture").run {
                setAlpha(10, 10, 255)
                commit().close()
            }
        }

        runtime.releaseDocument("document")

        assertEquals(0, runtime.pageStateCount())
        assertEquals(0L, runtime.retainedByteSize())
    }
}
