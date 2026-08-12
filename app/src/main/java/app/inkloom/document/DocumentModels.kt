package app.inkloom.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class InkDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val folderId: String? = null,
    val coverArgb: Long = 0xFF202020,
    val coverStyle: CoverStyle = CoverStyle.BLACK,
    val coverImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val archivedAt: Long? = null,
    val trashedAt: Long? = null,
    val pages: List<InkPage> = listOf(InkPage()),
)

@Serializable
enum class CoverStyle { BLACK, WHITE, BLACK_BAND, WHITE_GRID, CUSTOM_IMAGE }

@Serializable
data class InkPage(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Page",
    val customNumber: String? = null,
    val tags: List<String> = emptyList(),
    val outlineLevel: Int = 0,
    val outlineCollapsed: Boolean = false,
    val width: Float = 1600f,
    val height: Float = 2263f,
    val background: PageBackground = PageBackground.Paper(),
    val backgroundRotationQuarterTurns: Int = 0,
    val crop: PageCrop = PageCrop(),
    val layers: List<InkLayer> = defaultInkLayers(),
    val strokes: List<InkStroke> = emptyList(),
    val elements: List<PageElement> = emptyList(),
    val bookmarked: Boolean = false,
    val pdfTextBlocks: List<PdfTextBlock> = emptyList(),
    val pdfTextIndexVersion: Int = 0,
    val pdfGotoLinks: List<PdfGotoLink> = emptyList(),
    val pdfLinkIndexVersion: Int = 0,
    val recognizedTextBlocks: List<RecognizedTextBlock> = emptyList(),
    val handwritingIndexFingerprint: String = "",
    val imageOcrFingerprint: String = "",
)

@Serializable
data class PageCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
)

@Serializable
data class PdfTextBlock(
    val text: String,
    val bounds: List<PdfTextBounds> = emptyList(),
)

@Serializable
data class PdfTextBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Serializable
data class PdfGotoLink(
    val bounds: List<PdfTextBounds>,
    val destinationPageId: String,
    val destinationX: Float = 0f,
    val destinationY: Float = 0f,
    val destinationZoom: Float = 0f,
)

@Serializable
enum class RecognizedTextSource { HANDWRITING, IMAGE_OCR }

@Serializable
data class RecognizedTextBlock(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val bounds: PdfTextBounds,
    val source: RecognizedTextSource,
    val sourceIds: List<String> = emptyList(),
    val languageTag: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

object LayerIds {
    const val BACKGROUND = "background"
    const val INK = "ink"
    const val HIGHLIGHT = "highlight"
    const val OBJECTS = "objects"
}

@Serializable
enum class LayerRole { BACKGROUND, INK, HIGHLIGHT, OBJECTS, CUSTOM, FOLDER }

/** Vector layers retain editable stroke geometry. Raster layers own a persistent RGBA paint
 * surface and are the only layer kind on which pixel-sampling tools such as Blend operate. */
@Serializable
enum class LayerContentKind { VECTOR, RASTER }

@Serializable
enum class LayerBlendMode {
    NORMAL, MULTIPLY, SCREEN, ADD,
    OVERLAY, SOFT_LIGHT, HARD_LIGHT, COLOR_DODGE, COLOR_BURN,
    LIGHTEN, DARKEN, DIFFERENCE, EXCLUSION,
    HUE, SATURATION, COLOR, LUMINOSITY;
}

@Serializable
data class InkLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: LayerRole = LayerRole.CUSTOM,
    val contentKind: LayerContentKind = LayerContentKind.VECTOR,
    /** Content-addressed, full-page transparent PNG for a raster layer. */
    val rasterAssetPath: String? = null,
    /** Non-destructive page rotations applied after the raster asset was last written. */
    val rasterRotationQuarterTurns: Int = 0,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    val alphaLock: Boolean = false,
    /** IDs that formed the alpha mask when locking was enabled. Empty preserves legacy layers. */
    val alphaLockBaseContentIds: List<String> = emptyList(),
    val clippedToBelow: Boolean = false,
    val reference: Boolean = false,
    val draft: Boolean = false,
    val mask: LayerMask? = null,
    val parentId: String? = null,
    val collapsed: Boolean = false,
)

@Serializable
data class LayerMask(
    val baseVisible: Boolean = true,
    val regions: List<LayerMaskRegion> = emptyList(),
    val enabled: Boolean = true,
    val linked: Boolean = true,
)

@Serializable
data class LayerMaskRegion(
    val points: List<StrokePoint>,
    val reveal: Boolean,
    val opacity: Float = 1f,
)

