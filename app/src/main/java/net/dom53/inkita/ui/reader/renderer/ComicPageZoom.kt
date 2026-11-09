package net.dom53.inkita.ui.reader.renderer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.IntSize

internal class ComicPageZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var translation by mutableStateOf(Offset.Zero)
        private set
    var imageSize = Size.Zero

    val isZoomed: Boolean
        get() = scale > 1f

    fun reset() {
        scale = 1f
        translation = Offset.Zero
    }

    fun transform(
        viewport: IntSize,
        centroid: Offset,
        pan: Offset,
        zoom: Float,
        contentSize: Size = imageSize,
    ) {
        val newScale = (scale * zoom).coerceIn(1f, 5f)
        if (newScale == 1f) {
            reset()
            return
        }

        val focus = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
        val offset = focus + (translation - focus) * (newScale / scale) + pan
        val fit = minOf(viewport.width / contentSize.width, viewport.height / contentSize.height)
        val maxX = ((contentSize.width * fit * newScale - viewport.width) / 2f).coerceAtLeast(0f)
        val maxY = ((contentSize.height * fit * newScale - viewport.height) / 2f).coerceAtLeast(0f)
        scale = newScale
        translation = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    fun hasImage(): Boolean = imageSize.isSpecified && imageSize.width > 0f && imageSize.height > 0f
}

internal suspend fun PointerInputScope.detectComicPageTransform(
    canTransform: () -> Boolean,
    shouldPan: (Offset) -> Boolean,
    onTransformStart: () -> Unit = {},
    onTransform: (IntSize, Offset, Offset, Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var claimed = false
        var pendingPan = Offset.Zero
        var dragResolved = false
        do {
            // Claim transforms before the page turn and tap detectors see the event.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            var pan = event.calculatePan()
            if (!claimed && canTransform()) {
                val pinching = event.changes.count { it.pressed } > 1
                if (!dragResolved) pendingPan += pan
                val crossedSlop = !dragResolved && pendingPan.getDistance() > viewConfiguration.touchSlop
                if (crossedSlop) dragResolved = true
                if (pinching || (crossedSlop && shouldPan(pendingPan))) {
                    claimed = true
                    if (!pinching) pan = pendingPan
                    onTransformStart()
                }
            }
            if (claimed) {
                val centroid = event.calculateCentroid(useCurrent = false)
                if (centroid.isSpecified) onTransform(size, centroid, pan, event.calculateZoom())
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
