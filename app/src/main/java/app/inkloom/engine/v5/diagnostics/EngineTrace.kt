package app.inkloom.engine.v5.diagnostics

import android.os.Trace

internal inline fun <T> traceEngineV5(section: String, block: () -> T): T {
    if (!Trace.isEnabled()) return block()
    Trace.beginSection(section)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}
