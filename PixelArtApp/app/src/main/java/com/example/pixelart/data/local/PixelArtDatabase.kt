package com.example.pixelart.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PuzzleEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class PixelArtDatabase : RoomDatabase() {
    abstract fun puzzleDao(): PuzzleDao

    companion object {
        @Volatile
        private var INSTANCE: PixelArtDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pixelart_puzzles ADD COLUMN isVisible INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): PixelArtDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PixelArtDatabase::class.java,
                    "pixelart.db",
                ).addMigrations(MIGRATION_1_2)
                 .build()
                 .also { INSTANCE = it }
            }
    }
}
