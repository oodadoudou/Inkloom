@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package app.inkloom.document

import kotlinx.serialization.EncodeDefault
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
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val createdAt: Long = System.currentTimeMillis(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val updatedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val archivedAt: Long? = null,
    val trashedAt: Long? = null,
    val folderTrashBatchId: String? = null,
    val pages: List<InkPage> = listOf(InkPage()),
)

@Serializable
enum class CoverStyle {
    MINIMAL,
    DARK,
    PAPER,
    GRID,
    SOLID,
    BLACK,
    WHITE,
    BLACK_BAND,
    WHITE_GRID,
    CUSTOM_IMAGE,
}

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
    val dpi: Float = 300f,
    val colorManagement: app.inkloom.color.PageColorManagement = app.inkloom.color.PageColorManagement(),
    val backgroundFrame: PageBackgroundFrame? = null,
    val backgroundSourceCrop: PageCrop? = null,
    val background: PageBackground = PageBackground.Paper(),
    val backgroundRotationQuarterTurns: Int = 0,
    val crop: PageCrop = PageCrop(),
    val layers: List<InkLayer> = defaultInkLayers(),
    val elements: List<PageElement> = emptyList(),
    val bookmarked: Boolean = false,
    val pdfTextBlocks: List<PdfTextBlock> = emptyList(),
    val pdfTextIndexVersion: Int = 0,
    val pdfGotoLinks: List<PdfGotoLink> = emptyList(),
    val pdfLinkIndexVersion: Int = 0,
    val recognizedTextBlocks: List<RecognizedTextBlock> = emptyList(),
    val savedSelections: List<SavedSelection> = emptyList(),
    /** Visibility before the current solo preview; keyed by layer ID so reorder is harmless. */
    val soloVisibilityBefore: Map<String, Boolean> = emptyMap(),
    val soloLayerId: String? = null,
    val handwritingIndexFingerprint: String = "",
    val imageOcrFingerprint: String = "",
)

@Serializable
data class SavedSelection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val surface: MaskTileSurfaceRef,
    val rotationQuarterTurns: Int = 0,
    val crop: PageCrop = PageCrop(),
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
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val updatedAt: Long = System.currentTimeMillis(),
)

object LayerIds {
    const val BACKGROUND = "background"
    const val INK = "ink"
    const val HIGHLIGHT = "highlight"
    const val OBJECTS = "objects"
}

@Serializable
enum class LayerRole { BACKGROUND, INK, HIGHLIGHT, OBJECTS, CUSTOM, FOLDER, ADJUSTMENT }

@Serializable
enum class LayerBlendMode {
    NORMAL, MULTIPLY, SCREEN, ADD,
    OVERLAY, SOFT_LIGHT, HARD_LIGHT, COLOR_DODGE, COLOR_BURN,
    LIGHTEN, DARKEN, DIFFERENCE, EXCLUSION,
    HUE, SATURATION, COLOR, LUMINOSITY,
    LINEAR_BURN, SUBTRACT, GLOW_DODGE, ADD_GLOW, VIVID_LIGHT, LINEAR_LIGHT, PIN_LIGHT, HARD_MIX, DARKER_COLOR, LIGHTER_COLOR, DIVIDE;
}

/** Optional visual marker shown in the layer palette; it does not affect compositing. */
@Serializable
enum class LayerPaletteColor { NONE, GRAY, RED, ORANGE, YELLOW, GREEN, BLUE, VIOLET }

/** CSP-style display replacement: black maps to dark, white maps to light; source pixels remain intact. */
@Serializable
data class LayerColorReplacement(
    val darkArgb: Long = 0xff000000L,
    val lightArgb: Long = 0xffffffffL,
)

/**
 * Durable reference to one immutable sparse pixel-surface generation.
 *
 * The manifest is authoritative. The duplicated geometry and revision let document loading reject
 * a mismatched or stale manifest before it is attached to a page. Tile blobs are traced through the
 * verified manifest rather than being copied into the document model.
 */
