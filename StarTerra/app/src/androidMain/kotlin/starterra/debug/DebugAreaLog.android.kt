package starterra.debug

import android.util.Log
import dev.sorapenguin.starterra.BuildConfig

actual object DebugAreaLog { actual fun record(message: String) { if (BuildConfig.DEBUG) Log.d("StarTerraArea", message) } }
