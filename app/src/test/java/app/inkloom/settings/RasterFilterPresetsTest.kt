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
    @Test fun importedPresetParametersAreBounded(){
        val values=RasterFilterPresetsCodec.decode("""[{"name":"bad","kind":400,"strength":500,"angle":900,"centerX":-9}]""")
        assertEquals(11,values.single().kind);assertEquals(20f,values.single().strength,0f);assertEquals(0f,values.single().centerX,0f)
    }
}
