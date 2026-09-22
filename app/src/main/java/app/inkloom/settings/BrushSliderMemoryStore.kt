package app.inkloom.settings

import android.content.Context
import kotlin.math.abs

/** Slider positions are normalized so size's nonlinear scale and opacity share one gesture model. */
object BrushSliderMemory {
    const val MAX_MARKS = 4
    private const val MATCH_DISTANCE = .018f
    private const val SNAP_DISTANCE = .035f

    fun toggle(current: List<Float>, progress: Float): List<Float> {
        require(progress.isFinite())
        val value = progress.coerceIn(0f, 1f)
        val match = current.minByOrNull { abs(it - value) }?.takeIf { abs(it - value) <= MATCH_DISTANCE }
        if (match != null) return current - match
        if (current.size >= MAX_MARKS) return current
        return (current + value).sorted()
    }

    fun snap(current: List<Float>, progress: Float, bypass: Boolean = false): Float {
        val value = progress.coerceIn(0f, 1f)
        if (bypass) return value
        return current.minByOrNull { abs(it - value) }?.takeIf { abs(it - value) <= SNAP_DISTANCE } ?: value
    }
}

/** Separate from authored brushes and last-used masters; changing brushes never rewrites their library. */
class BrushSliderMemoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("brush_slider_memories", Context.MODE_PRIVATE)

    fun load(owner: String, control: String): List<Float> = synchronized(lock) {
        preferences.getString(key(owner, control), null).orEmpty().split(',').mapNotNull(String::toFloatOrNull)
            .filter { it.isFinite() && it in 0f..1f }.distinct().take(BrushSliderMemory.MAX_MARKS).sorted()
    }

    fun toggle(owner: String, control: String, progress: Float): List<Float> = synchronized(lock) {
        val next = BrushSliderMemory.toggle(load(owner, control), progress)
        preferences.edit().putString(key(owner, control), next.joinToString(",")).apply()
        next
    }

    private fun key(owner: String, control: String): String {
        require(owner.isNotBlank() && control in setOf("size", "opacity"))
        return "$control:$owner"
    }

    companion object { private val lock = Any() }
}
