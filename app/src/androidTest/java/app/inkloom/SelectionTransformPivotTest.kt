package app.inkloom

import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.tools.SelectionTransformPose
import app.inkloom.engine.v5.tools.SelectionAlignment
import app.inkloom.ui.SelectionCanvasTransform
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionTransformPivotTest {
    @Test fun referencePointDragDoesNotMoveContentAndNumericPositionMovesIt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val original = SelectionTransformPose(100f, 100f, 40f, 40f)
            val transform = SelectionCanvasTransform(original, 1000, 1000, 1f, {})
            transform.pivotEditing = true
            fun touch(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(0, 10, action, x, y, 0)
                try { transform.touch(event, x, y, 1000, 1000, 1f) } finally { event.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN, 100f, 100f)
            touch(MotionEvent.ACTION_MOVE, 80f, 80f)
            touch(MotionEvent.ACTION_UP, 80f, 80f)
            assertEquals(original, transform.pose)
            assertEquals(80f, transform.pivotX, .01f)
            assertEquals(80f, transform.pivotY, .01f)
            transform.setGeometry(20f, 30f, 80f, 60f, 0f)
            assertEquals(20f, transform.pose.centerX - transform.pose.width / 2f, .01f)
            assertEquals(30f, transform.pose.centerY - transform.pose.height / 2f, .01f)
            transform.resetPivot()
            assertEquals(transform.pose.centerX, transform.pivotX, .01f)
            assertEquals(transform.pose.centerY, transform.pivotY, .01f)
        }
    }

    @Test fun alignmentUsesVisibleBoundsAndShowsReferenceLine() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val transform = SelectionCanvasTransform(SelectionTransformPose(100f, 100f, 40f, 30f),
                1000, 1000, 1f, {})
            transform.alignToBounds(SelectionAlignment.LEFT, 20f, 0f, 120f, 200f)
            assertEquals(20f, transform.pose.sourceCorners().minOf { it.first }, .01f)
            transform.alignToBounds(SelectionAlignment.BOTTOM, 20f, 0f, 120f, 200f)
            assertEquals(200f, transform.pose.sourceCorners().maxOf { it.second }, .01f)
        }
    }

    @Test fun numericSkewChangesCornersWithoutResamplingDraft() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val original = SelectionTransformPose(100f, 100f, 40f, 40f)
            val transform = SelectionCanvasTransform(original, 1000, 1000, 1f, {})
            org.junit.Assert.assertTrue(transform.skewBy(10f, 5f))
            assertEquals(90f, transform.pose.sourceCorners()[0].first, .01f)
            assertEquals(85f, transform.pose.sourceCorners()[0].second, .01f)
            assertEquals(original, transform.original)
        }
    }
}
