package com.example.idlegame.ui.weapon

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.idlegame.data.GameState
import com.example.idlegame.databinding.ItemWeaponBinding

class WeaponAdapter : RecyclerView.Adapter<WeaponAdapter.ViewHolder>() {

    private var items: List<WeaponItem> = emptyList()

    sealed class WeaponItem {
        data class Filled(val starLevel: Int, val attack: Long) : WeaponItem()
        object Empty : WeaponItem()
    }

    class ViewHolder(val binding: ItemWeaponBinding) : RecyclerView.ViewHolder(binding.root)

    fun update(state: GameState) {
        val newItems = buildItems(state)
        if (newItems == items) return
        items = newItems
        notifyDataSetChanged()
    }

    private fun buildItems(state: GameState): List<WeaponItem> {
        val list = mutableListOf<WeaponItem>()
        state.weapons.entries.sortedByDescending { it.key }.forEach { (level, count) ->
            repeat(count) { list.add(WeaponItem.Filled(level, GameState.starAttack(level))) }
        }
        repeat(maxOf(0, state.weaponSlots - list.size)) { list.add(WeaponItem.Empty) }
        return list
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWeaponBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = items[position]) {
            is WeaponItem.Filled -> {
                holder.binding.tvStar.text = "★${item.starLevel}"
                holder.binding.tvAttack.text = "ATK:${item.attack}"
                holder.binding.root.alpha = 1f
                holder.binding.root.cardElevation = 4f
            }
            WeaponItem.Empty -> {
                holder.binding.tvStar.text = "空"
                holder.binding.tvAttack.text = "---"
                holder.binding.root.alpha = 0.3f
                holder.binding.root.cardElevation = 1f
            }
        }
    }

    override fun getItemCount() = items.size
}