@Serializable
data class RasterTileSurfaceRef(
    val manifestAssetPath: String,
    val surfaceId: String,
    val logicalWidth: Int,
    val logicalHeight: Int,
    val tileSize: Int,
    val revision: Long,
    /** Stable engine manifest identifier; defaults keep documents written before schema v2 readable. */
    val pixelFormat: String = "argb8888-premultiplied-srgb",
)

@Serializable
data class MaskTileSurfaceRef(
    val assetPath: String,
    val surfaceId: String,
    val logicalWidth: Int,
    val logicalHeight: Int,
    val tileSize: Int,
    val revision: Long,
    val defaultAlpha: Int,
    val tileCount: Int,
)

@Serializable
data class InkLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: LayerRole = LayerRole.CUSTOM,
    /** Authoritative sparse pixel surface for V5 paint layers. */
    val rasterTileSurface: RasterTileSurfaceRef? = null,
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
    /** When any page layer opts in, guides constrain only layers carrying this flag. */
    val drawingAssist: Boolean = false,
    /** Layer-owned ruler geometry. Null preserves the legacy page-wide guide settings. */
    val drawingGuide: LayerDrawingGuide? = null,
    val mask: LayerMask? = null,
    val parentId: String? = null,
    val collapsed: Boolean = false,
    /** Folder-only: child blending participates in the parent backdrop. */
    val passThrough: Boolean = false,
    val paletteColor: LayerPaletteColor = LayerPaletteColor.NONE,
    val colorReplacement: LayerColorReplacement? = null,
    /** Non-destructive color operation applied to the composited siblings below this layer. */
    val adjustment: LayerAdjustment? = null,
    /**
     * Fill, gradient and tone layers: the pixels are rendered from this setting and re-rendered
     * when it is edited. Brushes do not paint on these layers; rasterizing drops the setting.
     */
    val generator: LayerGenerator? = null,
    /**
     * Vector layers: the lines behind the pixels. Strokes and the vector eraser edit the lines and
     * re-render the pixels; tools that only change pixels refuse the layer until it is rasterized.
     */
    val vector: VectorLayerContent? = null,
    /** CSP rulers kept on this layer, with their display range and links. */
    val rulers: LayerRulers? = null,
    /** Folders only: a frame border folder's frames; the folder's mask is rendered from them. */
    val frame: FrameBorder? = null,
    /** The locked layer holding the frame lines of the frame border folder with this ID. */
    val frameBorderOf: String? = null,
    /** 3D layers: the posed drawing figure the pixels are rendered from. */
    val figure: DrawingFigure? = null,
    /** Folders only: an animation folder's timeline of cels. */
    val animation: AnimationFolder? = null,
)

/** Where a file object's image comes from, and the state of that file when it was last read. */
@Serializable
data class FileObjectSource(val uri: String, val name: String, val modified: Long = 0L, val size: Long = -1L)

/** CSP's fill, gradient and tone layers. */
@Serializable
sealed interface LayerGenerator {
    @Serializable @SerialName("fill")
    data class Fill(val colorArgb: Long = 0xFF808080) : LayerGenerator
    /** Start and end are fractions of the page; the definition carries shape, stops and edges. */
    @Serializable @SerialName("gradient")
    data class Gradient(val definition: GradientDefinition, val startX: Float = .5f, val startY: Float = 0f,
        val endX: Float = .5f, val endY: Float = 1f) : LayerGenerator
    /** Halftone screen: lines per inch at the page DPI, dot coverage 0..1, screen angle. */
    @Serializable @SerialName("tone")
    data class Tone(val linesPerInch: Float = 60f, val density: Float = .3f, val dot: ToneDotShape = ToneDotShape.CIRCLE,
        val angleDegrees: Float = 45f, val colorArgb: Long = 0xFF000000) : LayerGenerator
}

@Serializable
enum class ToneDotShape { CIRCLE, SQUARE, DIAMOND, LINE, CROSS }

/** Serializable snapshot of the guides attached to one paint layer.
 * Enum names are stored as strings so documents remain readable when guide modes expand. */
