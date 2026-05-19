package com.example.idlegame.ui.shop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.idlegame.IdleGameApp
import com.example.idlegame.data.GameState
import com.example.idlegame.databinding.FragmentShopBinding
import com.example.idlegame.ui.main.MainViewModel
import com.example.idlegame.util.formatNumber
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShopFragment : Fragment() {

    private var _binding: FragmentShopBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModel.Factory(requireActivity().application as IdleGameApp)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adManager = (requireActivity().application as IdleGameApp).adManager

        binding.btnWatchGemAd.setOnClickListener {
            adManager.showRewarded(
                requireActivity(),
                onRewarded = { viewModel.watchGemAd() },
                onFailed   = { toast("広告を準備中です。しばらくお待ちください") }
            )
        }

        binding.btnWatchCoinAd.setOnClickListener {
            adManager.showRewarded(
                requireActivity(),
                onRewarded = { viewModel.watchCoinAd() },
                onFailed   = { toast("広告を準備中です。しばらくお待ちください") }
            )
        }

        binding.btnWatchBoostAd.setOnClickListener {
            adManager.showRewarded(
                requireActivity(),
                onRewarded = { viewModel.watchAttackBoostAd() },
                onFailed   = { toast("広告を準備中です。しばらくお待ちください") }
            )
        }

        binding.btnWatchShieldAd.setOnClickListener {
            adManager.showRewarded(
                requireActivity(),
                onRewarded = { viewModel.watchShieldAd() },
                onFailed   = { toast("広告を準備中です。しばらくお待ちください") }
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s -> updateUI(s) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val s = viewModel.state.value
                    updateGemButton(s)
                    updateCoinButton(s)
                    updateBoostButton(s)
                    updateShieldButton(s)
                    kotlinx.coroutines.delay(1_000L)
                }
            }
        }
    }

    private fun updateUI(s: GameState) {
        binding.tvShopCoins.text = "コイン: ${formatNumber(s.coins)}"
        binding.tvShopGems.text  = "ジェム: ${formatNumber(s.gems.toLong())}"

        binding.tvGemReward.text = "ジェム +${GameState.GEM_AD_REWARD} を獲得"
        val today = today()
        val watchedToday = if (s.gemAdLastDate == today) s.gemAdWatchedToday else 0
        val dailyRemaining = GameState.GEM_AD_DAILY_LIMIT - watchedToday
        binding.tvGemLimit.text = "本日の残り: $dailyRemaining / ${GameState.GEM_AD_DAILY_LIMIT} 回"

        val coinReward = s.coinAdReward()
        binding.tvCoinReward.text = "コイン +${formatNumber(coinReward)} を獲得"

        updateGemButton(s)
        updateCoinButton(s)
        updateBoostButton(s)
        updateShieldButton(s)
    }

    private fun updateGemButton(s: GameState) {
        val now = System.currentTimeMillis()
        val today = today()
        val watchedToday = if (s.gemAdLastDate == today) s.gemAdWatchedToday else 0
        val dailyRemaining = GameState.GEM_AD_DAILY_LIMIT - watchedToday
        val cooldownRemaining = GameState.COIN_AD_COOLDOWN_MS - (now - s.lastGemAdTime)

        when {
            dailyRemaining <= 0 -> {
                binding.btnWatchGemAd.text = "本日の上限に達しました"
                binding.btnWatchGemAd.isEnabled = false
            }
            cooldownRemaining > 0 -> {
                binding.btnWatchGemAd.text = cooldownText(cooldownRemaining)
                binding.btnWatchGemAd.isEnabled = false
            }
            else -> {
                binding.btnWatchGemAd.text = "▶ 広告を見る"
                binding.btnWatchGemAd.isEnabled = true
            }
        }
    }

    private fun updateCoinButton(s: GameState) {
        val cooldownRemaining = GameState.COIN_AD_COOLDOWN_MS - (System.currentTimeMillis() - s.lastCoinAdTime)
        if (cooldownRemaining > 0) {
            binding.btnWatchCoinAd.text = cooldownText(cooldownRemaining)
            binding.btnWatchCoinAd.isEnabled = false
        } else {
            binding.btnWatchCoinAd.text = "▶ 広告を見る"
            binding.btnWatchCoinAd.isEnabled = true
        }
    }

    private fun updateBoostButton(s: GameState) {
        val now = System.currentTimeMillis()
        val cooldownRemaining = GameState.ATTACK_BOOST_AD_COOLDOWN_MS - (now - s.lastAttackBoostAdTime)
        val boostRemaining = s.boostRemainingMs()

        when {
            boostRemaining > 0 -> {
                binding.tvBoostCooldown.text = "⚔ 強化中！ 残り ${cooldownText(boostRemaining)}"
                binding.btnWatchBoostAd.text = "発動中"
                binding.btnWatchBoostAd.isEnabled = false
            }
            cooldownRemaining > 0 -> {
                binding.tvBoostCooldown.text = "クールダウン: ${cooldownText(cooldownRemaining)}"
                binding.btnWatchBoostAd.text = cooldownText(cooldownRemaining)
                binding.btnWatchBoostAd.isEnabled = false
            }
            else -> {
                binding.tvBoostCooldown.text = "準備完了！"
                binding.btnWatchBoostAd.text = "▶ 広告を見る"
                binding.btnWatchBoostAd.isEnabled = true
            }
        }
    }

    private fun updateShieldButton(s: GameState) {
        val now = System.currentTimeMillis()
        val cooldownRemaining = GameState.SHIELD_AD_COOLDOWN_MS - (now - s.lastShieldAdTime)

        when {
            s.penaltyShieldActive -> {
                binding.tvShieldStatus.text = "シールド発動中！次の敗北を防ぎます"
                binding.btnWatchShieldAd.text = "発動中"
                binding.btnWatchShieldAd.isEnabled = false
            }
            cooldownRemaining > 0 -> {
                binding.tvShieldStatus.text = "クールダウン: ${cooldownText(cooldownRemaining)}"
                binding.btnWatchShieldAd.text = cooldownText(cooldownRemaining)
                binding.btnWatchShieldAd.isEnabled = false
            }
            else -> {
                binding.tvShieldStatus.text = "シールドなし"
                binding.btnWatchShieldAd.text = "▶ 広告を見る"
                binding.btnWatchShieldAd.isEnabled = true
            }
        }
    }

    private fun cooldownText(remainingMs: Long): String {
        val minutes = (remainingMs / 60_000).toInt()
        val seconds = ((remainingMs % 60_000) / 1_000).toInt()
        return "${minutes}分${"%02d".format(seconds)}秒"
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
