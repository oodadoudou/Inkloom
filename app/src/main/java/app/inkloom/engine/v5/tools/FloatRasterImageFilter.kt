package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import kotlin.math.*

/** Premultiplied float sampling preserves alpha and HDR. All taps read one immutable generation;
 * world-coordinate mosaic/noise produce the same result on either side of a tile boundary. */
internal object FloatRasterImageFilter {
    fun apply(source:PixelTileSurfaceSnapshot,effect:RasterImageFilter.Effect,selection:MaskTileSurfaceSnapshot?,cancelled:()->Boolean):PixelTileSurface {
        require(effect.sigma.isFinite() && effect.sigma in 1f..20f)
        when(effect) {
            is RasterImageFilter.Effect.MotionBlur -> require(effect.angle.isFinite())
            is RasterImageFilter.Effect.RadialBlur -> require(effect.centerX in 0f..1f && effect.centerY in 0f..1f)
            is RasterImageFilter.Effect.ChromaticAberration -> require(effect.angle.isFinite())
            is RasterImageFilter.Effect.Wave -> require(effect.wavelength in 16f..512f)
            is RasterImageFilter.Effect.UnsharpMask -> require(effect.amount in 0f..4f && effect.threshold in 0f..1f)
            else -> Unit
        }
        val width=source.logicalWidth;val height=source.logicalHeight;val tile=source.tileSize
        val output=PixelTileSurface(width,height,tile,pixelFormat=source.pixelFormat)
        val halo=when(effect){
            is RasterImageFilter.Effect.RadialBlur -> ceil(hypot(width.toDouble(),height.toDouble())*(if(effect.zoom) effect.sigma.toDouble()/100 else sin(Math.toRadians(effect.sigma.toDouble())))).toInt()+2
            is RasterImageFilter.Effect.Mosaic -> ceil(effect.sigma*2).toInt()
            else -> ceil(effect.sigma*3).toInt()+2
        }
        val keys=linkedSetOf<PixelTileKey>()
        source.tileInfo.forEach{info->
            val left=max(0,info.key.column*tile-halo)/tile;val top=max(0,info.key.row*tile-halo)/tile
            val right=min(width-1,(info.key.column+1)*tile+halo)/tile;val bottom=min(height-1,(info.key.row+1)*tile+halo)/tile
            for(y in top..bottom)for(x in left..right)keys+=PixelTileKey(x,y)
        }
        fun pixel(x:Int,y:Int)=source.linearPixelAt(x,y)
        fun sample(x:Float,y:Float):LinearRgba {
            val ix=floor(x).toInt();val iy=floor(y).toInt();val tx=x-ix;val ty=y-iy
            val a=pixel(ix,iy);val b=pixel(ix+1,iy);val c=pixel(ix,iy+1);val d=pixel(ix+1,iy+1)
            fun mix(f:(LinearRgba)->Float)=f(a)*(1-tx)*(1-ty)+f(b)*tx*(1-ty)+f(c)*(1-tx)*ty+f(d)*tx*ty
            return LinearRgba(mix{it.red},mix{it.green},mix{it.blue},mix{it.alpha})
        }
        fun mean(points:List<Pair<Float,Float>>,x:Int,y:Int):LinearRgba {
            var r=0f;var g=0f;var b=0f;var a=0f
            points.forEach{(dx,dy)->val p=sample(x+dx,y+dy);r+=p.red;g+=p.green;b+=p.blue;a+=p.alpha}
            val n=points.size.toFloat();return LinearRgba(r/n,g/n,b/n,a/n)
        }
        val angle=when(effect){is RasterImageFilter.Effect.MotionBlur->effect.angle;is RasterImageFilter.Effect.ChromaticAberration->effect.angle;else->0f}*PI.toFloat()/180
        val taps=when(effect){
            is RasterImageFilter.Effect.MotionBlur -> (0..32).map{ i->val d=(i/16f-1)*effect.sigma;cos(angle)*d to sin(angle)*d }
            is RasterImageFilter.Effect.LensBlur -> listOf(0f to 0f)+(1..40).map{i->val r=sqrt(i/40f)*effect.sigma;val a=i*2.399963f;cos(a)*r to sin(a)*r}
            else->emptyList()
        }
        val mosaic=mutableMapOf<Pair<Int,Int>,LinearRgba>()
        output.beginWrite("float image filter").use { write->
            keys.forEach{key->
                if(cancelled())throw java.util.concurrent.CancellationException()
                val w=min(tile,width-key.column*tile);val h=min(tile,height-key.row*tile)
                // Gaussian is separable and linear in pixels, including F16; no radius-squared loop.
                val sigma=effect.sigma;val radius=ceil(sigma*3).toInt()
                val blurred=if(effect is RasterImageFilter.Effect.GaussianBlur || effect is RasterImageFilter.Effect.UnsharpMask) {
                    val pw=w+radius*2;val ph=h+radius*2
                    val horizontal=FloatArray(pw*ph*4);val result=FloatArray(w*h*4)
                    val kernel=FloatArray(radius*2+1){ exp(-((it-radius)*(it-radius))/(2*sigma*sigma)) };val sum=kernel.sum();for(i in kernel.indices)kernel[i]/=sum
                    for(y in 0 until ph) {if(cancelled())throw java.util.concurrent.CancellationException();for(x in 0 until w) {
                        val out=(y*pw+x+radius)*4
                        for(dx in -radius..radius){val p=pixel(key.column*tile+x+dx,key.row*tile+y-radius);val k=kernel[dx+radius]
                            horizontal[out]+=p.red*k;horizontal[out+1]+=p.green*k;horizontal[out+2]+=p.blue*k;horizontal[out+3]+=p.alpha*k}
                    }}
                    for(y in 0 until h)for(x in 0 until w)for(dy in -radius..radius){val index=((y+radius+dy)*pw+x+radius)*4;val out=(y*w+x)*4
                        for(c in 0..3)result[out+c]+=horizontal[index+c]*kernel[dy+radius]}
                    result
                } else null
                fun filtered(x:Int,y:Int):LinearRgba {
                    val old=pixel(x,y)
                    return when(effect){
                        is RasterImageFilter.Effect.ConvertToDrawingColor, RasterImageFilter.Effect.BrightnessToOpacity ->
                            error("Color conversions use their pixel-local pipeline")
                        is RasterImageFilter.Effect.FixedBlur, is RasterImageFilter.Effect.PerlinNoise, is RasterImageFilter.Effect.Distort, is RasterImageFilter.Effect.Tonal,
                        RasterImageFilter.Effect.RemoveJpegNoise, is RasterImageFilter.Effect.Crystallize, is RasterImageFilter.Effect.NormalMap,
                        is RasterImageFilter.Effect.Artistic, is RasterImageFilter.Effect.PencilDrawing, is RasterImageFilter.Effect.Film,
                        is RasterImageFilter.Effect.ConvertLayer ->
                            error("Fixed blurs, noise and distortions use their own pipeline")
                        is RasterImageFilter.Effect.GaussianBlur,is RasterImageFilter.Effect.UnsharpMask -> {
                            val i=((y-key.row*tile)*w+x-key.column*tile)*4;val p=requireNotNull(blurred)
                            if(effect is RasterImageFilter.Effect.UnsharpMask) {
                                val edge=maxOf(abs(old.red-p[i]),abs(old.green-p[i+1]),abs(old.blue-p[i+2]))
                                if(edge<effect.threshold)old else LinearRgba(old.red+(old.red-p[i])*effect.amount,old.green+(old.green-p[i+1])*effect.amount,old.blue+(old.blue-p[i+2])*effect.amount,old.alpha)
                            } else LinearRgba(p[i],p[i+1],p[i+2],p[i+3])
                        }
                        is RasterImageFilter.Effect.MotionBlur,is RasterImageFilter.Effect.LensBlur -> mean(taps,x,y)
                        is RasterImageFilter.Effect.RadialBlur -> {
                            val cx=effect.centerX*(width-1);val cy=effect.centerY*(height-1);val dx=x-cx;val dy=y-cy
                            val samples=(0..24).map{i->val amount=(i/24f-.5f)*effect.sigma
                                if(effect.zoom) dx*amount/100 to dy*amount/100 else {
                                    val a=amount*PI.toFloat()/180
                                    (dx*cos(a)-dy*sin(a)-dx) to (dx*sin(a)+dy*cos(a)-dy)
                                }};mean(samples,x,y)
                        }
                        is RasterImageFilter.Effect.Mosaic -> {
                            val size=(effect.sigma*2).roundToInt().coerceAtLeast(2);val cell=x/size to y/size
                            mosaic.getOrPut(cell){var r=0f;var g=0f;var b=0f;var a=0f;var n=0
                                for(yy in cell.second*size until min(height,(cell.second+1)*size))for(xx in cell.first*size until min(width,(cell.first+1)*size)){val p=pixel(xx,yy);r+=p.red;g+=p.green;b+=p.blue;a+=p.alpha;n++}
                                LinearRgba(r/n,g/n,b/n,a/n)}
                        }
                        is RasterImageFilter.Effect.Noise -> {
                            fun noise(channel:Int):Float {var n=x*73856093 xor (y*19349663) xor effect.seed xor (channel*83492791);n=(n xor (n ushr 13))*1274126177;return ((n ushr 1)/Int.MAX_VALUE.toFloat()-.5f)*effect.sigma/20*old.alpha}
                            LinearRgba(old.red+noise(0),old.green+noise(if(effect.monochrome)0 else 1),old.blue+noise(if(effect.monochrome)0 else 2),old.alpha)
                        }
                        is RasterImageFilter.Effect.ChromaticAberration -> {
                            val dx=cos(angle)*effect.sigma;val dy=sin(angle)*effect.sigma;val r=sample(x+dx,y+dy);val b=sample(x-dx,y-dy)
                            LinearRgba(r.red,old.green,b.blue,maxOf(old.alpha,r.alpha,b.alpha))
                        }
                        is RasterImageFilter.Effect.Sharpen -> {
                            // Unpremultiplied Laplacian; transparent neighbors contribute the center
                            // color, so ink edges keep their alpha and gain no bright halo.
                            if(old.alpha<=0f) old else {
                                val amount=if(effect.strong) .5f else .25f
                                val offsets=if(effect.strong) SHARPEN_STRONG else SHARPEN_NORMAL
                                val r=old.red/old.alpha;val g=old.green/old.alpha;val b=old.blue/old.alpha
                                var dr=0f;var dg=0f;var db=0f
                                for(i in offsets.indices step 2){val p=pixel(x+offsets[i],y+offsets[i+1])
                                    if(p.alpha>0f){dr+=r-p.red/p.alpha;dg+=g-p.green/p.alpha;db+=b-p.blue/p.alpha}}
                                fun channel(c:Float,d:Float)=(c+d*amount).coerceAtLeast(0f).let{if(source.pixelFormat.isHighPrecision)it else it.coerceAtMost(1f)}*old.alpha
                                LinearRgba(channel(r,dr),channel(g,dg),channel(b,db),old.alpha)
                            }
                        }
                        is RasterImageFilter.Effect.Wave -> sample(x+sin(y*2*PI.toFloat()/effect.wavelength)*effect.sigma,y.toFloat())
                        is RasterImageFilter.Effect.CleanLineArt -> {
                            var n=0;for(dy in -1..1)for(dx in -1..1)if((dx!=0 || dy!=0) && pixel(x+dx,y+dy).alpha>=.125f)n++
                            if(n<=(effect.sigma.roundToInt()-1).coerceIn(0,3))LinearRgba(0f,0f,0f,0f) else old
                        }
                        is RasterImageFilter.Effect.AdjustLineWidth,is RasterImageFilter.Effect.Denoise -> {
                            val radius=effect.sigma.roundToInt().coerceIn(1,3);val points=ArrayList<LinearRgba>()
                            for(dy in -radius..radius)for(dx in -radius..radius)points+=pixel(x+dx,y+dy)
                            if(effect is RasterImageFilter.Effect.AdjustLineWidth) (if(effect.grow)points.maxBy{it.alpha} else points.minBy{it.alpha})
                            else {
                                fun median(f:(LinearRgba)->Float):Float = points.map(f).sorted()[points.size/2]
                                LinearRgba(median{it.red},median{it.green},median{it.blue},median{it.alpha})
                            }
                        }
                    }
                }
                val packed=LongArray(tile*tile)
                for(y in 0 until h){if(cancelled())throw java.util.concurrent.CancellationException();for(x in 0 until w){
                    val px=key.column*tile+x;val py=key.row*tile+y;val a=source.linearPixelAt(px,py)
                    val weight=selection?.alphaAt((px.toLong()*selection.logicalWidth/width).toInt(),(py.toLong()*selection.logicalHeight/height).toInt())?.div(255f) ?: 1f
                    val b=if(weight==0f)a else filtered(px,py)
                    fun mix(f:(LinearRgba)->Float)=f(a)+(f(b)-f(a))*weight
                    packed[y*tile+x]=PackedRgbaF16.packPremultiplied(mix{it.red},mix{it.green},mix{it.blue},mix{it.alpha}.coerceIn(0f,1f))
                }}
                if(source.pixelFormat.isHighPrecision)write.editTileF16(key,PixelRect(0,0,w,h)){p,_->packed.copyInto(p)}
                else write.editTile(key,PixelRect(0,0,w,h)){p,_->for(i in p.indices)p[i]=PackedRgbaF16.toPremultipliedArgb8888(packed[i])}
                // Mosaic needs only neighboring cells of the current tile, not a page-sized cache.
                mosaic.clear()
            };write.commit().close()
        };return output
    }

    private val SHARPEN_NORMAL=intArrayOf(0,-1,-1,0,1,0,0,1)
    private val SHARPEN_STRONG=intArrayOf(-1,-1,0,-1,1,-1,-1,0,1,0,-1,1,0,1,1,1)
}
