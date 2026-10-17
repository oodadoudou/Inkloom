package app.inkloom.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A page above or below the active one in the continuous view: a render of the page that takes no
 * ink. The viewport's touch proxy owns all input, so a drag starting here scrolls the strip and a
 * tap or pen press only makes it the active page; a stroke never lands on a page not chosen.
 */
@Composable
internal fun ContinuousPageNeighbor(page: InkPage, label: String, assets: DocumentAssetService, renderWidthPx: Int,
    scale: Float, translationX: Float, translationY: Float, modifier: Modifier = Modifier) {
    val render by produceState<Bitmap?>(initialValue = null, page, renderWidthPx) {
        value = withContext(Dispatchers.IO) { runCatching { assets.renderPageBitmap(page, renderWidthPx.coerceIn(64, 2048)) }.getOrNull() }
    }
    Box(modifier
        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = translationX, translationY = translationY)
        .shadow(3.dp, RoundedCornerShape(4.dp))
        .background(Color.White)
        .semantics { contentDescription = label }) {
        render?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds) }
        // A light veil marks it as not the page being drawn on, without hiding its content.
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .07f)))
    }
}
