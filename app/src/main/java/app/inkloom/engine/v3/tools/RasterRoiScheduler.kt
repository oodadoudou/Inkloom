package app.inkloom.engine.v3.tools

import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Bounded worker scheduler for tile-independent ROI kernels. */
internal object RasterRoiScheduler {
    internal val maxConcurrency = minOf(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
    private val executor = Executors.newFixedThreadPool(
        maxConcurrency,
    ) { runnable -> Thread(runnable, "Inkloom-RasterROI").apply { isDaemon = true } }

    fun <T> map(
        plan: RasterRoiPlan,
        units: List<RasterRoiWorkUnit> = plan.writeUnits,
        operation: (RasterRoiWorkUnit) -> T,
    ): List<T> {
        if (plan.executionMode == RasterExecutionMode.SERIAL || units.size <= 1) {
            return units.map(operation)
        }
        return executor.invokeAll(units.map { unit -> Callable { operation(unit) } })
            .map { future -> future.get() }
    }
}
