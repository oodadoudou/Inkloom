package app.inkloom.engine.v5.diagnostics

import java.util.concurrent.atomic.AtomicLong

/**
 * Opt-in counters for the color picker's high-frequency interaction path.
 *
 * The default-off branch keeps production overhead negligible. Android tests enable this trace to
 * prove that MOVE samples stay transient, gradient resources remain stable, and only the gesture
 * boundary publishes the final workspace color.
 */
internal object ColorPickerInteractionTrace {
    data class Snapshot(
        val pointerSamples: Long,
        val previewWrites: Long,
        val workspaceCommits: Long,
        val panelCompositions: Long,
        val hueRingAllocations: Long,
        val saturationGradientAllocations: Long,
    )

    @Volatile var enabled: Boolean = false

    private val pointerSamples = AtomicLong()
    private val previewWrites = AtomicLong()
    private val workspaceCommits = AtomicLong()
    private val panelCompositions = AtomicLong()
    private val hueRingAllocations = AtomicLong()
    private val saturationGradientAllocations = AtomicLong()

    fun pointerSample() = increment(pointerSamples)
    fun previewWrite() = increment(previewWrites)
    fun workspaceCommit() = increment(workspaceCommits)
    fun panelComposition() = increment(panelCompositions)
    fun hueRingAllocation() = increment(hueRingAllocations)
    fun saturationGradientAllocation() = increment(saturationGradientAllocations)

    fun snapshot() = Snapshot(
        pointerSamples = pointerSamples.get(),
        previewWrites = previewWrites.get(),
        workspaceCommits = workspaceCommits.get(),
        panelCompositions = panelCompositions.get(),
        hueRingAllocations = hueRingAllocations.get(),
        saturationGradientAllocations = saturationGradientAllocations.get(),
    )

    fun reset() {
        pointerSamples.set(0)
        previewWrites.set(0)
        workspaceCommits.set(0)
        panelCompositions.set(0)
        hueRingAllocations.set(0)
        saturationGradientAllocations.set(0)
    }

    private fun increment(counter: AtomicLong) {
        if (enabled) counter.incrementAndGet()
    }
}
