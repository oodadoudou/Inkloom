package app.inkloom.settings
import org.junit.Assert.*
import org.junit.Test
class RasterFilterPresetsTest {
    @Test fun filtersAndFavoritesSurviveSettingsBackup(){
        val preset=RasterFilterPreset(name="Portrait",favorite=true,kind=7,strength=12f,centerX=.2f,zoom=true)
        val settings=AppSettings(rasterFilterPresets=listOf(preset))
        assertEquals(settings.rasterFilterPresets,AppSettingsBackupCodec.decode(AppSettingsBackupCodec.encode(settings)).getOrThrow().settings.rasterFilterPresets)
        assertEquals(listOf(preset),RasterFilterPresetsCodec.decode(RasterFilterPresetsCodec.encode(listOf(preset))))
    }
    @Test fun lineArtColorAndNewKindsSurvivePresetAndSettingsBackup() {
        val presets=listOf(RasterFilterPreset(name="Ink red",kind=12,colorArgb=0xFFFF4433.toInt()),
            RasterFilterPreset(name="Scan",kind=13))
        assertEquals(presets,RasterFilterPresetsCodec.decode(RasterFilterPresetsCodec.encode(presets)))
        val settings=AppSettings(rasterFilterPresets=presets)
        assertEquals(presets,AppSettingsBackupCodec.decode(AppSettingsBackupCodec.encode(settings)).getOrThrow().settings.rasterFilterPresets)
        assertEquals(app.inkloom.engine.v5.tools.RasterImageFilter.Effect.ConvertToDrawingColor(0xFFFF4433.toInt()),presets[0].effect())
        assertEquals(app.inkloom.engine.v5.tools.RasterImageFilter.Effect.BrightnessToOpacity,presets[1].effect())
    }

    @Test fun importedPresetParametersAreBounded(){
        val values=RasterFilterPresetsCodec.decode("""[{"name":"bad","kind":400,"strength":500,"angle":900,"centerX":-9}]""")
        assertEquals(15,values.single().kind);assertEquals(20f,values.single().strength,0f);assertEquals(0f,values.single().centerX,0f)
    }

    @Test fun fixedSharpenKindsRoundTripWithoutStrength(){
        val presets=listOf(RasterFilterPreset(name="Sharp",kind=14),RasterFilterPreset(name="Sharp more",kind=15,favorite=true))
        assertEquals(presets,RasterFilterPresetsCodec.decode(RasterFilterPresetsCodec.encode(presets)))
        assertEquals(app.inkloom.engine.v5.tools.RasterImageFilter.Effect.Sharpen(strong=false),presets[0].effect())
        assertEquals(app.inkloom.engine.v5.tools.RasterImageFilter.Effect.Sharpen(strong=true),presets[1].effect())
    }
}
