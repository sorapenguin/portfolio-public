package com.example.idlegame.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [GameStateEntity::class], version = 4, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {
    abstract fun gameStateDao(): GameStateDao

    companion object {
        @Volatile private var INSTANCE: GameDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE game_state ADD COLUMN autoMergeEndTime INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE game_state ADD COLUMN autoMergeFreeUsesToday INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE game_state ADD COLUMN autoMergeFreeLastDate TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE game_state ADD COLUMN autoMergeLastUsedTime INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE game_state ADD COLUMN tutorialShown INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE game_state ADD COLUMN dailyDate TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE game_state ADD COLUMN dailyMergeCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE game_state ADD COLUMN dailyPlaySeconds INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE game_state ADD COLUMN dailyAdWatchCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE game_state ADD COLUMN dailyMissionsClaimed TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): GameDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "idle_game.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
            }
    }
}