@Serializable
data class LayerDrawingGuide(
    val symmetryMode: String = "NONE",
    val symmetryCenterX: Float = .5f,
    val symmetryCenterY: Float = .5f,
    val symmetryAngleDegrees: Float = 0f,
    val rulerEnabled: Boolean = false,
    val rulerCenterX: Float = .5f,
    val rulerCenterY: Float = .5f,
    val rulerAngleDegrees: Float = 0f,
    val rulerSnapDegrees: Float = 15f,
    val gridSnapEnabled: Boolean = false,
    val gridSpacing: Float = 64f,
    val alignmentGuidesEnabled: Boolean = false,
    val verticalGuideX: Float = .5f,
    val horizontalGuideY: Float = .5f,
    val perspectiveMode: String = "NONE",
    val perspectiveAssisted: Boolean = true,
    val perspectiveEditing: Boolean = false,
    val perspectivePoint1X: Float = .5f,
    val perspectivePoint1Y: Float = .42f,
    val perspectivePoint2X: Float = .88f,
    val perspectivePoint2Y: Float = .42f,
    val perspectivePoint3X: Float = .5f,
    val perspectivePoint3Y: Float = .12f,
    val specialRulerMode: String = "NONE",
    val specialRulerAssisted: Boolean = true,
    val specialRulerEditing: Boolean = false,
    val specialRulerCenterX: Float = .5f,
    val specialRulerCenterY: Float = .5f,
    val specialRulerAngleDegrees: Float = 0f,
    val specialRulerSpacing: Float = 64f,
)

/** V5 has one paint-layer truth. Backgrounds and folders are structural, all other layers own pixels. */
fun InkLayer.isPaintSurfaceLayer(): Boolean = role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER, LayerRole.ADJUSTMENT)

/** Pixel layers and adjustment layers can be limited to the clipping base below them. */
fun InkLayer.canClipToBelow(): Boolean = isPaintSurfaceLayer() || role == LayerRole.ADJUSTMENT

@Serializable
enum class LayerAdjustmentKind {
    BRIGHTNESS_CONTRAST,
    HUE_SATURATION,
    LEVELS,
    CURVES,
    COLOR_BALANCE,
    GRADIENT_MAP,
    POSTERIZE,
    INVERT,
    THRESHOLD,
}

@Serializable
data class AdjustmentCurvePoint(val input: Float, val output: Float)

@Serializable
data class AdjustmentLevels(
    val inputBlack: Float = 0f,
    val inputGamma: Float = 1f,
    val inputWhite: Float = 1f,
    val outputBlack: Float = 0f,
    val outputWhite: Float = 1f,
)

@Serializable
data class LayerAdjustment(
    val kind: LayerAdjustmentKind = LayerAdjustmentKind.BRIGHTNESS_CONTRAST,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val hueDegrees: Float = 0f,
    val saturation: Float = 0f,
    val luminosity: Float = 0f,
    val posterizeLevels: Int = 8,
    val threshold: Float = .5f,
    val inputBlack: Float = 0f,
    val inputGamma: Float = 1f,
    val inputWhite: Float = 1f,
    val outputBlack: Float = 0f,
    val outputWhite: Float = 1f,
    val redLevels: AdjustmentLevels = AdjustmentLevels(),
    val greenLevels: AdjustmentLevels = AdjustmentLevels(),
    val blueLevels: AdjustmentLevels = AdjustmentLevels(),
    val curve: List<AdjustmentCurvePoint> = listOf(AdjustmentCurvePoint(0f, 0f), AdjustmentCurvePoint(1f, 1f)),
    val redCurve: List<AdjustmentCurvePoint> = listOf(AdjustmentCurvePoint(0f, 0f), AdjustmentCurvePoint(1f, 1f)),
    val greenCurve: List<AdjustmentCurvePoint> = listOf(AdjustmentCurvePoint(0f, 0f), AdjustmentCurvePoint(1f, 1f)),
    val blueCurve: List<AdjustmentCurvePoint> = listOf(AdjustmentCurvePoint(0f, 0f), AdjustmentCurvePoint(1f, 1f)),
    val curveSmooth: Boolean = false,
    val redCurveSmooth: Boolean = false,
    val greenCurveSmooth: Boolean = false,
    val blueCurveSmooth: Boolean = false,
    val shadowsRed: Float = 0f,
    val shadowsGreen: Float = 0f,
    val shadowsBlue: Float = 0f,
    val midtonesRed: Float = 0f,
    val midtonesGreen: Float = 0f,
    val midtonesBlue: Float = 0f,
    val highlightsRed: Float = 0f,
    val highlightsGreen: Float = 0f,
    val highlightsBlue: Float = 0f,
    val gradient: List<GradientColorStop> = listOf(GradientColorStop(0f, 0xFF000000L), GradientColorStop(1f, 0xFFFFFFFFL)),
)

