package com.example.idlegame.ui.prestige

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.idlegame.IdleGameApp
import com.example.idlegame.data.GameState
import com.example.idlegame.data.GameState.Companion.PRESTIGE_ATTACK
import com.example.idlegame.data.GameState.Companion.PRESTIGE_COIN
import com.example.idlegame.data.GameState.Companion.PRESTIGE_GEM_DROP
import com.example.idlegame.data.GameState.Companion.PRESTIGE_OFFLINE
import com.example.idlegame.databinding.FragmentPrestigeBinding
import com.example.idlegame.databinding.ItemPrestigeUpgradeBinding
import com.example.idlegame.ui.main.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PrestigeFragment : Fragment() {

    private var _binding: FragmentPrestigeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModel.Factory(requireActivity().application as IdleGameApp)
    }

    private val itemHolders = mutableMapOf<Int, ItemPrestigeUpgradeBinding>()
    private var holdId: Int? = null
    private var holdJob: Job? = null

    private val upgradeIds = listOf(PRESTIGE_ATTACK, PRESTIGE_COIN, PRESTIGE_OFFLINE, PRESTIGE_GEM_DROP)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPrestigeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val container = binding.llPrestigeList
        val inflater = LayoutInflater.from(requireContext())

        for (id in upgradeIds) {
            val item = ItemPrestigeUpgradeBinding.inflate(inflater, container, false)
            itemHolders[id] = item
            attachTouchListener(item, id)
            container.addView(item.root)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s -> updateUI(s) }
            }
        }
    }

    private fun updateUI(s: GameState) {
        binding.tvPrestigeStones.text = "輝石: ${s.prestigeStones}"
        for (id in upgradeIds) {
            itemHolders[id]?.let { updateItem(it, s, id) }
        }
    }

    private fun updateItem(item: ItemPrestigeUpgradeBinding, s: GameState, id: Int) {
        val level  = s.prestigeUpgradeLevel(id)
        val maxLv  = s.prestigeUpgradeMax(id)
        val cost   = s.prestigeUpgradeCost(id)
        val maxed  = level >= maxLv

        item.tvPrestigeTitle.text  = upgradeName(id)
        item.tvPrestigeLevel.text  = "Lv.$level / $maxLv"
        item.tvPrestigeStatus.text = upgradeStatus(s, id)

        if (maxed) {
            item.btnPrestigeUpgrade.visibility = View.GONE
            if (holdId == id) stopHold()
        } else {
            item.btnPrestigeUpgrade.visibility = View.VISIBLE
            item.btnPrestigeUpgrade.text       = "強化 ($cost 輝石)"
            item.btnPrestigeUpgrade.isEnabled  = s.prestigeStones >= cost
        }
    }

    private fun upgradeName(id: Int): String = when (id) {
        PRESTIGE_ATTACK   -> "⚔ 攻撃力倍率"
        PRESTIGE_COIN     -> "💰 コイン倍率"
        PRESTIGE_OFFLINE  -> "⏰ オフライン延長"
        PRESTIGE_GEM_DROP -> "💎 ジェムドロップUP"
        else              -> ""
    }

    private fun upgradeStatus(s: GameState, id: Int): String {
        val lv = s.prestigeUpgradeLevel(id)
        return when (id) {
            PRESTIGE_ATTACK   -> {
                val cur  = "×%.2f".format(1.0 + 0.05 * lv)
                val next = "×%.2f".format(1.0 + 0.05 * (lv + 1))
                if (lv >= s.prestigeUpgradeMax(id)) "攻撃力 $cur【最大】"
                else "攻撃力 $cur  →  $next"
            }
            PRESTIGE_COIN     -> {
                val cur  = "×%.1f".format(1.0 + 0.10 * lv)
                val next = "×%.1f".format(1.0 + 0.10 * (lv + 1))
                if (lv >= s.prestigeUpgradeMax(id)) "コイン獲得 $cur【最大】"
                else "コイン獲得 $cur  →  $next"
            }
            PRESTIGE_OFFLINE  -> {
                val cur  = 8 + lv
                val next = 8 + lv + 1
                if (lv >= s.prestigeUpgradeMax(id)) "オフライン上限 ${cur}時間【最大】"
                else "オフライン上限 ${cur}時間  →  ${next}時間"
            }
            PRESTIGE_GEM_DROP -> {
                val cur  = 5 + lv
                val next = 5 + lv + 1
                if (lv >= s.prestigeUpgradeMax(id)) "ジェムドロップ率 ${cur}%【最大】"
                else "ジェムドロップ率 ${cur}%  →  ${next}%"
            }
            else -> ""
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchListener(item: ItemPrestigeUpgradeBinding, id: Int) {
        item.btnPrestigeUpgrade.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    viewModel.buyPrestigeUpgrade(id)
                    holdId = id
                    holdJob?.cancel()
                    holdJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(400L)
                        while (isActive) {
                            viewModel.buyPrestigeUpgrade(id)
                            delay(150L)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopHold()
                    true
                }
                else -> false
            }
        }
    }

    private fun stopHold() {
        holdId = null
        holdJob?.cancel()
        holdJob = null
    }

    override fun onDestroyView() {
        stopHold()
        itemHolders.clear()
        super.onDestroyView()
        _binding = null
    }
}
