package skyisland.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "save_data")
data class SaveEntity(
    @androidx.room.PrimaryKey val slot: Int = 1,
    val payload: String,
)

@Dao
interface SaveDao {
    @Query("SELECT * FROM save_data WHERE slot = 1")
    fun load(): SaveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(entity: SaveEntity)
}

@Database(entities = [SaveEntity::class], version = 1, exportSchema = false)
abstract class SkyIslandDatabase : RoomDatabase() {
    abstract fun saveDao(): SaveDao
}

class LocalSaveRepository(context: Context) : SaveRepository {
    private val dao = Room.databaseBuilder(
        context.applicationContext,
        SkyIslandDatabase::class.java,
        "skyisland.db",
    ).allowMainThreadQueries().build().saveDao()

    override fun save(data: SaveData) = dao.save(SaveEntity(payload = SaveCodec.encode(data)))
    override fun load(): SaveData = dao.load()?.payload?.let(SaveCodec::decode) ?: SaveData()
}

class FileBalanceLogRepository(context: Context) : BalanceLogRepository {
    private val file = context.applicationContext.getFileStreamPath("balance_log.csv")

    override fun appendLines(lines: List<String>) {
        if (lines.isEmpty()) return
        if (!file.exists()) file.writeText("timestamp,session_id,turn,event,detail\n")
        file.appendText(lines.joinToString("\n", postfix = "\n"))
    }
}

