package starterra.save

import java.util.prefs.Preferences
import starterra.debug.DebugSaveLog
import starterra.game.CoreState
import starterra.game.OutpostProgress
import starterra.world.FirstChapterContent

actual object PlatformOutpostSave {
    private val service = StarTerraSaveService(object : KeyValueStore {
        private val preferences = Preferences.userRoot().node("dev/sorapenguin/starterra")
        override fun read(key: String): String? = preferences.get(key, null)
        override fun write(key: String, value: String) { preferences.put(key, value); preferences.flush() }
    }, FirstChapterContent.starShards.map { it.id }.toSet())

    actual fun load(): Pair<OutpostProgress, Boolean> {
        val data = service.load()
        DebugSaveLog.record("load result=SUCCESS version=2 state=${data.toProgress().coreState} signalLinked=${data.signalLinked}")
        return data.toProgress() to data.signalLinked
    }

    actual fun save(progress: OutpostProgress, signalLinked: Boolean) {
        if (service.save(progress, signalLinked)) DebugSaveLog.record("save result=SUCCESS version=2 signalLinked=$signalLinked")
        else DebugSaveLog.record("save result=FAILED reason=STORE")
    }
}