@Serializable
enum class LayerMaskCommand {
    CREATE_MASK_SELECTION,
    CREATE_MASK_OUTSIDE_SELECTION,
    HIDE_SELECTION,
    REVEAL_SELECTION,
    TOGGLE_ENABLED,
    TOGGLE_LINKED,
    APPLY,
    DELETE,
}

@Serializable
enum class FillSubTool { BUCKET, ENCLOSE_AND_FILL, LASSO_FILL, LEFTOVER_PEN }

fun defaultInkLayers(): List<InkLayer> = listOf(
    InkLayer(LayerIds.BACKGROUND, "Background", LayerRole.BACKGROUND, locked = true),
    InkLayer(LayerIds.INK, "Notes", LayerRole.INK),
)

@Serializable
sealed interface PageBackground {
    @Serializable @SerialName("paper")
    data class Paper(val colorArgb: Long = 0xFFFFFFFF, val pattern: PaperPattern = PaperPattern.PLAIN) : PageBackground
    @Serializable @SerialName("pdf")
    data class Pdf(val assetPath: String, val pageIndex: Int) : PageBackground
    @Serializable @SerialName("image")
    data class Image(val assetPath: String) : PageBackground
}

@Serializable
enum class PaperPattern { PLAIN, GRID, DOT, RULED, CORNELL }

@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val timeMs: Long = 0,
    val tilt: Float = 0f,
    val orientation: Float = 0f,
    val velocity: Float = 0f,
    /** Local pigment produced by Blend. Null inherits the stroke's base color. */
    val pigmentArgb: Long? = null,
)

@Serializable
data class BrushCurvePoint(val x: Float, val y: Float)

@Serializable
data class BrushSecondaryDynamics(
    val tiltEnabled: Boolean = false,
    val tiltMinimum: Float = 0f,
    val velocityEnabled: Boolean = false,
    val velocityMinimum: Float = 0f,
    val velocityCurve: List<BrushCurvePoint> = emptyList(),
    val randomEnabled: Boolean = false,
    val randomMinimum: Float = 0f,
)

/** Complete CSP dynamics for one brush parameter. Secondary brushes have their
 * own effectors and must not inherit the main tip's pressure/tilt/speed/random input. */
@Serializable
data class BrushParameterDynamics(
    val pressureEnabled: Boolean = false,
    val pressureMinimum: Float = 0f,
    val pressureCurve: List<BrushCurvePoint> = emptyList(),
    val secondary: BrushSecondaryDynamics = BrushSecondaryDynamics(),
)

@Serializable
data class BrushColorDynamics(
    val pressureEnabled: Boolean = false,
    val pressureMinimum: Float = 0f,
    val pressureCurve: List<BrushCurvePoint> = emptyList(),
    val tiltEnabled: Boolean = false,
    val tiltMinimum: Float = 0f,
    val velocityEnabled: Boolean = false,
    val velocityMinimum: Float = 0f,
    val velocityCurve: List<BrushCurvePoint> = emptyList(),
    val randomEnabled: Boolean = false,
    val randomMinimum: Float = -1f,
)

@Serializable
enum class BrushEngineKind {
    MESH_INK,
    STAMP_RASTER,
    AIRBRUSH,
    WET_SMUDGE,
    ERASE_MASK,
}

