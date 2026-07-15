package starterra.debug

import android.util.Log
import dev.sorapenguin.starterra.BuildConfig

actual object DebugInputLog {
    actual fun record(message: String) {
        if (BuildConfig.DEBUG) Log.d("StarTerraInput", message)
    }
}
