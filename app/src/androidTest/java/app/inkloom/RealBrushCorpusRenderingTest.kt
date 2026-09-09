package app.inkloom

import app.inkloom.model.BrushToolBinding
import app.inkloom.model.BrushToolRole

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.importer.AbrBrushParser
import app.inkloom.importer.ProcreateBrushParser
import app.inkloom.importer.SutBrushParser
import app.inkloom.importer.ProductionBrushRenderProbe
import app.inkloom.importer.BrushRenderProbeResult
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.brushpresetv5.BrushExecutionCapabilityResolver
import app.inkloom.brushpresetv5.BrushExecutionCapabilityResolution
import app.inkloom.brushpresetv5.CompiledBrushProgramV5
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush
import app.inkloom.model.SemanticExecutionDisposition
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.tools.RasterLayerPatch
import app.inkloom.engine.v5.material.AndroidBrushTipMaskDecoder
import app.inkloom.engine.v5.material.BrushTipMaskRequest
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealBrushCorpusRenderingTest {
    @Test
    fun importLedgerProductionProbeCommitsPaintAndEraserThroughTheRealCanvasPath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(false, true).forEach { eraser ->
            val converted = BrushPresetV5Import.convertOrReject(
                ImportedBrush(
                    id = "probe-$eraser",
                    name = "Production probe $eraser",
                    format = BrushFormat.ABR,
                    compatibility = Compatibility.HIGH,
                    sizeBytes = 1,
                    report = emptyList(),
                    toolBinding = BrushToolBinding(if (eraser) BrushToolRole.ERASE else BrushToolRole.PAINT),
                ),
            )

            assertEquals(BrushRenderProbeResult.Succeeded, ProductionBrushRenderProbe(context).probe(converted))
        }
    }

    @Test
    fun userProvidedProcreateBrushesReachATerminalProductionOutcome() =
        verifyRealBrush("brush", BrushFormat.PROCREATE, requireRendered = false)

    @Test
    fun userProvidedProcreateBrushSetBrushesReachTerminalProductionOutcomes() =
        verifyRealBrush("brushset", BrushFormat.PROCREATE)

    @Test
    fun sparseProcreateWetStampCommitsItsFirstTap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetRoot = File(context.cacheDir, "sparse-wet-stamp-${System.nanoTime()}").apply { mkdirs() }
        try {
            val brush = stagedSources("brushset").asSequence().flatMapIndexed { index, source ->
                parse(source, BrushFormat.PROCREATE, File(assetRoot, "source-$index")).asSequence()
            }.map(BrushPresetV5Import::convertOrReject).firstOrNull { it.name == "X Freckles" }
            assumeTrue("LOCAL_FIXTURE_REQUIRED: X Freckles must be present in the staged corpus", brush != null)
            assertTrue("X Freckles must commit every behavior probe including the first tap", brushCommitsAndRenders(brush!!))
        } finally {
            assetRoot.deleteRecursively()
        }
    }

    @Test
    fun userProvidedAbrBrushCommitsAndRendersInk() = verifyRealBrush("abr", BrushFormat.ABR)

    @Test
    fun userProvidedSutBrushKeepsRealtimePreview() = verifyRealBrush(
        "sut",
        BrushFormat.SUT,
        verifyRealtime = true,
        requireRendered = false,
    )

    @Test
    fun disabledSutMixingRetainsSourceAmountsWithoutEnablingWetTransport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetRoot = File(context.cacheDir, "sut-disabled-mix-${System.nanoTime()}").apply { mkdirs() }
        try {
            val disabled = stagedSources("sut").flatMapIndexed { index, file ->
                parse(file, BrushFormat.SUT, File(assetRoot, "source-$index"))
            }.filter { !it.watercolor && it.paintMix > .0001f }
            assertTrue("LOCAL_FIXTURE_REQUIRED: a SUT with disabled mixing and retained paint density", disabled.isNotEmpty())
            val rendered = mutableListOf<ImportedBrush>()
            disabled.forEach { source ->
                val converted = BrushPresetV5Import.convertOrReject(source)
                assertEquals(source.paintMix, converted.paintMix, 0f)
                assertEquals(source.colorMix, converted.colorMix, 0f)
                if (converted.compatibility == Compatibility.FAILED) {
                    assertEquals(null, converted.v5PresetJson)
                    assertTrue(converted.report.any { it.startsWith("UNSUPPORTED_WITH_EXPLICIT_REASON:") })
                    Log.i("InkloomBrushCorpus", "DISABLED_MIX_REJECTED name=${source.name} reasons=${converted.report}")
                } else {
                    val preset = BrushPresetV5Import.requirePreset(converted)
                    assertTrue(preset.identity.family != app.inkloom.brushpresetv5.BrushFamily.WET_PAINT)
                    assertEquals(null, preset.wetMix)
                    assertTrue("Disabled-mix SUT ${source.name} must execute", brushCommitsAndRenders(converted))
                    Log.i("InkloomBrushCorpus", "DISABLED_MIX_EXECUTED name=${source.name} family=${preset.identity.family} dual=${preset.dual != null}")
                    rendered += converted
                }
            }
            assertTrue("Real disabled-mix SUTs must still render, not all become rejected", rendered.isNotEmpty())
            assertTrue("LOCAL_FIXTURE_REQUIRED: Painting brush must execute its dry dual-tip program",
                rendered.any { it.name == "Painting brush" && BrushPresetV5Import.requirePreset(it).dual != null })
            assertPersistenceReloadRemainsExecutable("sut-disabled-mix", rendered)
        } finally {
            assetRoot.deleteRecursively()
        }
    }

    @Test
    fun unresolvedSutSecondaryTextureReferenceIsRejectedInsteadOfInvented() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetRoot = File(context.cacheDir, "sut-missing-dual-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = stagedSources("sut").firstOrNull { file ->
                parse(file, BrushFormat.SUT, File(assetRoot, "find-${file.name}"))
                    .any { it.name == "Painting brush" }
            }
            assertTrue("LOCAL_FIXTURE_REQUIRED: Painting brush SUT", source != null)
            val copy = requireNotNull(source).copyTo(File(assetRoot, "missing-reference.sut"))
            android.database.sqlite.SQLiteDatabase.openDatabase(copy.path, null, 0).use { db ->
                db.execSQL("UPDATE Variant SET DualTextureImage=?", arrayOf(byteArrayOf(1, 2, 3, 4)))
            }
            val decoded = parse(copy, BrushFormat.SUT, File(assetRoot, "decoded")).single()
            assertEquals(null, decoded.dualGrainPreviewPath)
            assertEquals("SUT.DualTextureImage", decoded.dualGrain.dualSourceGrainReference)
            val converted = BrushPresetV5Import.convertOrReject(decoded)
            assertEquals(Compatibility.FAILED, converted.compatibility)
            assertEquals(null, converted.v5PresetJson)
            assertTrue(converted.report.any {
                it.startsWith("UNSUPPORTED_WITH_EXPLICIT_REASON:") && it.contains("SUT.dualGrain.asset")
            })
        } finally {
            assetRoot.deleteRecursively()
        }
    }

    @Test
    fun realImportedQuickEditsKeepCanonicalIdentityAndReachProduction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetRoot = File(context.cacheDir, "quick-real-brush-${System.nanoTime()}").apply { mkdirs() }
        try {
            for ((extension, format) in listOf("sut" to BrushFormat.SUT, "abr" to BrushFormat.ABR,
                "brush" to BrushFormat.PROCREATE, "brushset" to BrushFormat.PROCREATE)) {
                var rendered = 0
                var rejected = 0
                val sources = stagedSources(extension)
                for ((index, source) in sources.withIndex()) {
                    val candidates = parse(source, format, File(assetRoot, "$extension-$index"))
                    assertTrue("No $format records parsed from ${source.name}", candidates.isNotEmpty())
                    for (decoded in candidates) {
                        val imported = BrushPresetV5Import.convertOrReject(decoded)
                        val attempt = runCatching { app.inkloom.brushpresetv5.QuickBrushEditSession.start(imported) }
                        if (attempt.isFailure) {
                            assertTrue("An unsupported real brush must give a terminal reason", !attempt.exceptionOrNull()?.message.isNullOrBlank())
                            rejected++
                            continue
                        }
                        val draft = attempt.getOrThrow()
                        val flow = if (draft.current.deposition.flow > .4f) .3f else .7f
                        val edited = draft.change("flow", flow).commit()
                        val program = BrushPresetV5Import.requirePreset(edited)
                        assertEquals(imported.id, edited.id)
                        assertEquals(imported.id, program.identity.id)
                        assertEquals(flow, program.deposition.flow, 0f)
                        assertEquals(draft.current.renderSeed, program.renderSeed)
                        assertEquals(draft.current.identity.renderBrushId, program.identity.renderBrushId)
                        assertEquals("Edited .$extension brush must actually paint", BrushRenderProbeResult.Succeeded,
                            ProductionBrushRenderProbe(context).probe(edited))
                        rendered++
                        break
                    }
                    if (rendered > 0) break
                }
                // The supplied standalone Procreate brushes currently have only explicitly rejected semantics.
                if (extension != "brush") assertTrue("No executable .$extension quick-edit fixture: rejected=$rejected", rendered > 0)
                else assertTrue("No terminal .$extension outcome", rendered + rejected > 0)
                Log.i("InkloomQuickCorpus", "$extension rendered=$rendered rejected=$rejected")
            }
        } finally { assetRoot.deleteRecursively() }
    }

    @Test fun marineLinerHasNoTransparentBreaksAtFineSizes() {
        val source = File("/data/local/tmp/inkloom-target-brushes/marine.brushset")
        assumeTrue("LOCAL_FIXTURE_REQUIRED: marine.brushset", source.isFile)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.cacheDir, "marine-thin-${System.nanoTime()}").apply { mkdirs() }
        try {
            val brush = BrushPresetV5Import.convertOrReject(parse(source, BrushFormat.PROCREATE, root).first { it.name == "海怪-勾线笔" })
            val failures = mutableListOf<String>()
            val references = mutableMapOf<Pair<Float, Float>, IntArray>()
            for (step in listOf(10, 100)) for (size in listOf(1f, 2f, 4f)) for (pressure in listOf(.15f, .4f, .8f)) {
                val surface = PixelTileSurface(1000, 1000)
                val view = InkCanvasView(instrumentation.targetContext).apply {
                    layout(0, 0, 1000, 1000)
                    activeLayerId = "paint"
                    setRasterLayerSurfaces(mapOf("paint" to surface))
                    applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, size, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
                    onPaintCapabilityRejected = { Log.e("MarineThin", "rejected: $it") }
                    prepareBrushAssetsNow()
                }
                instrumentation.runOnMainSync {
                    fun send(action: Int, x: Float, time: Long) {
                        val event = stylusEvent(action, x, 500f, time, pressure)
                        try { view.onTouchEvent(event) } finally { event.recycle() }
                    }
                    send(MotionEvent.ACTION_DOWN, 100f, 1)
                    val reports = 800 / step
                    repeat(reports) { send(MotionEvent.ACTION_MOVE, 100f + (it + 1) * step, 2L + it) }
                    send(MotionEvent.ACTION_UP, 900f, reports + 3L)
                }
                view.awaitV5StrokeWorkerIdleForTest()
                instrumentation.runOnMainSync {
                    val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    bitmap.recycle()
                }
                val missing = (250..750).count { x -> (493..507).none { y -> surface.pixelAt(x, y) ushr 24 > 0 } }
                val pixels = IntArray(501 * 15) { i -> surface.pixelAt(250 + i % 501, 493 + i / 501) }
                val key = size to pressure
                val reference = references[key]
                val error = if (reference == null) 0 else pixels.indices.maxOf { i ->
                    kotlin.math.abs((pixels[i] ushr 24) - (reference[i] ushr 24)) }
                if (reference == null) references[key] = pixels
                Log.i("MarineThin", "GAP_COUNT step=$step size=$size pressure=$pressure missing=$missing alphaError=$error")
                if (missing > 0 || error > 1) failures += "step=$step size=$size pressure=$pressure missing=$missing alphaError=$error"
            }
            assertTrue("Marine liner gaps: $failures", failures.isEmpty())
        } finally { root.deleteRecursively() }
    }

    private fun verifyRealBrush(
        extension: String,
        format: BrushFormat,
        verifyRealtime: Boolean = false,
        requireRendered: Boolean = true,
    ) {
        val sources = stagedSources(extension)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetRoot = File(context.cacheDir, "real-brush-assets-${System.nanoTime()}").apply { mkdirs() }
        try {
            var discoveredCount = 0
            var renderedCount = 0
            var rejectedCount = 0
            val renderedBrushes = mutableListOf<ImportedBrush>()
            sources.forEachIndexed { index, source ->
                val parsed = parse(source, format, File(assetRoot, "source-$index"))
                assertNotNull("No $format brush parsed from ${source.name}", parsed.firstOrNull())
                discoveredCount += parsed.size
                parsed.forEach { decoded ->
                    val representative = BrushPresetV5Import.convertOrReject(decoded)
                    assertTrue(
                        "$format ${source.name} fell back instead of parsing: ${representative.report}",
                        representative.report.none { it.contains("parsing failed", ignoreCase = true) },
                    )
                    val nonExecuted = representative.semanticMappings.filter {
                        it.executionDisposition != SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED
                    }
                    nonExecuted.forEach { mapping ->
                        assertTrue(
                            "${representative.name} silently retained ${mapping.sourceSemantic} without a compatibility report",
                            representative.report.any {
                                it.startsWith("SEMANTIC_CAPABILITY:${mapping.executionDisposition.name}:${mapping.sourceSemantic}:")
                            },
                        )
                    }
                    if (representative.compatibility == Compatibility.FAILED) {
                        assertEquals(null, representative.v5PresetJson)
                        assertTrue(
                            "$format ${source.name} import rejection must carry a stable explicit reason",
                            representative.report.any { it.startsWith("UNSUPPORTED_WITH_EXPLICIT_REASON:") },
                        )
                        Log.w(
                            "InkloomBrushCorpus",
                            "IMPORT_REJECTED source=${source.name} id=${representative.id} reasons=${representative.report}",
                        )
                        rejectedCount += 1
                        return@forEach
                    }
                    nonExecuted.forEach { mapping ->
                        assertEquals(
                            "A usable brush may retain an explicitly reported non-blocking source semantic, " +
                                "but it may not carry a fatal runtime gap: ${mapping.sourceSemantic}",
                            SemanticExecutionDisposition.PRESERVED_SOURCE_ONLY,
                            mapping.executionDisposition,
                        )
                    }
                    val preset = BrushPresetV5Import.requirePreset(representative)
                    Log.i(
                        "InkloomBrushCorpus",
                        "source=${source.name} id=${representative.id} format=${representative.format} " +
                            "compatibility=${representative.compatibility} family=${preset.identity.family} " +
                            "size=${representative.defaultSize} opacity=${representative.defaultOpacity} flow=${representative.flow} " +
                            "spacing=${representative.spacing} tips=${representative.tipPreviewPaths.size} " +
                            "grain=${representative.grainDensity} pressure=${preset.dynamics.pressure} tilt=${preset.dynamics.tilt} wet=${preset.wetMix} " +
                            "output=${preset.output} smoothing=${preset.stroke?.smoothing} " +
                            "tipAspect=${preset.tip.aspect} tipRotation=${preset.tip.rotationDegrees} hardness=${preset.tip.hardness} " +
                            "tipMax=${preset.tipTexture?.assetPaths.orEmpty().map { path -> AndroidBrushTipMaskDecoder.decode(BrushTipMaskRequest(path, preset.tip.hardness))?.maximumCoverage }} " +
                            "unsupported=${representative.report.filter { it.contains("unsupported", ignoreCase = true) }} " +
                            "semanticOutcomes=${representative.semanticMappings.groupingBy { it.executionDisposition }.eachCount()} " +
                            "report=${representative.report}",
                    )
                    when (val capability = BrushExecutionCapabilityResolver.resolve(CompiledBrushProgramV5.compile(preset))) {
                        BrushExecutionCapabilityResolution.Supported -> Log.i(
                            "InkloomBrushCorpus",
                            "CAPABILITY_RESOLVED id=${representative.id} outcome=SUPPORTED",
                        )
                        is BrushExecutionCapabilityResolution.Unsupported -> Log.i(
                            "InkloomBrushCorpus",
                            "CAPABILITY_RESOLVED id=${representative.id} outcome=UNSUPPORTED reasons=${capability.reasons.joinToString(",")}",
                        )
                    }
                    assertEquals(format, representative.format)
                    if (brushCommitsAndRenders(representative)) {
                        renderedCount += 1
                        renderedBrushes += representative
                        if (verifyRealtime) assertSutBrushKeepsRealTimePreview(representative)
                    } else {
                        rejectedCount += 1
                    }
                }
            }
            assertEquals(
                "$format .$extension corpus terminal outcomes must conserve every discovered brush",
                discoveredCount,
                renderedCount + rejectedCount,
            )
            Log.i(
                "InkloomBrushCorpus",
                "CORPUS_SUMMARY format=$format extension=$extension filesScanned=${sources.size} " +
                    "brushesDiscovered=$discoveredCount rendered=$renderedCount rejected=$rejectedCount",
            )
            if (requireRendered) {
                assertTrue("No staged .$extension $format brush rendered through Paint Engine V5", renderedCount > 0)
                assertPersistenceReloadRemainsExecutable(extension, renderedBrushes)
            }
        } finally {
            assetRoot.deleteRecursively()
        }
    }

    private fun assertPersistenceReloadRemainsExecutable(
        extension: String,
        renderedBrushes: List<ImportedBrush>,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferenceName = "real-brush-$extension-${System.nanoTime()}"
        val store = BrushMetadataStore(context, preferenceName)
        try {
            store.save(renderedBrushes)
            val reloaded = BrushMetadataStore(context, preferenceName).load()

            assertEquals(renderedBrushes.map(ImportedBrush::id), reloaded.map(ImportedBrush::id))
            assertEquals(renderedBrushes.map(ImportedBrush::groupName), reloaded.map(ImportedBrush::groupName))
            assertEquals(renderedBrushes.map(ImportedBrush::sortOrder), reloaded.map(ImportedBrush::sortOrder))
            assertEquals(
                "Reloaded .$extension brush must still execute through the production renderer",
                BrushRenderProbeResult.Succeeded,
                ProductionBrushRenderProbe(context).probe(reloaded.first()),
            )
        } finally {
            context.getSharedPreferences(preferenceName, android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun stagedSources(extension: String): List<File> {
        val sourceRoot = File("/data/local/tmp/inkloom-real-brushes")
        val sources = sourceRoot.listFiles()?.filter { it.isFile && it.extension.equals(extension, ignoreCase = true) }
            ?.sortedBy(File::getName)
            .orEmpty()
        assumeTrue(
            "LOCAL_FIXTURE_REQUIRED: stage a .$extension brush under $sourceRoot",
            sources.isNotEmpty(),
        )
        return sources
    }

    private fun parse(source: File, format: BrushFormat, assetRoot: File): List<ImportedBrush> {
        val fallback = ImportedBrush(
            id = "corpus-${format.name.lowercase()}-${source.nameWithoutExtension.lowercase()}",
            name = source.nameWithoutExtension,
            format = format,
            compatibility = Compatibility.BASIC,
            sizeBytes = source.length(),
            report = emptyList(),
        )
        return when (format) {
            BrushFormat.PROCREATE -> ProcreateBrushParser(
                assetDirectory = File(assetRoot, "procreate"),
                allowProceduralApproximation = true,
            ).enrich(source, source.name.hashCode().toString(), fallback)
            BrushFormat.SUT -> listOf(SutBrushParser(File(assetRoot, "sut")).enrich(source, source.name.hashCode().toString(), fallback))
            BrushFormat.ABR -> AbrBrushParser(File(assetRoot, "abr")).enrich(source, source.name.hashCode().toString(), fallback)
            else -> error("Unsupported real corpus format $format")
        }
    }

    private fun assertSutBrushKeepsRealTimePreview(brush: ImportedBrush) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val surface = PixelTileSurface(1000, 1000)
        var committed: RasterLayerPatch? = null
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1000, 1000)
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { commit -> committed = commit.capturePatch() }
            applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, brush.defaultSize.coerceIn(20f, 140f), 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            prepareBrushAssetsNow()
        }
        try {
            val frameTimes = ArrayList<Long>(90)
            val inputTimes = ArrayList<Long>(90)
            val workerTimes = ArrayList<Long>(90)
            val presentationTimes = ArrayList<Long>(90)
            instrumentation.runOnMainSync {
                view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 120f, 620f, 100L))
                repeat(90) { index ->
                    val started = android.os.SystemClock.elapsedRealtimeNanos()
                    view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 128f + index * 8f, 620f + (index % 7) * 3f, 110L + index))
                    val inputEnded = android.os.SystemClock.elapsedRealtimeNanos()
                    view.awaitV5StrokeWorkerIdleForTest()
                    val workerEnded = android.os.SystemClock.elapsedRealtimeNanos()
                    bitmap.eraseColor(Color.TRANSPARENT)
                    view.draw(Canvas(bitmap))
                    val ended = android.os.SystemClock.elapsedRealtimeNanos()
                    if (index >= 8) {
                        frameTimes += ended - started
                        inputTimes += inputEnded - started
                        workerTimes += workerEnded - inputEnded
                        presentationTimes += ended - workerEnded
                    }
                }
            }
            view.awaitV5StrokeWorkerIdleForTest()
            instrumentation.runOnMainSync {
                bitmap.eraseColor(Color.TRANSPARENT)
                view.draw(Canvas(bitmap))
                assertTrue("Real SUT ${brush.name} must present pixels before pen-up", bitmap.hasVisibleAlpha())
                view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 860f, 640f, 220L))
            }
            view.awaitV5StrokeWorkerIdleForTest()
            instrumentation.runOnMainSync { }
            fun stageP95(values: List<Long>): Float {
                val ordered = values.sorted()
                return ordered[(ordered.size * 95 / 100).coerceAtMost(ordered.lastIndex)] / 1_000_000f
            }
            Log.i("InkloomV5Perf", "real_sut_stage=${brush.name} input_p95=${stageP95(inputTimes)} " +
                "worker_p95=${stageP95(workerTimes)} presentation_p95=${stageP95(presentationTimes)}")
            val sorted = frameTimes.sorted()
            val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
            Log.i(
                "InkloomV5Perf",
                "real_sut=${brush.name} p95_ms=$p95Ms size=${brush.defaultSize} spacing=${brush.spacing} " +
                    "spacing_mode=${brush.spacingMode} flow=${brush.flow} tips=${brush.tipPreviewPaths.size} " +
                    "grain=${brush.grainDensity} dual=${brush.dualTipPreviewPaths.size}",
            )
            assertTrue("Real SUT ${brush.name} preview p95 was ${p95Ms}ms", p95Ms < 16.7f)
            requireNotNull(committed).close()
        } finally {
            bitmap.recycle()
        }
    }

    private fun brushCommitsAndRenders(brush: ImportedBrush): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(1000, 1000)
        val committed = mutableListOf<RasterLayerPatch>()
        val rejections = mutableListOf<String>()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1000, 1000)
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { commit -> committed += commit.capturePatch() }
            onPaintCapabilityRejected = { rejections += it }
            // A fixed bounded diameter makes cross-brush behavior probes comparable and prevents
            // giant authored defaults from turning the gate into a fill-rate benchmark.
            applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, brush.defaultSize.coerceIn(20f, 64f), 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            prepareBrushAssetsNow()
        }
        var previousCommits = 0
        var previousRejections = 0
        var completedProbes = 0
        dispatchBehaviorProbe(view) {
            view.awaitV5StrokeWorkerIdleForTest()
            instrumentation.runOnMainSync { }
            val commits = committed.size - previousCommits
            val rejected = rejections.size - previousRejections
            assertEquals("${brush.name} probe $completedProbes must have exactly one terminal outcome: " +
                "commits=$commits rejected=${rejections.drop(previousRejections)}", 1, commits + rejected)
            rejections.drop(previousRejections).forEach { reason ->
                assertTrue("Explicit terminal reason required: $reason", reason.startsWith("UNSUPPORTED_WITH_EXPLICIT_REASON:"))
            }
            Log.i("InkloomBrushCorpus", "CONTACT_OUTCOME name=${brush.name} probe=$completedProbes commits=$commits reasons=${rejections.drop(previousRejections)}")
            previousCommits = committed.size
            previousRejections = rejections.size
            completedProbes++
        }
        assertEquals(EXPECTED_BEHAVIOR_PROBE_COMMITS, completedProbes)
        val rendered = committed.isNotEmpty()
        if (!rendered) {
            Log.w(
                "InkloomBrushCorpus",
                "PRODUCTION_REJECTED id=${brush.id} format=${brush.format} name=${brush.name} rejections=$rejections",
            )
            return false
        }
        // A zero-coverage tap may reject explicitly while later dabs cross opaque material.
        // Do not alter source coverage merely to manufacture nine nonempty commits.
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val foundInk = bitmap.hasVisibleAlpha() && surface.tileCount > 0
        bitmap.recycle()
        committed.forEach(RasterLayerPatch::close)
        assertTrue("${brush.format} ${brush.name} committed but rendered no ink", foundInk)
        Log.i(
            "InkloomBrushCorpus",
            "BEHAVIOR_PROBE_RENDERED id=${brush.id} format=${brush.format} name=${brush.name} " +
                "probes=$EXPECTED_BEHAVIOR_PROBE_COMMITS tileCount=${surface.tileCount}",
        )
        return true
    }

    private fun dispatchBehaviorProbe(view: InkCanvasView, afterStroke: () -> Unit) {
        fun stroke(points: List<ProbePoint>) {
            val downTime = points.first().time
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
            points.forEachIndexed { index, point ->
                val action = when (index) {
                    0 -> MotionEvent.ACTION_DOWN
                    points.lastIndex -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
                stylusEvent(action, point.x, point.y, point.time, point.pressure, point.tilt, downTime).also { event ->
                    try { view.onTouchEvent(event) } finally { event.recycle() }
                }
            }
            }
            afterStroke()
        }
        var time = 10L
        stroke(listOf(ProbePoint(80f, 100f, .65f, .10f, time), ProbePoint(86f, 100f, .65f, .10f, time + 4)))
        time += 20
        stroke((0..12).map { index -> ProbePoint(120f + index * 58f, 200f, .55f, .12f, time + index * 8L) })
        time += 120
        stroke((0..12).map { index -> ProbePoint(120f + index * 58f, 310f, .10f + index / 12f * .88f, .12f, time + index * 8L) })
        time += 120
        stroke((0..12).map { index -> ProbePoint(120f + index * 58f, 420f, .72f, .02f + index / 12f * .82f, time + index * 8L) })
        time += 120
        stroke((0..14).map { index -> ProbePoint(100f + index * 55f, 530f + (index % 3 - 1) * 8f, .62f, .35f, time + index * 22L) })
        time += 340
        stroke((0..14).map { index -> ProbePoint(100f + index * 55f, 625f + (index % 3 - 1) * 8f, .62f, .35f, time + index) })
        time += 40
        val circle = (0..24).map { index ->
            val angle = Math.PI.toFloat() * 2f * index / 24f
            ProbePoint(
                x = 500f + kotlin.math.cos(angle) * 115f,
                y = 770f + kotlin.math.sin(angle) * 75f,
                pressure = .58f,
                tilt = .42f,
                time = time + index * 7L,
            )
        }
        stroke(circle)
        time += 190
        repeat(2) { pass ->
            stroke((0..10).map { index ->
                ProbePoint(180f + index * 64f, 900f + pass * 5f, .56f, .38f, time + pass * 90L + index * 7L)
            })
        }
    }

    private fun Bitmap.hasVisibleAlpha(): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.any { Color.alpha(it) > 0 }
    }

    private fun stylusEvent(
        action: Int,
        x: Float,
        y: Float,
        time: Long,
        pressure: Float = .8f,
        tilt: Float = 0f,
        downTime: Long = 1L,
    ): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            this.pressure = pressure
            size = .1f
            setAxisValue(MotionEvent.AXIS_TILT, tilt)
        })
        return MotionEvent.obtain(downTime, time, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
    }

    private data class ProbePoint(
        val x: Float,
        val y: Float,
        val pressure: Float,
        val tilt: Float,
        val time: Long,
    )

    private companion object {
        const val EXPECTED_BEHAVIOR_PROBE_COMMITS = 9
    }
}
