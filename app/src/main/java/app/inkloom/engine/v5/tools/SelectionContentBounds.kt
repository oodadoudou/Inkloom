package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*

/** Tight visible bounds, independent of sparse tile allocation and working precision. */
fun selectionContentBounds(snapshot: PixelTileSurfaceSnapshot): PixelRect? {
    var left=snapshot.logicalWidth; var top=snapshot.logicalHeight; var right=0; var bottom=0
    for(key in snapshot.tileKeys) {
        val x0=key.column*snapshot.tileSize; val y0=key.row*snapshot.tileSize
        val area=PixelRect(x0,y0,minOf(x0+snapshot.tileSize,snapshot.logicalWidth),minOf(y0+snapshot.tileSize,snapshot.logicalHeight))
        val rgba=if(snapshot.pixelFormat.isHighPrecision) null else snapshot.copyPremultipliedPixels(area)
        val f16=if(snapshot.pixelFormat.isHighPrecision) snapshot.copyPremultipliedF16(area) else null
        for(y in 0 until area.height) for(x in 0 until area.width) {
            val i=y*area.width+x
            val visible=if(f16!=null) PackedRgbaF16.alpha(f16[i])>0f else (rgba!![i] ushr 24)>0
            if(visible) { left=minOf(left,x0+x);top=minOf(top,y0+y);right=maxOf(right,x0+x+1);bottom=maxOf(bottom,y0+y+1) }
        }
    }
    return if(right>left && bottom>top) PixelRect(left,top,right,bottom) else null
}
