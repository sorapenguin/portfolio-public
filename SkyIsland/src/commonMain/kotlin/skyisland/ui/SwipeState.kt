package skyisland.ui

data class SwipeDirection(val dx: Int, val dy: Int)

expect object SwipeState {
    fun consumeSwipe(): SwipeDirection?
}
