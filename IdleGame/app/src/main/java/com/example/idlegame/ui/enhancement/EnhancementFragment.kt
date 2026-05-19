package com.example.idlegame.ui.enhancement

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
import android.widget.Toast
import com.example.idlegame.databinding.FragmentEnhancementBinding
import com.example.idlegame.databinding.ItemPrestigeUpgradeBinding
import com.example.idlegame.databinding.ItemStarGenBinding
import com.example.idlegame.ui.main.MainViewModel
import com.example.idlegame.util.formatNumber
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EnhancementFragment : Fragment() {

    private var _binding: FragmentEnhancementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModel.Factory(requireActivity().application as IdleGameApp)
    }

    // ★生成強化
    private val starHolders = mutableMapOf<Int, ItemStarGenBinding>()
    private var holdStar: Int? = null
    private var holdJob: Job? = null
    private var coinHoldJob: Job? = null

    // 輝石強化
    private val prestigeIds = listOf(PRESTIGE_ATTACK, PRESTIGE_COIN, PRESTIGE_OFFLINE, PRESTIGE_GEM_DROP)
    private val prestigeHolders = mutableMapOf<Int, ItemPrestigeUpgradeBinding>()
    private var prestigeHoldId: Int? = null
    private var prestigeHoldJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEnhancementBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnExpandSlotsEnh.setOnClickListener {
            if (!viewModel.expandWeaponSlots()) {
                val s = viewModel.state.value
                val msg = if (s.weaponSlots >= 30) "スロットが最大です (30)" else "コインが足りません"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCoinAttack.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    viewModel.upgradeCoinAttack()
                    coinHoldJob?.cancel()
                    coinHoldJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(400L)
                        while (isActive) {
                            viewModel.upgradeCoinAttack()
                            delay(100L)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopCoinHold()
                    true
                }
                else -> false
            }
        }

        setupPrestigeItems()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s -> updateUI(s) }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPrestigeItems() {
        val inflater = LayoutInflater.from(requireContext())
        for (id in prestigeIds) {
            val item = ItemPrestigeUpgradeBinding.inflate(inflater, binding.llPrestigeList, false)
            prestigeHolders[id] = item
            item.btnPrestigeUpgrade.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        viewModel.buyPrestigeUpgrade(id)
                        prestigeHoldId = id
                        prestigeHoldJob?.cancel()
                        prestigeHoldJob = viewLifecycleOwner.lifecycleScope.launch {
                            delay(400L)
                            while (isActive) {
                                viewModel.buyPrestigeUpgrade(id)
                                delay(150L)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopPrestigeHold()
                        true
                    }
                    else -> false
                }
            }
            binding.llPrestigeList.addView(item.root)
        }
    }

    private fun updateUI(s: GameState) {
        binding.tvCoins.text = "コイン: ${formatNumber(s.coins)}"
        binding.tvGems.text = "ジェム: ${formatNumber(s.gems.toLong())}"
        binding.tvPrestigeStones.text = "輝石: ${s.prestigeStones}"
        binding.tvSlotInfoEnh.text = "${s.totalWeapons()} / ${s.weaponSlots}"
        binding.btnExpandSlotsEnh.text = if (s.weaponSlots >= 30) {
            "スロット最大 (30)"
        } else {
            "スロット拡張\n${formatNumber(s.weaponSlotExpandCost())} コイン"
        }
        updateCoinAttack(s)
        updateList(s)
        updatePrestigeItems(s)
    }

    private fun updateCoinAttack(s: GameState) {
        val level = s.coinAttackLevel
        val maxed = level >= GameState.COIN_ATTACK_MAX_LEVEL
        binding.tvCoinAttackLevel.text = "Lv.$level"
        binding.tvCoinAttackBonus.text = "現在の攻撃力ボーナス: +${formatNumber(s.coinAttackBonus())}"
        if (maxed) {
            binding.btnCoinAttack.text = "最大レベル到達"
            binding.btnCoinAttack.isEnabled = false
        } else {
            val cost = s.coinAttackNextCost()
            val bonus = s.coinAttackNextBonus()
            binding.btnCoinAttack.isEnabled = true
            if (s.coins >= cost) {
                binding.btnCoinAttack.text = "強化 (${formatNumber(cost)} コイン) → +${formatNumber(bonus)} 攻撃力"
            } else {
                val deficit = cost - s.coins
                binding.btnCoinAttack.text = "あと ${formatNumber(deficit)} コイン不足 (強化: ${formatNumber(cost)} コイン)"
            }
        }
    }

    private fun updateList(s: GameState) {
        val highestUnlocked = s.starGenLevels.entries.filter { it.value > 0 }.maxOfOrNull { it.key } ?: 1
        val maxVisible = maxOf(highestUnlocked + 1, 2)
        val visibleStars = (2..maxVisible).toList()

        val container = binding.llStarList
        val inflater = LayoutInflater.from(requireContext())

        for (star in visibleStars) {
            if (star !in starHolders) {
                val itemBinding = ItemStarGenBinding.inflate(inflater, container, false)
                starHolders[star] = itemBinding
                attachTouchListeners(itemBinding, star)
                container.addView(itemBinding.root)
            }
        }

        for (star in visibleStars) {
            updateStarContent(starHolders[star]!!, s, star)
        }

        val sorted = visibleStars.sortedWith(compareBy({ sortKey(s, it) }, { it }))
        for ((index, star) in sorted.withIndex()) {
            val child = starHolders[star]!!.root
            if (container.indexOfChild(child) != index) {
                container.removeView(child)
                container.addView(child, index)
            }
        }
    }

    private fun sortKey(s: GameState, star: Int): Int = when (s.starGenLevel(star)) {
        0    -> 1
        50   -> 2
        else -> 0
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchListeners(item: ItemStarGenBinding, star: Int) {
        item.btnUpgrade.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    viewModel.upgradeStarGen(star)
                    holdStar = star
                    holdJob?.cancel()
                    holdJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(400L)
                        while (isActive) {
                            viewModel.upgradeStarGen(star)
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
        item.btnUnlock.setOnClickListener { viewModel.unlockStar(star) }
    }

    private fun stopHold() {
        holdStar = null
        holdJob?.cancel()
        holdJob = null
    }

    private fun stopCoinHold() {
        coinHoldJob?.cancel()
        coinHoldJob = null
    }

    private fun stopPrestigeHold() {
        prestigeHoldId = null
        prestigeHoldJob?.cancel()
        prestigeHoldJob = null
    }

    private fun updateStarContent(item: ItemStarGenBinding, s: GameState, star: Int) {
        val level = s.starGenLevel(star)

        // レベルマックス(50)は非表示
        if (level >= 50) {
            item.root.visibility = View.GONE
            if (holdStar == star) stopHold()
            return
        }
        item.root.visibility = View.VISIBLE

        val unlocked = level > 0
        val canUnlock = s.canUnlockStar(star)
        val unlockCost = s.starUnlockCost(star)
        val upgradeCost = s.starUpgradeCost(star, level)

        item.tvStarTitle.text = "★$star 生成"
        item.tvStarLevel.text = if (unlocked) "Lv.$level / 50" else "ロック中"
        item.tvStarStatus.text = when {
            !unlocked && !canUnlock -> "🔒 ★${star - 1}をLv.50にすると解除可能"
            !unlocked && canUnlock  -> "ジェム ${formatNumber(unlockCost.toLong())} で解除できます"
            else                    -> "確率: ${level}%"
        }

        if (!unlocked && canUnlock) {
            item.btnUnlock.visibility = View.VISIBLE
            item.btnUnlock.isEnabled = true
            if (s.gems >= unlockCost) {
                item.btnUnlock.text = "解除 (${formatNumber(unlockCost.toLong())} ジェム)"
            } else {
                val deficit = unlockCost - s.gems
                item.btnUnlock.text = "あと ${formatNumber(deficit.toLong())} ジェム不足"
            }
        } else {
            item.btnUnlock.visibility = View.GONE
        }

        if (unlocked) {
            item.btnUpgrade.visibility = View.VISIBLE
            item.btnUpgrade.isEnabled = true
            if (s.gems >= upgradeCost) {
                item.btnUpgrade.text = "強化 (${formatNumber(upgradeCost.toLong())} ジェム)"
            } else {
                val deficit = upgradeCost - s.gems
                item.btnUpgrade.text = "あと ${formatNumber(deficit.toLong())} ジェム不足"
            }
        } else {
            item.btnUpgrade.visibility = View.GONE
        }
    }

    private fun updatePrestigeItems(s: GameState) {
        for (id in prestigeIds) {
            prestigeHolders[id]?.let { updatePrestigeItem(it, s, id) }
        }
    }

    private fun updatePrestigeItem(item: ItemPrestigeUpgradeBinding, s: GameState, id: Int) {
        val level = s.prestigeUpgradeLevel(id)
        val maxLv  = s.prestigeUpgradeMax(id)
        val cost   = s.prestigeUpgradeCost(id)
        val maxed  = level >= maxLv

        item.tvPrestigeTitle.text  = upgradeName(id)
        item.tvPrestigeLevel.text  = "Lv.$level / $maxLv"
        item.tvPrestigeStatus.text = upgradeStatus(s, id)

        if (maxed) {
            item.btnPrestigeUpgrade.visibility = View.GONE
            if (prestigeHoldId == id) stopPrestigeHold()
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

    override fun onDestroyView() {
        stopHold()
        stopCoinHold()
        stopPrestigeHold()
        starHolders.clear()
        prestigeHolders.clear()
        super.onDestroyView()
        _binding = null
    }
}
