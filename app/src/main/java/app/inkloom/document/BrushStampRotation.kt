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
    ): Float = fixedDegrees.coerceIn(-360f, 360f) +
        (if (followStylusOrientation) Math.toDegrees(stylusOrientationRadians.toDouble()).toFloat() else 0f) +
        (if (followStrokeDirection) strokeDirectionDegrees else 0f) +
        (randomUnit.coerceIn(0f, 1f) - .5f) * 2f * randomRotationDegrees.coerceIn(0f, 180f)
}