@Serializable
data class LayerMask(
    val baseVisible: Boolean = true,
    val enabled: Boolean = true,
    val linked: Boolean = true,
    /** Soft alpha by default; disabling it displays thresholded but retains editable source alpha. */
    val showGradients: Boolean = true,
    val expressionThreshold: Int = 128,
    /** Authoritative V5 sparse alpha surface. */
    val surface: MaskTileSurfaceRef? = null,
)

@Serializable
enum class LayerMaskCommand {
    EDIT,
    REPLACE_SURFACE,
    FILL_WHITE,
    FILL_BLACK,
    CREATE_MASK_SELECTION,
    CREATE_MASK_OUTSIDE_SELECTION,
    HIDE_SELECTION,
    REVEAL_SELECTION,
    TOGGLE_ENABLED,
    TOGGLE_LINKED,
    TOGGLE_OVERLAY,
    INVERT,
    LOAD_AS_SELECTION,
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
    data class Paper(val colorArgb: Long = 0xFFFFFFFF, val pattern: PaperPattern = PaperPattern.PLAIN, val density: PaperDensity = PaperDensity.DENSE) : PageBackground
    @Serializable @SerialName("pdf")
    data class Pdf(val assetPath: String, val pageIndex: Int) : PageBackground
    @Serializable @SerialName("image")
    data class Image(
        val assetPath: String,
        val paperAspectRatio: Float = 0f,
        val cropToFill: Boolean = false,
        val paperColorArgb: Long = 0xFFFFFFFF,
    ) : PageBackground
}

@Serializable
enum class PaperDensity(val spacingScale: Float) { DENSE(1f), MEDIUM(1.7f), SPACIOUS(2.5f) }

@Serializable
enum class PaperPattern { PLAIN, GRID, DOT, RULED, CORNELL }

/** Bitmask describing which brush-input sensors are currently established for a sample. The first
 * modeled sample of a stroke has no history: velocity is 0 by construction and tilt/direction are
 * often device defaults, so they are NOT_YET_ESTABLISHED and brush dynamics treat them as neutral
 * instead of passing a numeric zero through a response curve as an extreme value. */
object BrushSensorFlags {
    const val VELOCITY = 1
    const val TILT = 2
    const val ORIENTATION = 4
    const val ALL = VELOCITY or TILT or ORIENTATION
}

@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val timeMs: Long = 0,
    val tilt: Float = 0f,
    val orientation: Float = 0f,
    val velocity: Float = 0f,
    /** Cumulative page-space travel normalized to the 1600 px brush reference width. */
    val pathDistance: Float = 0f,
    /** Local pigment produced by Blend. Null inherits the stroke's base color. */
    val pigmentArgb: Long? = null,
    /** Established brush-input sensors; see [BrushSensorFlags]. Defaults to all-established so
     * imported/replayed points behave as real samples; the live input modeler clears the flags on
     * the first sample only. */
    val sensorFlags: Int = BrushSensorFlags.ALL,
) {
    fun hasSensor(flag: Int): Boolean = sensorFlags and flag != 0
}

@Serializable
data class BrushCurvePoint(val x: Float, val y: Float)

