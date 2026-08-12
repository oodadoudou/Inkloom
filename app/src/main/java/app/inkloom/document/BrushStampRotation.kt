package app.inkloom.document

object BrushStampRotation {
    fun followsStroke(stroke: InkStroke, dual: Boolean): Boolean = when {
        dual && stroke.dualSpray -> stroke.dualSprayParticleFollowStrokeDirection
        dual -> stroke.dualFollowStrokeDirection || stroke.dualRibbon ||
            shouldUseTouchRotation(stroke, stroke.dualFollowStylusOrientation, stroke.dualShapeTouchRotation)
        stroke.spray -> stroke.sprayParticleFollowStrokeDirection
        else -> stroke.followStrokeDirection || stroke.ribbon ||
            shouldUseTouchRotation(stroke, stroke.followStylusOrientation, stroke.shapeTouchRotation)
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
                dual -> stroke.dualRotationDegrees + stroke.dualShapeScreenRotationDegrees
                spray -> stroke.sprayParticleRotationDegrees
                else -> stroke.rotationDegrees + stroke.shapeScreenRotationDegrees
            },
            followStylusOrientation = when {
                dual && spray -> stroke.dualSprayParticleFollowStylusOrientation
                dual -> stroke.dualFollowStylusOrientation
                spray -> stroke.sprayParticleFollowStylusOrientation
                else -> stroke.followStylusOrientation
            },
            followStrokeDirection = when {
                dual && spray -> stroke.dualSprayParticleFollowStrokeDirection
                dual -> stroke.dualFollowStrokeDirection || stroke.dualRibbon
                spray -> stroke.sprayParticleFollowStrokeDirection
                else -> stroke.followStrokeDirection || stroke.ribbon
            },
            strokeDirectionRotation = when {
                dual && spray -> 0f
                dual -> stroke.dualShapeTouchRotation
                spray -> 0f
                else -> stroke.shapeTouchRotation
            },
            inputKind = stroke.inputKind,
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
        strokeDirectionRotation: Float = 0f,
        inputKind: StrokeInputKind = StrokeInputKind.STYLUS,
    ): Float {
        val useAzimuth = followStylusOrientation && inputKind == StrokeInputKind.STYLUS
        val directionFactor = when {
            followStrokeDirection -> 1f
            useAzimuth -> 0f
            else -> strokeDirectionRotation.coerceIn(-1f, 1f)
        }
        return fixedDegrees.coerceIn(-360f, 360f) +
        (if (useAzimuth) Math.toDegrees(stylusOrientationRadians.toDouble()).toFloat() else 0f) +
        strokeDirectionDegrees * directionFactor +
        strokeRotationDegrees.coerceIn(-180f, 180f) +
        (randomUnit.coerceIn(0f, 1f) - .5f) * 2f * randomRotationDegrees.coerceIn(0f, 180f)
    }

    private fun shouldUseTouchRotation(stroke: InkStroke, followsAzimuth: Boolean, amount: Float): Boolean =
        amount != 0f && (!followsAzimuth || stroke.inputKind == StrokeInputKind.TOUCH)

    private fun signedRandom(renderSeed: Int, salt: Long): Float = java.util.Random(renderSeed.toLong() xor salt).nextFloat() * 2f - 1f

    private const val MAIN_STROKE_SEED = 0x58A71D3BL
    private const val DUAL_STROKE_SEED = 0x73C4E291L
}
