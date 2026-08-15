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
internal typealias SparseTileScene = app.inkloom.engine.v3.scene.SparseTileScene
internal typealias SparseTileDebugSnapshot = app.inkloom.engine.v3.scene.SparseTileScene.DebugSnapshot

internal typealias NativeRasterOps = app.inkloom.engine.v3.tools.NativeRasterOps
internal typealias RasterBlendSession = app.inkloom.engine.v3.tools.RasterBlendSession
internal typealias RasterEraserSession = app.inkloom.engine.v3.tools.RasterEraserSession
internal typealias RasterLiquifySession = app.inkloom.engine.v3.tools.RasterLiquifySession
internal typealias RasterLayerPatch = app.inkloom.engine.v3.tools.RasterLayerPatch
internal typealias RasterWetPaintMixer = app.inkloom.engine.v3.tools.RasterWetPaintMixer
internal typealias RasterWetPaintSession = app.inkloom.engine.v3.tools.RasterWetPaintSession

internal inline fun <T> traceEngineV3(section: String, block: () -> T): T =
    app.inkloom.engine.v3.diagnostics.traceEngineV3(section, block)
