package app.inkloom.model

import app.inkloom.brushpresetv5.BrushPresetV5Codec
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.brushpresetv5.CompiledBrushProgramV5
import app.inkloom.brushpresetv5.ProceduralTipKind
import app.inkloom.brushpresetv5.RenderBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBrushCatalogTest {
    @Test
    fun factoryCatalogContainsProfessionalPenPencilAndPaintingDefaults() {
        val namesByGroup = SystemBrushCatalog.brushes.groupBy(ImportedBrush::groupName).mapValues { entry -> entry.value.map(ImportedBrush::name) }

        assertTrue(namesByGroup.getValue("Pen").containsAll(listOf("G Pen", "Painting Pen")))
        assertTrue(namesByGroup.getValue("Pencil").containsAll(listOf("Technical Pencil", "H Pencil", "HB Pencil", "2B Pencil", "6B Pencil")))
        assertTrue(namesByGroup.getValue("Brush").containsAll(listOf("Classic PS No. 19", "Dry Painting Brush")))
        assertEquals(19f, SystemBrushCatalog.byId("system-ps19")!!.defaultSize)
    }

    @Test
    fun graphiteGradesAndInkPresetsHaveDistinctRuntimeDescriptors() {
        val graphite = listOf("system-technical-pencil", "system-h-pencil", "system-hb-pencil", "system-2b-pencil", "system-6b-pencil")
            .map { requireNotNull(SystemBrushCatalog.byId(it)) }

        assertEquals(graphite.size, graphite.map { listOf(it.defaultSize, it.hardness, it.flow, it.spacing) }.distinct().size)
        assertNotEquals(SystemBrushCatalog.byId("system-g-pen"), SystemBrushCatalog.byId("system-painting-pen"))
        assertTrue(SystemBrushCatalog.brushes.all { it.format == BrushFormat.SYSTEM && it.compatibility == Compatibility.COMPLETE })
        assertTrue(graphite.all { it.proceduralPaperGrainEnabled && it.proceduralPaperGrainStrength > 0f })
    }

    @Test
    fun factoryBrushesNeverSilentlyCorrectTheAuthoredTrajectory() {
        SystemBrushCatalog.brushes.forEach { brush ->
            assertEquals("${brush.name} stabilization", 0f, brush.stabilization, .00001f)
            assertEquals("${brush.name} StreamLine", 0f, brush.streamLine, .00001f)
            assertEquals("${brush.name} moving-average stabilization", 0f, brush.procreateStabilization, .00001f)
            assertEquals("${brush.name} motion filtering", 0f, brush.motionFiltering, .00001f)
        }
    }

    @Test
    fun referenceBrushSemanticsSurvivePresetPersistence() {
        listOf("system-g-pen", "system-hb-pencil", "system-dry-painting-brush").forEach { id ->
            val source = requireNotNull(SystemBrushCatalog.byId(id))
            val first = CompiledBrushProgramV5.compile(BrushPresetV5Import.requirePreset(source))
            val restored = requireNotNull(BrushPresetV5Codec.decodeOrNull(requireNotNull(source.v5PresetJson)))
            val second = CompiledBrushProgramV5.compile(restored)

            assertEquals("$id backend changed after persistence", first.backend, second.backend)
            assertEquals("$id tip changed after persistence", first.tip, second.tip)
            assertEquals("$id deposition changed after persistence", first.deposition, second.deposition)
            assertEquals("$id paper grain changed after persistence", first.grainSampler, second.grainSampler)
            assertEquals("$id tilt changed after persistence", first.tilt, second.tilt)
        }
    }

    @Test
    fun referenceFamiliesCompileToTheirActualProductionModels() {
        val ink = compiled("system-g-pen")
        val pencil = compiled("system-hb-pencil")
        val painting = compiled("system-dry-painting-brush")

        assertEquals(RenderBackend.CONTINUOUS_RIBBON, ink.backend)
        assertEquals(RenderBackend.PROCEDURAL_STAMP, pencil.backend)
        assertEquals(ProceduralTipKind.GRAPHITE, pencil.tip.tipTexture?.proceduralKind)
        assertEquals(RenderBackend.PROCEDURAL_STAMP, painting.backend)
        assertEquals(ProceduralTipKind.BRISTLE, painting.tip.tipTexture?.proceduralKind)
        assertTrue(painting.stamp?.followStrokeDirection == true)
    }

    private fun compiled(id: String): CompiledBrushProgramV5 {
        val source = requireNotNull(SystemBrushCatalog.byId(id))
        return CompiledBrushProgramV5.compile(BrushPresetV5Import.requirePreset(source))
    }

    @Test
    fun lineArtPensCurveTheirPressureWithoutStrokeTapers() {
        listOf("system-clean-liner", "system-ink-g-pen", "system-lineart-pencil").forEach { id ->
            val program = compiled(id)
            // The raster engine refuses stroke tapers, so a factory brush must never ask for them.
            assertTrue("$id must not use stroke tapers", !program.strokeShaping.taperStartEnabled && !program.strokeShaping.taperEndEnabled)
        }
        listOf("system-clean-liner", "system-ink-g-pen").forEach { id ->
            val program = compiled(id)
            assertEquals("$id is an ink ribbon", RenderBackend.CONTINUOUS_RIBBON, program.backend)
            assertTrue("$id has a pressure curve", program.pressure.widthCurve.size >= 3)
            assertTrue("$id nearly vanishes at the lightest touch", program.pressure.widthMinimum <= .05f)
        }
        val liner = compiled("system-clean-liner").pressure.widthCurve
        val gPen = compiled("system-ink-g-pen").pressure.widthCurve
        assertTrue("the liner stays thinner under light pressure",
            app.inkloom.document.BrushDynamics.curve(liner, .3f) < app.inkloom.document.BrushDynamics.curve(gPen, .3f) - .2f)
    }

    @Test
    fun newPencilsAndCrayonsUseTheirProceduralSticks() {
        assertEquals(ProceduralTipKind.GRAPHITE, compiled("system-cream-pencil").tip.tipTexture?.proceduralKind)
        assertEquals(ProceduralTipKind.GRAPHITE, compiled("system-lineart-pencil").tip.tipTexture?.proceduralKind)
        listOf("system-wax-crayon", "system-soft-pastel").forEach { id ->
            val program = compiled(id)
            assertEquals(RenderBackend.PROCEDURAL_STAMP, program.backend)
            assertEquals(ProceduralTipKind.CHALK, program.tip.tipTexture?.proceduralKind)
            assertTrue("$id has paper tooth", (program.grainSampler?.strength ?: 0f) >= .7f)
        }
        val creamTooth = compiled("system-cream-pencil").tip.tipTexture!!.toothStrength
        val lineartTooth = compiled("system-lineart-pencil").tip.tipTexture!!.toothStrength
        assertTrue("the cream pencil is waxier than the gritty one", creamTooth < lineartTooth)
    }

    @Test
    fun watercolourGouacheAndBlenderUseTheirEngines() {
        listOf("system-wet-wash", "system-gouache", "system-flat-gouache").forEach { id ->
            assertEquals("$id paints wet", RenderBackend.WET_PAINT, compiled(id).backend)
        }
        val wash = BrushPresetV5Import.requirePreset(SystemBrushCatalog.byId("system-wet-wash")!!).wetMix!!
        val gouache = BrushPresetV5Import.requirePreset(SystemBrushCatalog.byId("system-gouache")!!).wetMix!!
        assertTrue("the wash is watery and bleeds, gouache is opaque", wash.dilution > .5f && gouache.dilution < .15f && gouache.charge > wash.charge)
        val flat = BrushPresetV5Import.requirePreset(SystemBrushCatalog.byId("system-flat-gouache")!!)
        assertEquals(ProceduralTipKind.SQUARE, flat.tipTexture?.proceduralKind)
        assertTrue(flat.stamp?.followStrokeDirection == true && flat.tip.aspect < .5f)
        assertEquals(ProceduralTipKind.WATERCOLOR_RING, compiled("system-watercolor-edge").tip.tipTexture?.proceduralKind)
        mapOf("system-triangle-watercolor" to ProceduralTipKind.TRIANGLE, "system-square-watercolor" to ProceduralTipKind.SQUARE).forEach { (id, kind) ->
            val program = compiled(id)
            assertEquals(RenderBackend.PROCEDURAL_STAMP, program.backend)
            assertEquals(kind, program.tip.tipTexture?.proceduralKind)
            assertTrue("$id shifts colour per dab", (program.stamp?.colorJitter?.hueDegrees ?: 0f) > 0f)
        }
        val blender = requireNotNull(SystemBrushCatalog.byId("system-textured-blender"))
        assertEquals(BrushToolRole.SMUDGE, blender.toolBinding.role)
        assertEquals(ProceduralTipKind.CHALK, BrushPresetV5Import.requirePreset(blender).tipTexture?.proceduralKind)
    }

    @Test
    fun authoredBrushesSurvivePersistence() {
        listOf("system-clean-liner", "system-ink-g-pen", "system-cream-pencil", "system-lineart-pencil", "system-wax-crayon", "system-soft-pastel",
            "system-wet-wash", "system-watercolor-edge", "system-triangle-watercolor", "system-square-watercolor", "system-gouache",
            "system-flat-gouache", "system-textured-blender").forEach { id ->
            val source = requireNotNull(SystemBrushCatalog.byId(id))
            val restored = requireNotNull(BrushPresetV5Codec.decodeOrNull(requireNotNull(source.v5PresetJson)))
            assertEquals(id, BrushPresetV5Import.requirePreset(source), restored)
        }
    }

    @Test
    fun liquifyCatalogExposesCspAndProcreateModesAsPeerPresets() {
        val names = SystemBrushCatalog.brushes.filter { it.groupName == "Liquify" }.map(ImportedBrush::name)

        assertTrue(names.containsAll(listOf(
            "Push", "Push Left", "Push Right", "Twirl Left", "Twirl Right",
            "Pinch", "Expand", "Crystals", "Edge", "Reconstruct",
        )))
    }
}
