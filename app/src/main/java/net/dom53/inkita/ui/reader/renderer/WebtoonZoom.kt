package net.dom53.inkita.ui.reader.renderer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun Modifier.webtoonZoom(
    listState: LazyListState,
    onTap: () -> Unit,
): Modifier {
    val zoom = remember(listState) { ComicPageZoom() }
    val isZoomed = zoom.isZoomed
    val onTapLatest by rememberUpdatedState(onTap)

    return clipToBounds()
        .onSizeChanged { zoom.reset() }
        .pointerInput(listState) {
            detectComicPageTransform(
                canTransform = { true },
                shouldPan = { zoom.isZoomed && abs(it.x) > abs(it.y) },
            ) { viewport, centroid, pan, factor ->
                val previousScale = zoom.scale
                zoom.transform(
                    viewport,
                    Offset(centroid.x, viewport.height / 2f),
                    Offset(pan.x, 0f),
                    factor,
                    contentSize = Size(viewport.width.toFloat(), viewport.height.toFloat()),
                )
                listState.dispatchRawDelta(centroid.y / previousScale - (centroid.y + pan.y) / zoom.scale)
            }
        }.pointerInput(listState, isZoomed) {
            val reset: ((Offset) -> Unit)? =
                if (isZoomed) {
                    { tap ->
                        val delta = tap.y / zoom.scale - tap.y
                        zoom.reset()
                        listState.dispatchRawDelta(delta)
                    }
                } else {
                    null
                }
            detectTapGestures(onDoubleTap = reset, onTap = { onTapLatest() })
        }.layout { measurable, constraints ->
            val scale = zoom.scale
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            // Measure only the portion visible after scaling so LazyColumn can still reach its end.
            val listHeight = (height / scale).roundToInt()
            val list = measurable.measure(Constraints.fixed(width, listHeight))
            layout(width, height) {
                list.placeWithLayer(0, 0) {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleX = scale
                    scaleY = scale
                    translationX = zoom.translation.x
                }
            }
        }
}