@Serializable
data class InkStroke(
    val id: String = UUID.randomUUID().toString(),
    val renderSeed: Int = id.hashCode(),
    val engineKind: BrushEngineKind = BrushEngineKind.MESH_INK,
    val masks: List<StrokeMaskDab> = emptyList(),
    val points: List<StrokePoint>,
    val colorArgb: Long = 0xFF111111,
    val width: Float = 5f,
    val opacity: Float = 1f,
    val brushId: String = "system-pencil",
    val layerId: String = LayerIds.INK,
    val tipAssetPath: String? = null,
    val tipAssetPaths: List<String> = emptyList(),
    val dualTipAssetPaths: List<String> = emptyList(),
    val grainAssetPath: String? = null,
    val grainDensity: Float = 0f,
    val grainDensityDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val grainScale: Float = 100f,
    val grainMovement: Float = 1f,
    val grainZoom: Float = 1f,
    val grainRotationDegrees: Float = 0f,
    val grainBrightness: Float = 0f,
    val grainContrast: Float = 0f,
    val grainReversed: Boolean = false,
    val grainEmphasized: Boolean = false,
    val grainOffsetJitter: Boolean = false,
    val grainCompositeMode: Int = CspTextureComposite.MULTIPLY,
    val spacing: Float = .12f,
    val spacingMode: Int = CspBrushGap.FIXED,
    val flow: Float = 1f,
    val hardness: Float = 1f,
    val antiAliasMode: Int = CspAntiAlias.MEDIUM,
    val blur: Float = 0f,
    val blurUnit: Int = 0,
    val blurLinkedToSize: Boolean = false,
    val thickness: Float = 1f,
    val thicknessVertical: Boolean = false,
    val rotationDegrees: Float = 0f,
    val followStylusOrientation: Boolean = false,
    val followStrokeDirection: Boolean = false,
    val randomRotationDegrees: Float = 0f,
    val strokeRotationJitterDegrees: Float = 0f,
    val stampCount: Int = 1,
    val stampCountJitter: Float = 0f,
    val continuousPlot: Boolean = false,
    val colorJitterHueDegrees: Float = 0f,
    val colorJitterSaturation: Float = 0f,
    val colorJitterValue: Float = 0f,
    val colorJitterTarget: Int = 0,
    val colorJitterHueDynamics: BrushColorDynamics = BrushColorDynamics(),
    val colorJitterSaturationDynamics: BrushColorDynamics = BrushColorDynamics(),
    val colorJitterValueDynamics: BrushColorDynamics = BrushColorDynamics(),
    val watercolor: Boolean = false,
    val colorMix: Float = 0f,
    val colorMixPressureEnabled: Boolean = false,
    val colorMixPressureMinimum: Float = 0f,
    val colorMixPressureCurve: List<BrushCurvePoint> = emptyList(),
    val colorMixSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val paintMix: Float = 0f,
    val paintMixPressureEnabled: Boolean = false,
    val paintMixPressureMinimum: Float = 0f,
    val paintMixPressureCurve: List<BrushCurvePoint> = emptyList(),
    val paintMixSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val spray: Boolean = false,
    val sprayParticleSize: Float = 100f,
    val sprayParticleSizeUnit: Int = 0,
    val sprayParticleSizeSyncBrushSize: Boolean = true,
    val sprayParticleSizePressureEnabled: Boolean = false,
    val sprayParticleSizePressureMinimum: Float = 0f,
    val sprayParticleSizePressureCurve: List<BrushCurvePoint> = emptyList(),
    val sprayParticleSizeSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val sprayDensity: Float = 1f,
    val sprayDensityPressureEnabled: Boolean = false,
    val sprayDensityPressureMinimum: Float = 0f,
    val sprayDensityPressureCurve: List<BrushCurvePoint> = emptyList(),
    val sprayDensitySecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val sprayBias: Float = 0f,
    val spraySpread: Float = 1f,
    val sprayParticleRotationDegrees: Float = 0f,
    val sprayParticleFollowStylusOrientation: Boolean = false,
    val sprayParticleFollowStrokeDirection: Boolean = false,
    val sprayParticleRandomRotationDegrees: Float = 0f,
    val ribbon: Boolean = false,
    val tipSequenceMode: Int = 0,
    val tipFlipHorizontalMode: Int = 0,
    val tipFlipVerticalMode: Int = 0,
    val blendTipsWithDarken: Boolean = false,
    val dualBrush: Boolean = false,
    val dualSizeScale: Float = 1f,
    val dualSizeDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val dualSpacing: Float = .12f,
    val dualSpacingMode: Int = CspBrushGap.FIXED,
    val dualSpacingDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val dualFlow: Float = 1f,
    val dualFlowDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val dualHardness: Float = 1f,
    val dualAntiAliasMode: Int = CspAntiAlias.MEDIUM,
    val dualThickness: Float = 1f,
    val dualThicknessDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val dualThicknessVertical: Boolean = false,
    val dualRotationDegrees: Float = 0f,
    val dualFollowStylusOrientation: Boolean = false,
    val dualFollowStrokeDirection: Boolean = false,
    val dualRandomRotationDegrees: Float = 0f,
    val dualStrokeRotationJitterDegrees: Float = 0f,
    val dualStampCount: Int = 1,
    val dualStampCountJitter: Float = 0f,
    val dualRibbon: Boolean = false,
    val dualSpray: Boolean = false,
    val dualSprayParticleSize: Float = 100f,
    val dualSprayParticleSizeUnit: Int = 0,
    val dualSprayParticleSizeSyncBrushSize: Boolean = true,
    val dualSprayParticleSizeDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val dualSprayDensity: Float = 1f,
    val dualSprayDensityDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val dualSprayBias: Float = 0f,
    val dualSpraySpread: Float = 1f,
    val dualSprayParticleRotationDegrees: Float = 0f,
    val dualSprayParticleFollowStylusOrientation: Boolean = false,
    val dualSprayParticleFollowStrokeDirection: Boolean = false,
    val dualSprayParticleRandomRotationDegrees: Float = 0f,
    val dualTipSequenceMode: Int = 0,
    val dualTipFlipHorizontalMode: Int = 0,
    val dualTipFlipVerticalMode: Int = 0,
    val dualBlendTipsWithDarken: Boolean = false,
    val dualCompositeMode: Int = 0,
    val dualApplyRgb: Boolean = false,
    val taperMode: BrushTaperMode = BrushTaperMode.PERCENTAGE,
    val taperStartEnabled: Boolean = false,
    val taperStartLength: Float = .2f,
    val taperStartLengthUnit: Int = 0,
    val taperStartMinimum: Float = .3f,
    val taperEndEnabled: Boolean = false,
    val taperEndLength: Float = .2f,
    val taperEndLengthUnit: Int = 0,
    val taperEndMinimum: Float = .3f,
    val waterEdgeEnabled: Boolean = false,
    val waterEdgeRadius: Float = 3f,
    val waterEdgeRadiusUnit: Int = 0,
    val waterEdgeOpacity: Float = .2f,
    val waterEdgeDarkness: Float = 0f,
    val waterEdgeBlur: Float = 0f,
    val waterEdgeBlurUnit: Int = 0,
    val sizePressureEnabled: Boolean = true,
    val sizePressureMinimum: Float = .35f,
    val sizePressureCurve: List<BrushCurvePoint> = emptyList(),
    val sizeSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val opacityPressureEnabled: Boolean = false,
    val opacityPressureMinimum: Float = 0f,
    val opacityPressureCurve: List<BrushCurvePoint> = emptyList(),
    val opacitySecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val flowPressureEnabled: Boolean = false,
    val flowPressureMinimum: Float = 0f,
    val flowPressureCurve: List<BrushCurvePoint> = emptyList(),
    val flowSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val spacingPressureEnabled: Boolean = false,
    val spacingPressureMinimum: Float = 0f,
    val spacingPressureCurve: List<BrushCurvePoint> = emptyList(),
    val spacingSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val thicknessPressureEnabled: Boolean = false,
    val thicknessPressureMinimum: Float = 0f,
    val thicknessPressureCurve: List<BrushCurvePoint> = emptyList(),
    val thicknessSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val blurPressureEnabled: Boolean = false,
    val blurPressureMinimum: Float = 0f,
    val blurPressureCurve: List<BrushCurvePoint> = emptyList(),
    val blurSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
)

