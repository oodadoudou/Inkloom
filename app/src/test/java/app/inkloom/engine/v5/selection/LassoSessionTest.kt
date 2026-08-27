package app.inkloom.engine.v5.selection

import app.inkloom.engine.v5.coordinates.DocumentPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LassoSessionTest {
    @Test fun previewAndFinalShareTheExactCapturedTrajectory() {
        val session = LassoSession(1_600f, 2_263f)
        val points = listOf(
            DocumentPoint(100f, 100f),
            DocumentPoint(900f, 100f),
            DocumentPoint(900f, 800f),
            DocumentPoint(100f, 800f),
        )
        points.forEach(session::append)
        val preview = session.preview()

        val final = session.finish()

        assertEquals(points, preview.points)
        assertEquals(preview.points, final!!.points)
        assertFalse(preview.closed)
        assertTrue(final.closed)
    }

    @Test fun filteringIsDefinedInDocumentPixelsAndDoesNotRewritePriorPoints() {
        val session = LassoSession(1_600f, 2_263f, minimumSampleDistance = 1f)
        assertTrue(session.append(DocumentPoint(10f, 10f)))
        assertFalse(session.append(DocumentPoint(10.5f, 10.25f)))
        assertTrue(session.append(DocumentPoint(12f, 10f)))

        assertEquals(listOf(DocumentPoint(10f, 10f), DocumentPoint(12f, 10f)), session.preview().points)
    }

    @Test fun cancelAndDegenerateContoursNeverProduceSelectionTruth() {
        val degenerate = LassoSession(100f, 100f, 0f)
        degenerate.append(DocumentPoint(10f, 10f))
        degenerate.append(DocumentPoint(20f, 20f))
        degenerate.append(DocumentPoint(30f, 30f))
        assertNull(degenerate.finish())

        val cancelled = LassoSession(100f, 100f)
        cancelled.append(DocumentPoint(10f, 10f))
        cancelled.cancel()
        assertFalse(cancelled.isActive)
        assertTrue(cancelled.preview().points.isEmpty())
    }
}
