package app.inkloom

import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.settings.DrawingColors
import app.inkloom.settings.DrawingColorStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DrawingColorStoreTest {
    @Test fun notebookColorPairsPreserveAlphaAcrossReadersAndDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstId = "color-store-first"
        val secondId = "color-store-second"
        val store = DrawingColorStore(context)
        store.remove(firstId)
        store.remove(secondId)
        try {
            assertEquals(DrawingColors(), store.load(firstId))
            val first = DrawingColors(0x803355aa.toInt(), 0x00fedcba)
            val second = DrawingColors(0xffffffff.toInt(), 0xff000000.toInt())
            store.save(firstId, first)
            store.save(secondId, second)
            val reader = DrawingColorStore(context)
            assertEquals(first, reader.load(firstId))
            assertEquals(second, reader.load(secondId))
            store.remove(firstId)
            assertEquals(DrawingColors(), reader.load(firstId))
            assertEquals(second, reader.load(secondId))
            assertFalse(context.getSharedPreferences("inkloom_drawing_colors", Context.MODE_PRIVATE).contains(firstId))
        } finally {
            store.remove(firstId)
            store.remove(secondId)
        }
    }

    @Test fun swapPublishesOneCompletePairAndIdenticalColorDoesNotWrite() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val id = "color-store-swap"
        val store = DrawingColorStore(context)
        val preferences = context.getSharedPreferences("inkloom_drawing_colors", Context.MODE_PRIVATE)
        val original = DrawingColors(0x80663399.toInt(), 0xccee9911.toInt())
        store.save(id, original)
        instrumentation.waitForIdleSync()
        val observed = mutableListOf<DrawingColors>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == id) observed += store.load(id)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        try {
            val swapped = DrawingColors(original.background, original.foreground)
            instrumentation.runOnMainSync {
                store.save(id, swapped)
                store.save(id, swapped)
            }
            instrumentation.waitForIdleSync()
            assertEquals(listOf(swapped), observed)
        } finally {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            store.remove(id)
        }
    }
}
