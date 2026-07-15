package starterra.debug

import android.util.Log
import dev.sorapenguin.starterra.BuildConfig

actual object DebugSaveLog {
    actual fun record(message: String) {
        if (BuildConfig.DEBUG) Log.d("StarTerraSave", message)
    }
}