@Serializable
data class StrokeMaskDab(
    val x: Float,
    val y: Float,
    val radiusX: Float,
    val radiusY: Float,
    val rotationDegrees: Float = 0f,
    val hardness: Float = 1f,
    val opacity: Float = 1f,
    val tipAssetPath: String? = null,
)

@Serializable
enum class BrushTaperMode { DISTANCE, PERCENTAGE, FADE }

@Serializable
sealed interface PageElement {
    val id: String
    val x: Float
    val y: Float
    val width: Float
    val height: Float
    val rotationDegrees: Float
    val opacity: Float
    val layerId: String

    @Serializable @SerialName("text")
    data class Text(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float = 600f, override val height: Float = 120f, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, val text: String, val colorArgb: Long = 0xFF111111, val textSize: Float = 42f, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val alignment: InkTextAlignment = InkTextAlignment.START, val listStyle: TextListStyle = TextListStyle.NONE, val lineSpacing: Float = 1.2f) : PageElement

    @Serializable @SerialName("sticky")
    data class Sticky(
        override val id: String = UUID.randomUUID().toString(),
        override val x: Float,
        override val y: Float,
        override val width: Float = 360f,
        override val height: Float = 260f,
        override val rotationDegrees: Float = 0f,
        override val opacity: Float = 1f,
        override val layerId: String = LayerIds.INK,
        val text: String,
        val colorArgb: Long = 0xFFE5E5E5,
        val collapsed: Boolean = false,
        val strokes: List<InkStroke> = emptyList(),
    ) : PageElement

    @Serializable @SerialName("shape")
    data class Shape(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float, override val height: Float, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, val kind: ShapeKind = ShapeKind.RECTANGLE, val colorArgb: Long = 0xFF111111, val fillArgb: Long? = null, val strokeWidth: Float = 5f, val path: List<ShapeVertex> = emptyList(), val gradientEndArgb: Long? = null, val gradientStartX: Float = 0f, val gradientStartY: Float = .5f, val gradientEndX: Float = 1f, val gradientEndY: Float = .5f, val gradientKind: GradientKind = GradientKind.LINEAR, val gradientEdgeMode: GradientEdgeMode = GradientEdgeMode.CLAMP, val gradientReverse: Boolean = false, val gradientDither: Boolean = true, val gradientEllipseRatio: Float = .6f, val gradientStops: List<GradientColorStop> = emptyList(), val maskAssetPath: String? = null) : PageElement