@Serializable
data class BrushSecondaryDynamics(
    val tiltEnabled: Boolean = false,
    val tiltMinimum: Float = 0f,
    /** Normalized stylus tilt at which this parameter begins responding. */
    val tiltStart: Float = 0f,
    /** Optional response curve; empty retains the legacy increasing response. */
    val tiltCurve: List<BrushCurvePoint> = emptyList(),
    val velocityEnabled: Boolean = false,
    val velocityMinimum: Float = 0f,
    /** CSP's velocity multiplier. A value of 1 preserves the normalized input. */
    val velocityScale: Float = 1f,
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
    /** Normalized stylus tilt at which this parameter begins responding. */
    val tiltStart: Float = 0f,
    val velocityEnabled: Boolean = false,
    val velocityMinimum: Float = 0f,
    /** CSP's velocity multiplier. A value of 1 preserves the normalized input. */
    val velocityScale: Float = 1f,
    val velocityCurve: List<BrushCurvePoint> = emptyList(),
    val randomEnabled: Boolean = false,
    val randomMinimum: Float = -1f,
)

/** Procreate keeps stamp, stroke, pressure, and tilt color programs independent. */
@Serializable
data class ProcreateColorDynamics(
    val stampHue: Float = 0f,
    val stampSaturation: Float = 0f,
    val stampLightness: Float = 0f,
    val stampDarkness: Float = 0f,
    val stampSecondary: Float = 0f,
    val strokeHue: Float = 0f,
    val strokeSaturation: Float = 0f,
    val strokeLightness: Float = 0f,
    val strokeDarkness: Float = 0f,
    val strokeSecondary: Float = 0f,
    val pressureHue: Float = 0f,
    val pressureHueCurve: List<BrushCurvePoint> = emptyList(),
    val pressureSaturation: Float = 0f,
    val pressureSaturationCurve: List<BrushCurvePoint> = emptyList(),
    val pressureBrightness: Float = 0f,
    val pressureBrightnessCurve: List<BrushCurvePoint> = emptyList(),
    val pressureSecondary: Float = 0f,
    val pressureSecondaryCurve: List<BrushCurvePoint> = emptyList(),
    val tiltHue: Float = 0f,
    val tiltSaturation: Float = 0f,
    val tiltBrightness: Float = 0f,
    val tiltSecondary: Float = 0f,
    val tiltStart: Float = 0f,
) {
    fun isDisabled(): Boolean =
        stampHue == 0f && stampSaturation == 0f && stampLightness == 0f && stampDarkness == 0f && stampSecondary == 0f &&
            strokeHue == 0f && strokeSaturation == 0f && strokeLightness == 0f && strokeDarkness == 0f && strokeSecondary == 0f &&
            pressureHue == 0f && pressureSaturation == 0f && pressureBrightness == 0f && pressureSecondary == 0f &&
            tiltHue == 0f && tiltSaturation == 0f && tiltBrightness == 0f && tiltSecondary == 0f
}

/** Procreate Brush Studio > Wet Mix values retained independently from CSP color mixing.
 * Values use the normalized archive range. Grade retains the archive's signed authored direction;
 * [legacyMixSoftening] preserves the older scalar without folding it into Grade. */
@Serializable
data class ProcreateWetMix(
    val dilution: Float = 0f,
    val charge: Float = 0f,
    val attack: Float = 0f,
    val pull: Float = 0f,
    val grade: Float = 0f,
    val blur: Float = 0f,
    val blurJitter: Float = 0f,
    val wetnessJitter: Float = 0f,
    val legacyMixSoftening: Float = 0f,
) {
    fun isDisabled(): Boolean =
        dilution == 0f && charge == 0f && attack == 0f && pull == 0f && grade == 0f &&
            blur == 0f && blurJitter == 0f && wetnessJitter == 0f && legacyMixSoftening == 0f
}

/** Procreate Brush Studio > Taper. Pressure and Touch are separate authored programs; collapsing
 * them loses finger drawing, opacity taper, tip shape, and the shared pressure response. */
