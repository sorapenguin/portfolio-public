package starterra.save

import android.content.Context
import starterra.debug.DebugSaveLog
import starterra.game.OutpostProgress
import starterra.world.FirstChapterContent

object AndroidOutpostSaveStore {
    private var service: StarTerraSaveService? = null

    fun initialize(context: Context) {
        val preferences = context.applicationContext.getSharedPreferences("starterra.outpost", Context.MODE_PRIVATE)
        service = StarTerraSaveService(object : KeyValueStore {
            override fun read(key: String): String? = preferences.getString(key, null)
            override fun write(key: String, value: String) { check(preferences.edit().putString(key, value).commit()) }
        }, FirstChapterContent.starShards.map { it.id }.toSet())
    }

    fun load(): Pair<OutpostProgress, Boolean> {
        val data = service?.load() ?: return OutpostProgress() to false
        DebugSaveLog.record("load result=SUCCESS version=2 state=${data.toProgress().coreState} signalLinked=${data.signalLinked}")
        return data.toProgress() to data.signalLinked
    }

    fun save(progress: OutpostProgress, signalLinked: Boolean) {
        val activeService = service
        if (activeService == null || !activeService.save(progress, signalLinked)) DebugSaveLog.record("save result=FAILED reason=STORE")
        else DebugSaveLog.record("save result=SUCCESS version=2 signalLinked=$signalLinked")
    }
}

actual object PlatformOutpostSave {
    actual fun load(): Pair<OutpostProgress, Boolean> = AndroidOutpostSaveStore.load()
    actual fun save(progress: OutpostProgress, signalLinked: Boolean) = AndroidOutpostSaveStore.save(progress, signalLinked)
}
