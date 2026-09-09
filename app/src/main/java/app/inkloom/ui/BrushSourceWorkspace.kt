package app.inkloom.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import app.inkloom.brushpresetv5.*
import java.io.File
import java.util.UUID

/** Owns only images created by this editor. Imported originals are never writable targets. */
internal class BrushSourceWorkspace(private val context:Context): AutoCloseable {
    private val created=mutableSetOf<File>()
    private var retained=setOf<String>()
    private var closed=false
    @Synchronized fun retain(preset:BrushPresetV5) { retained=paths(preset).toSet() }
    @Synchronized override fun close() { closed=true;created.filter { it.path !in retained }.forEach(File::delete) }
    @Synchronized private fun write(bitmap:Bitmap):String {
        check(!closed)
        val dir=File(context.filesDir,"brush-sources").apply { mkdirs() }
        val file=File(dir,"${UUID.randomUUID()}.png")
        created += file
        file.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        return file.path
    }
    fun import(uri:Uri):String {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,bounds) }
        require(bounds.outWidth>0 && bounds.outHeight>0) { "Unreadable image / 无法读取图片" }
        val options=BitmapFactory.Options().apply { inSampleSize=1 }
        while(maxOf(bounds.outWidth,bounds.outHeight)/options.inSampleSize>2048) options.inSampleSize*=2
        val bitmap=context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,options) }
            ?: error("Unreadable image / 无法读取图片")
        return try { write(bitmap) } finally { bitmap.recycle() }
    }
    fun transform(path:String,operation:SourceImageOperation):String {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(path,bounds)
        val options=BitmapFactory.Options().apply { inSampleSize=1 }
        while(maxOf(bounds.outWidth,bounds.outHeight)/options.inSampleSize>2048) options.inSampleSize*=2
        val bitmap=BitmapFactory.decodeFile(path,options) ?: error("Source unavailable / 来源图案不可用")
        var result:Bitmap?=null
        return try {
            result=if(operation==SourceImageOperation.ROTATE) Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,Matrix().apply{postRotate(90f)},false)
                else process(bitmap,operation)
            write(result)
        } finally { if(result!==bitmap) result?.recycle();bitmap.recycle() }
    }
    companion object {
        fun paths(p:BrushPresetV5):List<String> = p.tipTexture?.let { it.assetPaths.ifEmpty { listOfNotNull(it.assetPath) } }.orEmpty()+listOfNotNull(p.textureGrain?.assetPath)+p.dual?.tipAssetPaths.orEmpty()+listOfNotNull(p.dual?.textureGrain?.assetPath)
        fun replace(p:BrushPresetV5,role:BrushSourceRole,path:String,index:Int=0):BrushPresetV5 {
            fun replaced(paths:List<String>):List<String> = paths.toMutableList().apply { if(index in indices) set(index,path) else add(path) }
            return when(role) {
                BrushSourceRole.SHAPE -> {
                    val tip=p.tipTexture ?: TipTextureSettings()
                    val paths=replaced(tip.assetPaths.ifEmpty { listOfNotNull(tip.assetPath) })
                    p.copy(tipTexture=tip.copy(procedural=false,assetPath=paths.first(),assetPaths=paths))
                }
                BrushSourceRole.GRAIN -> p.copy(textureGrain=(p.textureGrain ?: TextureGrainSettings(path,density=1f)).copy(assetPath=path))
                BrushSourceRole.SECONDARY_SHAPE -> p.copy(dual=(p.dual ?: DualBrushSettings(listOf(path))).let { it.copy(tipAssetPaths=replaced(it.tipAssetPaths)) })
                BrushSourceRole.SECONDARY_GRAIN -> p.copy(dual=requireNotNull(p.dual).let { it.copy(textureGrain=(it.textureGrain ?: TextureGrainSettings(path,density=1f)).copy(assetPath=path)) })
            }
        }
        private fun process(bitmap:Bitmap,operation:SourceImageOperation):Bitmap {
            val w=bitmap.width;val h=bitmap.height;val pixels=IntArray(w*h);bitmap.getPixels(pixels,0,w,0,0,w,h)
            if(operation==SourceImageOperation.INVERT) {
                val transparent=pixels.any { it ushr 24 != 255 }
                for(i in pixels.indices) pixels[i]=if(transparent) ((255-(pixels[i] ushr 24)) shl 24) else pixels[i] xor 0x00ffffff
            } else {
                // Symmetric edge blending makes opposite edges equal without a hard seam.
                val original=pixels.copyOf();val band=maxOf(1,minOf(w,h)/8)
                fun blend(a:Int,b:Int,t:Float):Int { var out=0;for(shift in listOf(0,8,16,24)) out=out or ((((a ushr shift and 255)*(1f-t)+(b ushr shift and 255)*t).toInt()) shl shift);return out }
                for(y in 0 until h) for(x in 0 until w) {
                    val d=minOf(x,w-1-x)
                    if(d<band) pixels[y*w+x]=blend(original[y*w+x],original[y*w+w-1-x],.5f*(1f-d.toFloat()/band))
                }
                pixels.copyInto(original)
                for(y in 0 until h) for(x in 0 until w) {
                    val d=minOf(y,h-1-y)
                    if(d<band) pixels[y*w+x]=blend(original[y*w+x],original[(h-1-y)*w+x],.5f*(1f-d.toFloat()/band))
                }
            }
            return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
        }
    }
}
internal enum class BrushSourceRole { SHAPE,GRAIN,SECONDARY_SHAPE,SECONDARY_GRAIN }
internal enum class SourceImageOperation { ROTATE,INVERT,REPEAT }
