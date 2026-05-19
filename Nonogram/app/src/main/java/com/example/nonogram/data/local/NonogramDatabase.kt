package com.example.nonogram.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.nonogram.data.model.Puzzle

@Database(entities = [Puzzle::class], version = 4, exportSchema = false)
abstract class NonogramDatabase : RoomDatabase() {
    abstract fun puzzleDao(): PuzzleDao

    companion object {
        @Volatile private var INSTANCE: NonogramDatabase? = null

        fun getInstance(context: Context): NonogramDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, NonogramDatabase::class.java, "nonogram.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
