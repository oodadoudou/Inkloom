package app.inkloom.color

import kotlinx.serialization.Serializable

/** Pixels always use canonical linear extended sRGB. Profile assignment reinterprets the nominal
 * RGB numbers at presentation/output; output conversion does not rewrite editable source data. */
@Serializable
data class PageColorManagement(
    val highPrecision: Boolean = false,
    val assignedProfile: String? = null,
    val outputProfile: String? = null,
    val outputSpace: RgbOutputSpace = RgbOutputSpace.SRGB,
    val proofProfile: String? = null,
    val cmykProfile: String? = null,
    val softProof: Boolean = false,
    val intent: ColorRenderingIntent = ColorRenderingIntent.RELATIVE,
) {
    fun assetPaths() = listOfNotNull(assignedProfile, outputProfile, proofProfile,cmykProfile)
    fun mapAssets(map: (String) -> String) = copy(assignedProfile=assignedProfile?.let(map),
        outputProfile=outputProfile?.let(map), proofProfile=proofProfile?.let(map),cmykProfile=cmykProfile?.let(map))
}
@Serializable enum class RgbOutputSpace { SRGB, DISPLAY_P3 }
@Serializable enum class ColorRenderingIntent(val code: Int) { PERCEPTUAL(0), RELATIVE(1), SATURATION(2), ABSOLUTE(3) }
