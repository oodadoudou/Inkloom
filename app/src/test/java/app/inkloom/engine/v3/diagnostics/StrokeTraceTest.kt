package app.inkloom.engine.v3.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StrokeTraceTest {

    @Before
    fun setUp() {
        StrokeTrace.reset()
        StrokeTrace.enabled = true
    }

    @After
    fun tearDown() {
        StrokeTrace.enabled = false
        StrokeTrace.reset()
    }

    @Test
    fun disabledTraceIsNoOp() {
        StrokeTrace.enabled = false
        assertNull(StrokeTrace.begin("disabled"))
        StrokeTrace.mark("disabled", StrokeTrace.STAGE_FIRST_SAMPLE)
        StrokeTrace.markOnce("disabled", StrokeTrace.STAGE_FIRST_DAB)
        StrokeTrace.abandon("disabled")
    }

    @Test
    fun sessionTracksTimelineInOrder() {
        val session = StrokeTrace.begin("s1")
        assertNotNull(session)
        session!!.mark(StrokeTrace.STAGE_FIRST_SAMPLE)
        session.mark(StrokeTrace.STAGE_RUNTIME_CREATED)
        session.mark(StrokeTrace.STAGE_FRONT_BUFFER_DRAW)
        session.mark(StrokeTrace.STAGE_ACTION_UP)
        session.mark(StrokeTrace.STAGE_TRANSACTION_COMMIT)
        session.mark(StrokeTrace.STAGE_FRONT_BUFFER_COMMIT)
        session.mark(StrokeTrace.STAGE_TRANSIENT_HIDDEN)
        assertEquals(
            listOf(
                StrokeTrace.STAGE_ACTION_DOWN,
                StrokeTrace.STAGE_FIRST_SAMPLE,
                StrokeTrace.STAGE_RUNTIME_CREATED,
                StrokeTrace.STAGE_FRONT_BUFFER_DRAW,
                StrokeTrace.STAGE_ACTION_UP,
                StrokeTrace.STAGE_TRANSACTION_COMMIT,
                StrokeTrace.STAGE_FRONT_BUFFER_COMMIT,
                StrokeTrace.STAGE_TRANSIENT_HIDDEN,
                StrokeTrace.STAGE_STABLE_FRAME,
            ),
            session.timeline().map { it.stage },
        )
        assertTrue(session.completed)
    }

    @Test
    fun markOnceRecordsOnlyFirstOccurrence() {
        val session = StrokeTrace.begin("s2")!!
        session.markOnce(StrokeTrace.STAGE_FIRST_DAB)
        session.markOnce(StrokeTrace.STAGE_FIRST_DAB)
        session.markOnce(StrokeTrace.STAGE_FIRST_DAB)
        assertEquals(1, session.timeline().count { it.stage == StrokeTrace.STAGE_FIRST_DAB })
    }

    @Test
    fun sessionWithoutTransientHiddenNeverCompletes() {
        val session = StrokeTrace.begin("s3")!!
        session.mark(StrokeTrace.STAGE_TRANSACTION_COMMIT)
        session.mark(StrokeTrace.STAGE_FRONT_BUFFER_COMMIT)
        assertTrue(!session.completed)
        assertNull(session.first(StrokeTrace.STAGE_STABLE_FRAME))
    }

    @Test
    fun derivedMetricsAggregateAcrossSessions() {
        repeat(3) { i ->
            val session = StrokeTrace.begin("agg-$i")!!
            session.mark(StrokeTrace.STAGE_FIRST_SAMPLE)
            session.mark(StrokeTrace.STAGE_FRONT_BUFFER_DRAW)
            session.mark(StrokeTrace.STAGE_ACTION_UP)
            session.mark(StrokeTrace.STAGE_TRANSACTION_COMMIT)
            session.mark(StrokeTrace.STAGE_TRANSIENT_HIDDEN)
        }
        val samples = StrokeTrace.samplesFor("DOWN→first transient")
        assertEquals(3, samples.size)
        assertTrue(samples.all { it >= 0 })
    }

    @Test
    fun abandonedSessionDoesNotAggregate() {
        StrokeTrace.begin("gone")!!.let { session ->
            session.mark(StrokeTrace.STAGE_FRONT_BUFFER_DRAW)
            StrokeTrace.abandon("gone")
        }
        assertTrue(StrokeTrace.samplesFor("DOWN→first transient").isEmpty())
    }

    @Test
    fun percentileHelper() {
        val values = listOf(1L, 2L, 3L, 4L, 100L).sorted()
        assertEquals(3L, values.percentile(.50))
        assertEquals(4L, values.percentile(.95))
        assertEquals(4L, values.percentile(.99))
        assertEquals(100L, values.percentile(1.0))
        assertEquals(1L, values.percentile(0.0))
        assertEquals(0L, emptyList<Long>().percentile(.5))
    }

    @Test
    fun traceBaseIdStripsSymmetrySuffix() {
        assertEquals("abc", StrokeTrace.traceBaseId("abc"))
        assertEquals("abc", StrokeTrace.traceBaseId("abc-sym-0"))
        assertEquals("abc", StrokeTrace.traceBaseId("abc-sym-7"))
    }
}
