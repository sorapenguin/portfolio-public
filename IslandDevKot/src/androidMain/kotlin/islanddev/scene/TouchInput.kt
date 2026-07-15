package islanddev.scene

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.MotionEvent
import android.view.Window
import java.util.WeakHashMap
import kotlin.math.abs

actual object TouchInput {
    private val installedWindows = WeakHashMap<Window, Boolean>()
    @Volatile private var pendingTap: TouchPoint? = null
    private var downPoint: TouchPoint? = null

    actual fun install(context: Any?) {
        val activity = (context as? Context)?.findActivity() ?: return
        val window = activity.window
        synchronized(installedWindows) {
            if (installedWindows.put(window, true) != null) return
            val delegate = window.callback
            window.callback = object : Window.Callback by delegate {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    synchronized(this@TouchInput) {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                downPoint = TouchPoint(
                                    x = event.x,
                                    y = event.y,
                                    rawX = event.rawX,
                                    rawY = event.rawY
                                )
                            }
                            MotionEvent.ACTION_UP -> {
                                val down = downPoint
                                downPoint = null
                                if (down != null) {
                                    val dx = event.x - down.x
                                    val dy = event.y - down.y
                                    if (maxOf(abs(dx), abs(dy)) <= TAP_THRESHOLD_PX) {
                                        pendingTap = TouchPoint(
                                            x = event.x,
                                            y = event.y,
                                            rawX = event.rawX,
                                            rawY = event.rawY
                                        )
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

    private const val TAP_THRESHOLD_PX = 20f
}
