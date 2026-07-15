package skyisland.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.MotionEvent
import android.view.Window
import java.util.WeakHashMap
import kotlin.math.abs

actual object TouchState {
    private val installedWindows = WeakHashMap<Window, Boolean>()
    private var pendingTap: TouchPoint? = null
    private var downPoint: TouchPoint? = null

    actual fun install(context: Any?) {
        val activity = (context as? Context)?.findActivity() ?: return
        val window = activity.window
        synchronized(installedWindows) {
            if (installedWindows.put(window, true) != null) return
            val delegate = window.callback
            window.callback = object : Window.Callback by delegate {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    synchronized(this@TouchState) {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> downPoint = TouchPoint(event.x, event.y)
                            MotionEvent.ACTION_UP -> {
                                val down = downPoint
                                downPoint = null
                                if (down != null) {
                                    val dx = event.x - down.x
                                    val dy = event.y - down.y
                                    if (maxOf(abs(dx), abs(dy)) > SWIPE_THRESHOLD_PX) {
                                        SwipeState.record(dx, dy)
                                    } else {
                                        pendingTap = TouchPoint(event.x, event.y)
                                    }
                                }
                            }
                            MotionEvent.ACTION_CANCEL -> downPoint = null
                        }
                    }
                    return delegate.dispatchTouchEvent(event)
                }
            }
        }
    }

    actual fun consumeTap(): TouchPoint? = synchronized(this) {
        pendingTap.also { pendingTap = null }
    }

    private fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private const val SWIPE_THRESHOLD_PX = 30f
}
