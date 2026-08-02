package app.inkloom.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class InkDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val folderId: String? = null,
    val coverArgb: Long = 0xFFA9C5B9,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val archivedAt: Long? = null,
    val trashedAt: Long? = null,
    val pages: List<InkPage> = listOf(InkPage()),
)

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
)

@Serializable
data class PageCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
)

object LayerIds {
    const val BACKGROUND = "background"
    const val INK = "ink"
    const val HIGHLIGHT = "highlight"
    const val OBJECTS = "objects"
}

@Serializable
enum class LayerRole { BACKGROUND, INK, HIGHLIGHT, OBJECTS, CUSTOM }

@Serializable
data class InkLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: LayerRole = LayerRole.CUSTOM,
    val visible: Boolean = true,
    val locked: Boolean = false,
)

fun defaultInkLayers(): List<InkLayer> = listOf(
    InkLayer(LayerIds.BACKGROUND, "Background", LayerRole.BACKGROUND, locked = true),
    InkLayer(LayerIds.INK, "Notes", LayerRole.INK),
    InkLayer(LayerIds.HIGHLIGHT, "Highlights", LayerRole.HIGHLIGHT),
    InkLayer(LayerIds.OBJECTS, "Objects", LayerRole.OBJECTS),
)

@Serializable
sealed interface PageBackground {
    @Serializable @SerialName("paper")
    data class Paper(val colorArgb: Long = 0xFFF7F3EA, val pattern: PaperPattern = PaperPattern.PLAIN) : PageBackground
    @Serializable @SerialName("pdf")
    data class Pdf(val assetPath: String, val pageIndex: Int) : PageBackground
    @Serializable @SerialName("image")
    data class Image(val assetPath: String) : PageBackground
}

@Serializable
enum class PaperPattern { PLAIN, GRID, DOT, RULED }

@Serializable
data class StrokePoint(val x: Float, val y: Float, val pressure: Float = 1f, val timeMs: Long = 0, val tilt: Float = 0f, val orientation: Float = 0f, val velocity: Float = 0f)

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

@Serializable
data class InkStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val colorArgb: Long = 0xFF303D38,
    val width: Float = 5f,
    val opacity: Float = 1f,
    val brushId: String = "system-pencil",
    val layerId: String = LayerIds.INK,
    val tipAssetPath: String? = null,
    val tipAssetPaths: List<String> = emptyList(),
    val dualTipAssetPaths: List<String> = emptyList(),
    val grainAssetPath: String? = null,
    val grainDensity: Float = 0f,
    val grainScale: Float = 100f,
    val grainRotationDegrees: Float = 0f,
    val grainBrightness: Float = 0f,
    val grainContrast: Float = 0f,
    val grainReversed: Boolean = false,
    val grainEmphasized: Boolean = false,
    val spacing: Float = .12f,
    val flow: Float = 1f,
    val hardness: Float = 1f,
    val thickness: Float = 1f,
    val thicknessVertical: Boolean = false,
    val rotationDegrees: Float = 0f,
    val followStylusOrientation: Boolean = false,
    val followStrokeDirection: Boolean = false,
    val randomRotationDegrees: Float = 0f,
    val watercolor: Boolean = false,
    val colorMix: Float = 0f,
    val paintMix: Float = 0f,
    val spray: Boolean = false,
    val sprayParticleSize: Float = 100f,
    val sprayParticleSizeUnit: Int = 0,
    val sprayParticleSizeSyncBrushSize: Boolean = true,
    val sprayDensity: Float = 1f,
    val sprayBias: Float = 0f,
    val ribbon: Boolean = false,
    val tipSequenceMode: Int = 0,
    val dualBrush: Boolean = false,
    val dualSizeScale: Float = 1f,
    val dualSpacing: Float = .12f,
    val dualFlow: Float = 1f,
    val dualHardness: Float = 1f,
    val dualThickness: Float = 1f,
    val dualThicknessVertical: Boolean = false,
    val dualRotationDegrees: Float = 0f,
    val dualFollowStylusOrientation: Boolean = false,
    val dualFollowStrokeDirection: Boolean = false,
    val dualRandomRotationDegrees: Float = 0f,
    val dualRibbon: Boolean = false,
    val dualSpray: Boolean = false,
    val dualSprayParticleSize: Float = 100f,
    val dualSprayParticleSizeUnit: Int = 0,
    val dualSprayParticleSizeSyncBrushSize: Boolean = true,
    val dualSprayDensity: Float = 1f,
    val dualSprayBias: Float = 0f,
    val dualTipSequenceMode: Int = 0,
    val dualCompositeMode: Int = 0,
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
    data class Text(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float = 600f, override val height: Float = 120f, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.OBJECTS, val text: String, val colorArgb: Long = 0xFF303D38, val textSize: Float = 42f, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val alignment: InkTextAlignment = InkTextAlignment.START, val listStyle: TextListStyle = TextListStyle.NONE, val lineSpacing: Float = 1.2f) : PageElement

    @Serializable @SerialName("sticky")
    data class Sticky(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float = 360f, override val height: Float = 260f, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.OBJECTS, val text: String, val colorArgb: Long = 0xFFFFE7A8, val collapsed: Boolean = false) : PageElement

    @Serializable @SerialName("shape")
    data class Shape(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float, override val height: Float, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.OBJECTS, val kind: ShapeKind = ShapeKind.RECTANGLE, val colorArgb: Long = 0xFF303D38, val fillArgb: Long? = null, val strokeWidth: Float = 5f) : PageElement

    @Serializable @SerialName("image")
    data class Image(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float, override val height: Float, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.OBJECTS, val assetPath: String, val crop: ImageCrop = ImageCrop(), val mask: ImageMask = ImageMask.RECTANGLE, val cornerRadius: Float = 24f) : PageElement

    @Serializable @SerialName("audio_marker")
    data class AudioMarker(override val id: String = UUID.randomUUID().toString(), override val x: Float, override val y: Float, override val width: Float = 520f, override val height: Float = 150f, override val rotationDegrees: Float = 0f, override val opacity: Float = 1f, override val layerId: String = LayerIds.OBJECTS, val assetPath: String, val title: String = "Audio note", val durationMs: Long, val createdAt: Long = System.currentTimeMillis()) : PageElement
}

@Serializable
enum class ShapeKind { RECTANGLE, ELLIPSE, TRIANGLE, DIAMOND, LINE, ARROW }

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
data class LibraryIndex(val folders: List<InkFolder> = emptyList(), val documentIds: List<String> = emptyList())

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
    data class StrokesReplaced(override val documentId: String, override val pageId: String, val removedStrokeIds: List<String>, val strokes: List<InkStroke>, override val timestamp: Long = System.currentTimeMillis()) : JournalEntry
    @Serializable @SerialName("element_upserted")
    data class ElementUpserted(override val documentId: String, override val pageId: String, val element: PageElement, override val timestamp: Long = System.currentTimeMillis()) : JournalEntry
    @Serializable @SerialName("element_removed")
    data class ElementRemoved(override val documentId: String, override val pageId: String, val elementId: String, override val timestamp: Long = System.currentTimeMillis()) : JournalEntry
}
