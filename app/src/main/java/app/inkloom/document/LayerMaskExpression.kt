package app.inkloom.document

import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot

/** Composition view of the editable mask. All exporters and live renderers use this same rule. */
fun LayerMask.expressed(snapshot: MaskTileSurfaceSnapshot): MaskTileSurfaceSnapshot =
    if (showGradients) snapshot else snapshot.thresholded(expressionThreshold.coerceIn(0, 255))
