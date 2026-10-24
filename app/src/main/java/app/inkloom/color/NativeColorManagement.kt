package app.inkloom.color

/** Little CMS handles unassociated RGB float components. Alpha is preserved by RGB transforms. */
internal object NativeColorManagement {
    init { System.loadLibrary("inkloom_engine_v5") }
    external fun profileKind(bytes: ByteArray): Int
    external fun builtinProfile(kind: Int): ByteArray
    external fun create(source: ByteArray?, sourceBuiltin: Int, destination: ByteArray?, destinationBuiltin: Int,
        proof: ByteArray?, intent: Int, cmyk: Boolean, gamutCheck: Boolean): Long
    external fun apply(handle: Long, input: FloatArray, output: FloatArray?, cmykOutput: ByteArray?)
    external fun close(handle: Long)
}
internal class IccTransform(source: ByteArray? = null, sourceBuiltin: Int = 1, destination: ByteArray? = null,
    destinationBuiltin: Int = 1, proof: ByteArray? = null, intent: Int = 1, private val cmyk: Boolean = false,
    gamutCheck: Boolean = false) : AutoCloseable {
    private var handle = NativeColorManagement.create(source,sourceBuiltin,destination,destinationBuiltin,proof,intent,cmyk,gamutCheck)
    fun rgb(input: FloatArray, output: FloatArray) { check(handle!=0L && !cmyk); NativeColorManagement.apply(handle,input,output,null) }
    fun cmyk(input: FloatArray, output: ByteArray) { check(handle!=0L && cmyk); NativeColorManagement.apply(handle,input,null,output) }
    override fun close() { if(handle!=0L) { NativeColorManagement.close(handle); handle=0 } }
}
