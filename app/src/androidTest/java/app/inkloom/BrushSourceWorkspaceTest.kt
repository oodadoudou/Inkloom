package app.inkloom

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.ui.*
import app.inkloom.brushpresetv5.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class BrushSourceWorkspaceTest {
    @Test fun sourceCopiesCancelCleanlyAndOnlySavedReferencesSurvive() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=File(context.cacheDir,"source-editor-original.png")
        val image=Bitmap.createBitmap(8,16,Bitmap.Config.ARGB_8888)
        for(y in 0..15) for(x in 0..7) image.setPixel(x,y,0xff000000.toInt() or (x*25 shl 16) or (y*12))
        original.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
        val bytes=original.readBytes()
        val workspace=BrushSourceWorkspace(context)
        val copy=workspace.import(Uri.fromFile(original))
        val rotated=workspace.transform(copy,SourceImageOperation.ROTATE)
        BitmapFactory.decodeFile(rotated).also { assertEquals(16,it.width);assertEquals(8,it.height);it.recycle() }
        val repeated=workspace.transform(copy,SourceImageOperation.REPEAT)
        BitmapFactory.decodeFile(repeated).also {
            for(y in 0 until it.height) assertEquals(it.getPixel(0,y),it.getPixel(it.width-1,y))
            for(x in 0 until it.width) assertEquals(it.getPixel(x,0),it.getPixel(x,it.height-1))
            it.recycle()
        }
        val preset=BrushPresetV5(identity=PresetIdentity("source","Source",family=BrushFamily.INK,renderBrushId="source"),renderSeed=1,tip=TipSettings(18f),deposition=DepositionSettings(),dynamics=DynamicsSettings(PressureMappings(),TiltMappings()))
        val saved=BrushSourceWorkspace.replace(preset,BrushSourceRole.SHAPE,rotated)
        workspace.retain(saved);workspace.close()
        assertFalse(File(copy).exists());assertFalse(File(repeated).exists());assertTrue(File(rotated).isFile)
        assertArrayEquals(bytes,original.readBytes())
        assertEquals(saved,BrushPresetV5Codec.decodeOrNull(BrushPresetV5Codec.encode(saved)))
        File(rotated).delete();original.delete()
    }
    @Test fun replacingOneTipPreservesOtherTipsAndCancelNeverTouchesOriginal() {
        val preset=BrushPresetV5(identity=PresetIdentity("source","Source",family=BrushFamily.INK,renderBrushId="source"),renderSeed=1,tip=TipSettings(18f),deposition=DepositionSettings(),dynamics=DynamicsSettings(PressureMappings(),TiltMappings()),tipTexture=TipTextureSettings(procedural=false,assetPath="first",assetPaths=listOf("first","second")))
        val edited=BrushSourceWorkspace.replace(preset,BrushSourceRole.SHAPE,"new",1)
        assertEquals(listOf("first","new"),edited.tipTexture!!.assetPaths)
        assertEquals(listOf("first","second"),preset.tipTexture!!.assetPaths)
        val workspace=BrushSourceWorkspace(InstrumentationRegistry.getInstrumentation().targetContext)
        workspace.close()
    }
    @Test fun identicalSourcesShareOneFileAndClosingNeverRemovesOneThatAlreadyExisted() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val first=BrushSourceWorkspace(context)
        val chalk=first.builtIn(BuiltInBrushSource.CHALK)
        assertEquals("the same image is stored once",chalk,first.builtIn(BuiltInBrushSource.CHALK))
        assertTrue(File(chalk).name.startsWith("source-"))
        val preset=BrushPresetV5(identity=PresetIdentity("source","Source",family=BrushFamily.INK,renderBrushId="source"),renderSeed=1,tip=TipSettings(18f),deposition=DepositionSettings(),dynamics=DynamicsSettings(PressureMappings(),TiltMappings()))
        first.retain(BrushSourceWorkspace.replace(preset,BrushSourceRole.SHAPE,chalk));first.close()
        assertTrue(File(chalk).isFile)
        // A later session that reaches the same image and is cancelled leaves the saved brush's file alone.
        val second=BrushSourceWorkspace(context)
        assertEquals(chalk,second.builtIn(BuiltInBrushSource.CHALK))
        val splatter=second.builtIn(BuiltInBrushSource.SPLATTER)
        second.close()
        assertTrue(File(chalk).isFile)
        assertFalse("an unsaved new source is removed",File(splatter).exists())
        File(chalk).delete()
    }
}