@Serializable
data class ProcreateTaper(
    val pressureStartLength: Float = 0f,
    val pressureEndLength: Float = 0f,
    val pressureSize: Float = 0f,
    val pressureOpacity: Float = 0f,
    val pressureTip: Float = 0f,
    val touchStartLength: Float = 0f,
    val touchEndLength: Float = 0f,
    val touchSize: Float = 0f,
    val touchOpacity: Float = 0f,
    val touchTip: Float = 0f,
    val pressureResponse: Float = 0f,
    val pressureLinked: Boolean = false,
    val touchLinked: Boolean = false,
    val classic: Boolean = false,
) {
    fun isDisabled(): Boolean =
        (pressureStartLength == 0f && pressureEndLength == 0f || pressureSize == 0f && pressureOpacity == 0f) &&
            (touchStartLength == 0f && touchEndLength == 0f || touchSize == 0f && touchOpacity == 0f)
}

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
    val deformation: ElementDeformation?

    @Serializable @SerialName("text")
    data class Text(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float = 600f, override val height: Float = 120f, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, override val deformation: ElementDeformation? = null, val text: String, val colorArgb: Long = 0xFF111111, val textSize: Float = 42f, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val alignment: InkTextAlignment = InkTextAlignment.START, val listStyle: TextListStyle = TextListStyle.NONE, val lineSpacing: Float = 1.2f) : PageElement

    @Serializable @SerialName("sticky")
    data class Sticky(
        override val id: String = UUID.randomUUID().toString(),
        override val x: Float,
        override val y: Float,
        override val width: Float = 360f,
        override val height: Float = 260f,
        override val rotationDegrees: Float = 0f,
        override val opacity: Float = 1f,
        override val layerId: String = LayerIds.INK, override val deformation: ElementDeformation? = null,
        val text: String,
        val colorArgb: Long = 0xFFE5E5E5,
        val collapsed: Boolean = false,
    ) : PageElement

    @Serializable @SerialName("shape")
    data class Shape(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float, override val height: Float, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, override val deformation: ElementDeformation? = null, val kind: ShapeKind = ShapeKind.RECTANGLE, val colorArgb: Long = 0xFF111111, val fillArgb: Long? = null, val strokeWidth: Float = 5f, val path: List<ShapeVertex> = emptyList(), val gradientEndArgb: Long? = null, val gradientStartX: Float = 0f, val gradientStartY: Float = .5f, val gradientEndX: Float = 1f, val gradientEndY: Float = .5f, val gradientKind: GradientKind = GradientKind.LINEAR, val gradientEdgeMode: GradientEdgeMode = GradientEdgeMode.CLAMP, val gradientReverse: Boolean = false, val gradientDither: Boolean = true, val gradientEllipseRatio: Float = .6f, val gradientStops: List<GradientColorStop> = emptyList(), val maskAssetPath: String? = null) : PageElement

    @Serializable @SerialName("image")
    data class Image(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float, override val height: Float, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, override val deformation: ElementDeformation? = null, val assetPath: String, val crop: ImageCrop = ImageCrop(), val mask: ImageMask = ImageMask.RECTANGLE, val cornerRadius: Float = 24f,
        /** CSP file object: the linked file this image was last read from; null for a plain imported image. */
        val source: FileObjectSource? = null) : PageElement

    @Serializable @SerialName("fill")
    data class Fill(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float, override val height: Float, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, override val deformation: ElementDeformation? = null, val colorArgb: Long = 0xFF111111, val maskAssetPath: String) : PageElement

    @Serializable @SerialName("link")
    data class Link(
        override val id: String = UUID.randomUUID().toString(),
        override val x: Float,
        override val y: Float,
        override val width: Float = 620f,
        override val height: Float = 150f,
        override val rotationDegrees: Float = 0f,
        override val opacity: Float = 1f,
        override val layerId: String = LayerIds.INK, override val deformation: ElementDeformation? = null,
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
data class InkFolder(val id: String = UUID.randomUUID().toString(), val name: String, val parentId: String? = null,
    val trashedAt: Long? = null, val trashBatchId: String? = null)
