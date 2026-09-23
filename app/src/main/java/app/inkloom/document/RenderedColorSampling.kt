package app.inkloom.document

object RenderedColorSampling {
    /** A transparent hit has no drawing color; keep the selected swatch unchanged. */
    fun pick(argb: Int): Int? = argb.takeIf { (it ushr 24) != 0 }
}
