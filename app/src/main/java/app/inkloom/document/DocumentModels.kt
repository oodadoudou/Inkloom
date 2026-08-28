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
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val updatedAt: Long = System.currentTimeMillis(),
)

object LayerIds {
    const val BACKGROUND = "background"
    const val INK = "ink"
    const val HIGHLIGHT = "highlight"
    const val OBJECTS = "objects"
}

@Serializable
enum class LayerRole { BACKGROUND, INK, HIGHLIGHT, OBJECTS, CUSTOM, FOLDER }

@Serializable
enum class LayerBlendMode {
    NORMAL, MULTIPLY, SCREEN, ADD,
    OVERLAY, SOFT_LIGHT, HARD_LIGHT, COLOR_DODGE, COLOR_BURN,
    LIGHTEN, DARKEN, DIFFERENCE, EXCLUSION,
    HUE, SATURATION, COLOR, LUMINOSITY;
}

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
    /** Legacy content-addressed full-page PNG. Never written after [rasterTileSurface] is attached. */
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

/** V5 has one paint-layer truth. Backgrounds and folders are structural, all other layers own pixels. */
fun InkLayer.isPaintSurfaceLayer(): Boolean = role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)

@Serializable
data class LayerMask(
    val baseVisible: Boolean = true,
    val regions: List<LayerMaskRegion> = emptyList(),
    val enabled: Boolean = true,
    val linked: Boolean = true,
    /** Authoritative V3 alpha. [regions] is a read-only legacy import/render fallback and no
     * production command writes it. */
    val surface: MaskTileSurfaceRef? = null,
)

@Serializable
data class LayerMaskRegion(
    val points: List<StrokePoint>,
    val reveal: Boolean,
    val opacity: Float = 1f,
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
    data class Paper(val colorArgb: Long = 0xFFFFFFFF, val pattern: PaperPattern = PaperPattern.PLAIN) : PageBackground
    @Serializable @SerialName("pdf")
    data class Pdf(val assetPath: String, val pageIndex: Int) : PageBackground
    @Serializable @SerialName("image")
    data class Image(val assetPath: String) : PageBackground
}

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
    ) : PageElement

    @Serializable @SerialName("shape")
    data class Shape(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float, override val height: Float, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, val kind: ShapeKind = ShapeKind.RECTANGLE, val colorArgb: Long = 0xFF111111, val fillArgb: Long? = null, val strokeWidth: Float = 5f, val path: List<ShapeVertex> = emptyList(), val gradientEndArgb: Long? = null, val gradientStartX: Float = 0f, val gradientStartY: Float = .5f, val gradientEndX: Float = 1f, val gradientEndY: Float = .5f, val gradientKind: GradientKind = GradientKind.LINEAR, val gradientEdgeMode: GradientEdgeMode = GradientEdgeMode.CLAMP, val gradientReverse: Boolean = false, val gradientDither: Boolean = true, val gradientEllipseRatio: Float = .6f, val gradientStops: List<GradientColorStop> = emptyList(), val maskAssetPath: String? = null) : PageElement

    @Serializable @SerialName("image")
    data class Image(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float, override val height: Float, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, val assetPath: String, val crop: ImageCrop = ImageCrop(), val mask: ImageMask = ImageMask.RECTANGLE, val cornerRadius: Float = 24f) : PageElement

    @Serializable @SerialName("fill")
    data class Fill(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float, override val height: Float, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, val colorArgb: Long = 0xFF111111, val maskAssetPath: String) : PageElement

    @Serializable @SerialName("audio_marker")
    data class AudioMarker(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float = 520f, override val height: Float = 150f, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.INK, val assetPath: String, val title: String = "Audio note", val durationMs: Long, @EncodeDefault(EncodeDefault.Mode.ALWAYS) val createdAt: Long = System.currentTimeMillis()) : PageElement

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
