package skyisland.ui

import kotlin.math.abs

actual object SwipeState {
    private var pendingSwipe: SwipeDirection? = null

    internal fun record(dx: Float, dy: Float) = synchronized(this) {
        pendingSwipe = if (abs(dx) >= abs(dy)) {
            SwipeDirection(if (dx > 0) 1 else -1, 0)
        } else {
            SwipeDirection(0, if (dy > 0) 1 else -1)
        }
    }

    actual fun consumeSwipe(): SwipeDirection? = synchronized(this) {
        pendingSwipe.also { pendingSwipe = null }
    }
}
