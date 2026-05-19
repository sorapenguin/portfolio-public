package com.example.idlegame.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.idlegame.IdleGameApp
import com.example.idlegame.R
import com.example.idlegame.data.GameRepository
import com.example.idlegame.data.GameState
import com.example.idlegame.databinding.FragmentMainBinding
import com.example.idlegame.databinding.ItemDailyMissionBinding
import com.example.idlegame.util.formatNumber
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private var tutorialDialogShown = false
    private val missionBindings = mutableListOf<ItemDailyMissionBinding>()

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModel.Factory(requireActivity().application as IdleGameApp)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // デイリーミッション行を3つ事前生成
        repeat(3) {
            val mb = ItemDailyMissionBinding.inflate(layoutInflater, binding.llDailyMissions, false)
            missionBindings.add(mb)
            binding.llDailyMissions.addView(mb.root)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.state, viewModel.isStateLoaded) { s, loaded -> s to loaded }
                    .collect { (s, loaded) ->
                    if (loaded && !s.tutorialShown && !tutorialDialogShown) {
                        tutorialDialogShown = true
                        showTutorialDialog()
                    }
                    binding.tvStage.text = if (s.isBossStage()) {
                        "★ BOSS Stage ${s.stage}  (HP×${s.bossMultiplier()})"
                    } else {
                        "Stage ${s.stage}"
                    }
                    binding.tvCoins.text = "コイン: ${formatNumber(s.coins)}"
                    binding.tvGems.text = "ジェム: ${s.gems}"

                    updateNextGoal(s)
                    updateDailyMissions(s)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.battleLog.collect { log ->
                    binding.tvBattleLog.text = if (log.isEmpty()) {
                        "（冒険中... 1分ごとに記録が更新されます）"
                    } else {
                        log.joinToString("\n")
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingOffline.collect { result ->
                    if (result != null) showOfflineDialog(result)
                }
            }
        }

        // ブーストバナー（毎秒更新）
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val s = viewModel.state.value
                    val remaining = s.boostRemainingMs()
                    if (remaining > 0) {
                        binding.tvBoostBanner.visibility = View.VISIBLE
                        val min = remaining / 60_000
                        val sec = (remaining % 60_000) / 1_000
                        binding.tvBoostBanner.text = "⚔ 攻撃力×2 発動中！ 残り ${min}分${"%02d".format(sec)}秒"
                    } else {
                        binding.tvBoostBanner.visibility = View.GONE
                    }
                    kotlinx.coroutines.delay(1_000L)
                }
            }
        }
    }

    private fun updateNextGoal(s: GameState) {
        val atk = s.totalAttack()
        val hp = s.enemyHp()
        val gap = hp - atk
        binding.tvNextGoal.text = if (gap > 0) {
            "次のステージに勝つためにあと攻撃力 ${formatNumber(gap)} 必要\n" +
            "（現在 ${formatNumber(atk)}  /  必要 ${formatNumber(hp)}）\n" +
            "→ 武器を増やすか、コイン強化・広告ブーストが有効です"
        } else {
            "✓ 現在のステージを突破中！（余裕: +${formatNumber(-gap)}）\n" +
            "Stage ${s.stage} → 次のボスまで頑張ろう"
        }
    }

    private fun updateDailyMissions(s: GameState) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val missions = s.dailyMissions(today)
        missions.forEachIndexed { i, mission ->
            val mb = missionBindings.getOrNull(i) ?: return@forEachIndexed
            mb.tvMissionTitle.text = mission.title
            mb.tvMissionProgress.text = mission.progressText
            when {
                mission.claimed -> {
                    mb.btnClaim.text = "受取済 +${mission.reward}💎"
                    mb.btnClaim.isEnabled = false
                    mb.tvMissionTitle.alpha = 0.5f
                }
                mission.canClaim -> {
                    mb.btnClaim.text = "受け取る +${mission.reward}💎"
                    mb.btnClaim.isEnabled = true
                    mb.tvMissionTitle.alpha = 1f
                    mb.btnClaim.setOnClickListener { viewModel.claimDailyMission(mission.id) }
                }
                else -> {
                    mb.btnClaim.text = "未達成"
                    mb.btnClaim.isEnabled = false
                    mb.tvMissionTitle.alpha = 1f
                }
            }
        }
    }

    private fun showTutorialDialog() {
        val message = """
            【武器】
            毎秒、武器（★1〜）がスロットに自動生成されます
            スロットは最大30まで拡張できます

            【合成】
            スロットが満タンになったら【武器】タブの「合成する」を押そう
            同じ★が2つで、より強い武器に昇格します
            「自動合成（無料）」で3分間自動的に合成することもできます

            【戦闘】
            1分ごとに敵と自動で戦い、コインを獲得します
            攻撃力が高いほど先のステージへ進めます

            【強化】（下のタブから移動）
            コインで攻撃力を強化し、スロット拡張もできます
            ジェムで高★武器の生成確率を上げましょう

            ★ 初心者ボーナス ★
            ★2生成ロック解除 ＋ ジェム×50 プレゼント！
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("はじめてのガイド")
            .setMessage(message)
            .setPositiveButton("受け取ってはじめる！") { _, _ ->
                viewModel.markTutorialShown()
            }
            .setCancelable(false)
            .show()
    }

    private fun showOfflineDialog(result: GameRepository.OfflineResult) {
        val hours = result.minutes / 60
        val mins  = result.minutes % 60
        val timeText = if (hours > 0) "${hours}時間${mins}分" else "${mins}分"

        val stageText = if (result.stageAfter > result.stageBefore) {
            "ステージ: ${result.stageBefore} → ${result.stageAfter}\n"
        } else {
            "（現在のステージで足踏み中）\n"
        }
        val gemsText = if (result.gems > 0) "獲得ジェム: +${result.gems}\n" else ""

        val message = "冒険を続けていました！\n\n" +
                "オフライン時間: $timeText\n" +
                stageText +
                "獲得コイン: +${formatNumber(result.coins)}\n" +
                gemsText + "\n" +
                "広告を見るとコインが2倍になります！"

        AlertDialog.Builder(requireContext())
            .setTitle("おかえりなさい！")
            .setMessage(message)
            .setPositiveButton("広告を見て×2") { _, _ ->
                val app = requireActivity().application as IdleGameApp
                app.adManager.showRewarded(
                    requireActivity(),
                    onRewarded = { viewModel.collectOfflineEarnings(doubled = true) },
                    onFailed = {
                        Toast.makeText(requireContext(), "広告を読み込み中です。通常報酬を受け取ります", Toast.LENGTH_SHORT).show()
                        viewModel.collectOfflineEarnings(doubled = false)
                    }
                )
            }
            .setNegativeButton("このまま受け取る") { _, _ ->
                viewModel.collectOfflineEarnings(doubled = false)
            }
            .setCancelable(false)
            .show()
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveGame()
    }

    override fun onDestroyView() {
        missionBindings.clear()
        super.onDestroyView()
        _binding = null
    }
}
