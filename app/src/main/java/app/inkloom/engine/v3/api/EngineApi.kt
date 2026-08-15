package app.inkloom.engine.v3.api

/** The single application-facing surface of Engine V3. Implementation packages stay replaceable
 * without allowing UI code to couple itself to renderer, scene, input or tool internals. */
internal typealias NativeStrokeSession = app.inkloom.engine.v3.input.NativeStrokeSession
internal typealias PointerPacketMapper = app.inkloom.engine.v3.input.PointerPacketMapper
internal typealias ViewportFrame = app.inkloom.engine.v3.geometry.ViewportFrame

internal typealias BrushProgram = app.inkloom.engine.v3.brush.BrushProgram
internal typealias BrushProgramRuntime = app.inkloom.engine.v3.brush.BrushProgramRuntime
internal typealias BrushResourceCache = app.inkloom.engine.v3.brush.BrushResourceCache
internal typealias BrushTipRequest = app.inkloom.engine.v3.brush.BrushTipRequest
internal typealias ProceduralBrushTip = app.inkloom.engine.v3.brush.ProceduralBrushTip

internal typealias ActiveStrokeCompositor = app.inkloom.engine.v3.render.ActiveStrokeCompositor
internal typealias ActiveStrokeTailPlanner = app.inkloom.engine.v3.render.ActiveStrokeTailPlanner
internal typealias BrushProgramBatchRenderer = app.inkloom.engine.v3.render.BrushProgramBatchRenderer

internal typealias EngineStrokeTransaction = app.inkloom.engine.v3.scene.EngineTransaction.Strokes
internal typealias EngineExternalTransaction = app.inkloom.engine.v3.scene.EngineTransaction.External
internal typealias EngineTransactionJournal = app.inkloom.engine.v3.scene.EngineTransactionJournal
internal typealias SparseAlphaMask = app.inkloom.engine.v3.scene.SparseAlphaMask
internal typealias DocumentSelectionState = app.inkloom.engine.v3.scene.DocumentSelectionState
internal typealias SparseTileScene = app.inkloom.engine.v3.scene.SparseTileScene
internal typealias SparseTileDebugSnapshot = app.inkloom.engine.v3.scene.SparseTileScene.DebugSnapshot
internal typealias PixelTileSurface = app.inkloom.engine.v3.surface.PixelTileSurface
internal typealias PixelTileTransactionRecord = app.inkloom.engine.v3.surface.PixelTileTransactionRecord

internal typealias NativeRasterOps = app.inkloom.engine.v3.tools.NativeRasterOps
internal typealias RasterTileBlendSession = app.inkloom.engine.v3.tools.RasterTileBlendSession
internal typealias RasterTileEraserSession = app.inkloom.engine.v3.tools.RasterTileEraserSession
internal typealias RasterTileLiquifySession = app.inkloom.engine.v3.tools.RasterTileLiquifySession
internal typealias RasterLayerPatch = app.inkloom.engine.v3.tools.RasterLayerPatch
internal typealias RasterTileEditSession = app.inkloom.engine.v3.tools.RasterTileEditSession
internal typealias RasterWetPaintMixer = app.inkloom.engine.v3.tools.RasterWetPaintMixer
internal typealias RasterTilePaintSession = app.inkloom.engine.v3.tools.RasterTilePaintSession
internal typealias RasterSelectionMask = app.inkloom.engine.v3.tools.RasterSelectionMask
internal typealias RasterSelectionTransformSession = app.inkloom.engine.v3.tools.RasterSelectionTransformSession
internal typealias SelectionAffineTransform = app.inkloom.engine.v3.tools.SelectionAffineTransform
internal typealias MaskTileSurfaceSnapshot = app.inkloom.engine.v3.surface.MaskTileSurfaceSnapshot

internal fun importPixelTileSurface(bitmap: android.graphics.Bitmap): PixelTileSurface =
    app.inkloom.engine.v3.android.PixelTileBitmapAdapter.import(bitmap)

internal inline fun <T> traceEngineV3(section: String, block: () -> T): T =
    app.inkloom.engine.v3.diagnostics.traceEngineV3(section, block)
