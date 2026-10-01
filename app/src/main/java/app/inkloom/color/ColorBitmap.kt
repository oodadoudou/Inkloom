package app.inkloom.color

import android.graphics.Bitmap
import android.graphics.ColorSpace
import app.inkloom.document.InkPage
import app.inkloom.engine.v5.surface.PixelTileFormat

internal fun InkPage.pixelFormat() = if(colorManagement.highPrecision) PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 else PixelTileFormat.PREMULTIPLIED_SRGB_8888
internal object ColorBitmap {
    fun create(width:Int,height:Int,highPrecision:Boolean):Bitmap = if(highPrecision)
        Bitmap.createBitmap(width,height,Bitmap.Config.RGBA_F16,true,ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB))
    else Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
}
