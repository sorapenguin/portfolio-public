package starterra.debug

import android.util.Log
import dev.sorapenguin.starterra.BuildConfig
actual object DebugSignalLog { actual fun record(message: String) { if (BuildConfig.DEBUG) Log.d("StarTerraSignal", message) } }