    @Serializable @SerialName("image")
    data class Image(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float, override val height: Float, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, val assetPath: String, val crop: ImageCrop = ImageCrop(), val mask: ImageMask = ImageMask.RECTANGLE, val cornerRadius: Float = 24f) : PageElement

    @Serializable @SerialName("fill")
    data class Fill(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float, override val height: Float, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, val colorArgb: Long = 0xFF111111, val maskAssetPath: String) : PageElement

    @Serializable @SerialName("audio_marker")
    data class AudioMarker(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float = 520f, override val height: Float = 150f, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, val assetPath: String, val title: String = "Audio note", val durationMs: Long, val createdAt: Long = System.currentTimeMillis()) : PageElement

    @Serializable @SerialName("link")
    data class Link(
        override val id: String = UUID.randomUUID().toString(),
        override val x: Float,
        override val y: Float,
        override val width: Float = 620f,
        override val height: Float = 150f,
        override val rotationDegrees: Float = 0f,
        override val opacity: Float = 1f,
        override val layerId: String = LayerIds.INK,
        val title: String,
        val url: String = "",
        val targetDocumentId: String? = null,
        val targetPageId: String? = null,
    ) : PageElement
}

@Serializable
enum class ShapeKind { RECTANGLE, ELLIPSE, TRIANGLE, DIAMOND, LINE, ARROW, FREEFORM }

@Serializable
data class ShapeVertex(val x: Float, val y: Float)

@Serializable
enum class InkTextAlignment { START, CENTER, END }

@Serializable
enum class TextListStyle { NONE, BULLET, NUMBERED, CHECKBOX }

@Serializable
enum class ImageMask { RECTANGLE, ROUNDED, OVAL }

@Serializable
data class ImageCrop(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    fun normalized(minimumSpan: Float = .05f): ImageCrop {
        val span = minimumSpan.coerceIn(.001f, 1f)
        val normalizedRight = right.coerceIn(span, 1f)
        val normalizedBottom = bottom.coerceIn(span, 1f)
        val normalizedLeft = left.coerceIn(0f, normalizedRight - span)
        val normalizedTop = top.coerceIn(0f, normalizedBottom - span)
        return ImageCrop(normalizedLeft, normalizedTop, normalizedRight, normalizedBottom)
    }

    fun pixelBounds(width: Int, height: Int): PixelBounds {
        val safe = normalized()
        val x = (safe.left * width).toInt().coerceIn(0, (width - 1).coerceAtLeast(0))
        val y = (safe.top * height).toInt().coerceIn(0, (height - 1).coerceAtLeast(0))
        val rightPixel = (safe.right * width).toInt().coerceIn(x + 1, width.coerceAtLeast(x + 1))
        val bottomPixel = (safe.bottom * height).toInt().coerceIn(y + 1, height.coerceAtLeast(y + 1))
        return PixelBounds(x, y, rightPixel - x, bottomPixel - y)
    }
}

data class PixelBounds(val x: Int, val y: Int, val width: Int, val height: Int)

@Serializable
data class InkFolder(val id: String = UUID.randomUUID().toString(), val name: String, val parentId: String? = null)

@Serializable
sealed interface JournalEntry {
    val documentId: String
    val pageId: String
    val timestamp: Long

    @Serializable @SerialName("stroke_added")
    data class StrokeAdded(override val documentId: String, override val pageId: String, val stroke: InkStroke, override val timestamp: Long = System.currentTimeMillis()) : JournalEntry
    @Serializable @SerialName("stroke_removed")
    data class StrokeRemoved(override val documentId: String, override val pageId: String, val strokeId: String, override val timestamp: Long = System.currentTimeMillis()) : JournalEntry
    @Serializable @SerialName("strokes_replaced")
    data class StrokesReplaced(
        override val documentId: String,
        override val pageId: String,
        val removedStrokeIds: List<String>,
        val strokes: List<InkStroke>,
        val replacementIndices: List<Int> = emptyList(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : JournalEntry
    @Serializable @SerialName("element_upserted")
    data class ElementUpserted(override val documentId: String, override val pageId: String, val element: PageElement, override val timestamp: Long = System.currentTimeMillis()) : JournalEntry
    @Serializable @SerialName("element_removed")
    data class ElementRemoved(override val documentId: String, override val pageId: String, val elementId: String, override val timestamp: Long = System.currentTimeMillis()) : JournalEntry
}
