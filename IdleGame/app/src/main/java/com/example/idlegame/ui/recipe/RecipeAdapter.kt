package com.example.idlegame.ui.recipe

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.idlegame.data.GameState
import com.example.idlegame.data.Material
import com.example.idlegame.data.Recipe
import com.example.idlegame.databinding.ItemRecipeBinding

class RecipeAdapter(
    private val onCraft: (String) -> Unit
) : RecyclerView.Adapter<RecipeAdapter.ViewHolder>() {

    private var recipes: List<Recipe> = emptyList()
    private var state: GameState = GameState()

    fun submitList(recipes: List<Recipe>, state: GameState) {
        this.recipes = recipes
        this.state = state
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecipeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(recipes[position], state)
    }

    override fun getItemCount() = recipes.size

    inner class ViewHolder(private val b: ItemRecipeBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(recipe: Recipe, s: GameState) {
            val discovered = recipe.id in s.discoveredRecipeIds

            if (discovered) {
                b.tvRecipeName.text = recipe.name
                b.tvStatus.text = "解放済み"
                b.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                b.tvLockReason.visibility = View.GONE
                b.layoutUnlocked.visibility = View.VISIBLE
                b.tvResult.text = "→ ${recipe.resultDescription}"

                b.layoutMaterials.removeAllViews()
                for (req in recipe.materials) {
                    val have = s.fragmentAmount(req.material)
                    val tv = TextView(b.root.context)
                    tv.text = "  ${req.material.label}: $have / ${req.amount}"
                    tv.textSize = 13f
                    tv.setTextColor(
                        if (have >= req.amount) Color.parseColor("#4CAF50")
                        else Color.parseColor("#F44336")
                    )
                    b.layoutMaterials.addView(tv)
                }

                val canCraft = s.canCraft(recipe)
                b.btnCraft.isEnabled = canCraft
                b.btnCraft.alpha = if (canCraft) 1f else 0.5f
                b.btnCraft.setOnClickListener { onCraft(recipe.id) }

                b.btnHint.setOnClickListener {
                    AlertDialog.Builder(b.root.context)
                        .setTitle("💡 ヒント")
                        .setMessage(recipe.hint)
                        .setPositiveButton("OK", null)
                        .show()
                }
            } else {
                b.tvRecipeName.text = "???"
                b.tvStatus.text = "🔒 未解放"
                b.tvStatus.setTextColor(Color.parseColor("#B0BEC5"))
                b.tvLockReason.visibility = View.VISIBLE
                b.tvLockReason.text = "Stage ${recipe.unlockStage} で解放"
                b.layoutUnlocked.visibility = View.GONE
            }
        }
    }
}
