package app.inkloom.document

object BrushStampRotation {
    fun followsStroke(stroke: InkStroke, dual: Boolean): Boolean = when {
        dual && stroke.dualSpray -> stroke.dualSprayParticleFollowStrokeDirection
        dual -> stroke.dualFollowStrokeDirection || stroke.dualRibbon
        stroke.spray -> stroke.sprayParticleFollowStrokeDirection
        else -> stroke.followStrokeDirection || stroke.ribbon
    }

    fun degrees(
        stroke: InkStroke,
        dual: Boolean,
        stylusOrientationRadians: Float,
        strokeDirectionDegrees: Float,
        randomUnit: Float,
    ): Float {
        val spray = if (dual) stroke.dualSpray else stroke.spray
        val strokeJitter = if (dual) stroke.dualStrokeRotationJitterDegrees else stroke.strokeRotationJitterDegrees
        return degrees(
            fixedDegrees = when {
                dual && spray -> stroke.dualSprayParticleRotationDegrees
                dual -> stroke.dualRotationDegrees
                spray -> stroke.sprayParticleRotationDegrees
                else -> stroke.rotationDegrees
            },
            followStylusOrientation = when {
                dual && spray -> stroke.dualSprayParticleFollowStylusOrientation
                dual -> stroke.dualFollowStylusOrientation
                spray -> stroke.sprayParticleFollowStylusOrientation
                else -> stroke.followStylusOrientation
            },
            followStrokeDirection = followsStroke(stroke, dual),
            stylusOrientationRadians = stylusOrientationRadians,
            strokeDirectionDegrees = strokeDirectionDegrees,
            randomRotationDegrees = when {
                dual && spray -> stroke.dualSprayParticleRandomRotationDegrees
                dual -> stroke.dualRandomRotationDegrees
                spray -> stroke.sprayParticleRandomRotationDegrees
                else -> stroke.randomRotationDegrees
            },
            randomUnit = randomUnit,
            strokeRotationDegrees = signedRandom(stroke.renderSeed, if (dual) DUAL_STROKE_SEED else MAIN_STROKE_SEED) * strokeJitter.coerceIn(0f, 180f),
        )
    }

    fun degrees(
        fixedDegrees: Float,
        followStylusOrientation: Boolean,
        followStrokeDirection: Boolean,
        stylusOrientationRadians: Float,
        strokeDirectionDegrees: Float,
        randomRotationDegrees: Float,
        randomUnit: Float,
        strokeRotationDegrees: Float = 0f,
    ): Float = fixedDegrees.coerceIn(-360f, 360f) +
        (if (followStylusOrientation) Math.toDegrees(stylusOrientationRadians.toDouble()).toFloat() else 0f) +
        (if (followStrokeDirection) strokeDirectionDegrees else 0f) +
        strokeRotationDegrees.coerceIn(-180f, 180f) +
        (randomUnit.coerceIn(0f, 1f) - .5f) * 2f * randomRotationDegrees.coerceIn(0f, 180f)

    private fun signedRandom(renderSeed: Int, salt: Long): Float = java.util.Random(renderSeed.toLong() xor salt).nextFloat() * 2f - 1f

    private const val MAIN_STROKE_SEED = 0x58A71D3BL
    private const val DUAL_STROKE_SEED = 0x73C4E291L
}
