package com.example.nonogram.data.local

import android.content.Context
import com.example.nonogram.data.model.Puzzle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BuiltinPuzzleLoader(private val context: Context) {

    private data class Entry(
        val id: Int,
        val title: String,
        val rows: Int,
        val cols: Int,
        val solution_json: String,
    )

    private val all: List<Entry> by lazy {
        val json = context.assets.open("builtin_puzzles.json").bufferedReader().readText()
        val type = object : TypeToken<List<Entry>>() {}.type
        Gson().fromJson(json, type)
    }

    fun loadByRows(rows: Int): List<Puzzle> = all
        .filter { it.rows == rows }
        .map { e ->
            Puzzle(
                id = e.id,
                title = e.title,
                rows = e.rows,
                cols = e.cols,
                solutionJson = e.solution_json,
            )
        }
}
