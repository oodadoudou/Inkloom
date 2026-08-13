package app.inkloom.engine.v3.diagnostics

import android.os.Trace

internal inline fun <T> traceEngineV3(section: String, block: () -> T): T {
    if (!Trace.isEnabled()) return block()
    Trace.beginSection(section)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}
