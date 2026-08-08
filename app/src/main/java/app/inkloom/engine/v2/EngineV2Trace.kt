package app.inkloom.engine.v2

import android.os.Trace

internal inline fun <T> traceEngineV2(section: String, block: () -> T): T {
    if (!Trace.isEnabled()) return block()
    Trace.beginSection(section)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}
