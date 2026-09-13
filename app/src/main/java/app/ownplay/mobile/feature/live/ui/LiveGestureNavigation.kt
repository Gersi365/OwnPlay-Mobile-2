package app.ownplay.mobile.feature.live.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

internal enum class LiveNavigationDirection {
    PREVIOUS,
    NEXT,
}

internal object LiveGestureNavigationPolicy {
    fun directionForHorizontalSwipe(
        totalDragX: Float,
        minDistancePx: Float,
    ): LiveNavigationDirection? {
        require(minDistancePx > 0f) { "Minimum swipe distance must be positive." }
        return when {
            totalDragX <= -minDistancePx -> LiveNavigationDirection.NEXT
            totalDragX >= minDistancePx -> LiveNavigationDirection.PREVIOUS
            else -> null
        }
    }

    fun adjacentKey(
        keys: List<String>,
        currentKey: String?,
        direction: LiveNavigationDirection,
    ): String? {
        val navigationKeys = keys.distinct()
        if (navigationKeys.size < 2) return null

        val currentIndex = navigationKeys.indexOf(currentKey)
        if (currentIndex < 0) {
            return when (direction) {
                LiveNavigationDirection.PREVIOUS -> navigationKeys.last()
                LiveNavigationDirection.NEXT -> navigationKeys.first()
            }
        }

        val nextIndex = when (direction) {
            LiveNavigationDirection.PREVIOUS ->
                (currentIndex - 1 + navigationKeys.size) % navigationKeys.size
            LiveNavigationDirection.NEXT ->
                (currentIndex + 1) % navigationKeys.size
        }
        return navigationKeys[nextIndex]
    }
}

internal fun Modifier.liveHorizontalNavigationGestures(
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(enabled, onPrevious, onNext) {
        var totalDragX = 0f
        val minDistancePx = 56.dp.toPx()

        detectHorizontalDragGestures(
            onDragStart = { totalDragX = 0f },
            onDragCancel = { totalDragX = 0f },
            onDragEnd = {
                when (
                    LiveGestureNavigationPolicy.directionForHorizontalSwipe(
                        totalDragX = totalDragX,
                        minDistancePx = minDistancePx,
                    )
                ) {
                    LiveNavigationDirection.PREVIOUS -> onPrevious()
                    LiveNavigationDirection.NEXT -> onNext()
                    null -> Unit
                }
                totalDragX = 0f
            },
            onHorizontalDrag = { change, dragAmount ->
                totalDragX += dragAmount
                change.consume()
            },
        )
    }
}
