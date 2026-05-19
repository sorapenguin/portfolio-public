package com.example.idlegame.ui.weapon

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.idlegame.IdleGameApp
import com.example.idlegame.R
import com.example.idlegame.data.GameState
import com.example.idlegame.databinding.FragmentWeaponBinding
import com.example.idlegame.ui.main.MainViewModel
import com.example.idlegame.util.formatNumber
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeaponFragment : Fragment() {

    private var _binding: FragmentWeaponBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModel.Factory(requireActivity().application as IdleGameApp)
    }

    private val adapter = WeaponAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeaponBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvWeapons.layoutManager = GridLayoutManager(requireContext(), 5)
        binding.rvWeapons.adapter = adapter

        adapter.update(viewModel.state.value)

        binding.btnTrashSettings.setOnClickListener {
            showTrashSettingsDialog()
        }

        binding.btnMerge.setOnClickListener {
            viewModel.mergeWeapons()
        }

        val adManager = (requireActivity().application as IdleGameApp).adManager

        binding.btnAutoMergeFree.setOnClickListener {
            viewModel.activateAutoMergeFree()
        }

        binding.btnAutoMergeAd.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("広告を視聴して自動合成")
                .setMessage("広告を視聴して自動合成（3分）を開始しますか？")
                .setPositiveButton("視聴する") { _, _ ->
                    adManager.showRewarded(
                        requireActivity(),
                        onRewarded = { viewModel.activateAutoMergeAd() },
                        onFailed   = { Toast.makeText(requireContext(), "広告を準備中です", Toast.LENGTH_SHORT).show() }
                    )
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }

        var holdJob: Job? = null
        binding.btnGemSynth.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    viewModel.addSynthesisMinute()
                    holdJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(400L)
                        while (isActive) {
                            viewModel.addSynthesisMinute()
                            delay(150L)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holdJob?.cancel()
                    holdJob = null
                    true
                }
                else -> false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var prevWeapons: Map<Int, Int> = emptyMap()
                viewModel.state.collect { s ->
                    if (s.weapons != prevWeapons) {
                        adapter.update(s)
                        prevWeapons = s.weapons
                    }
                    binding.tvTotalAttack.text = "総攻撃力: ${formatNumber(s.totalAttack())}"
                    val isFull = s.totalWeapons() >= s.weaponSlots
                    if (isFull) {
                        binding.tvSlotInfo.text = "スロット満杯！合成ボタンを押してください"
                        binding.tvSlotInfo.setTextColor(requireContext().getColor(R.color.gold))
                    } else {
                        binding.tvSlotInfo.text = "スロット: ${s.totalWeapons()} / ${s.weaponSlots}"
                        binding.tvSlotInfo.setTextColor(requireContext().getColor(R.color.text_secondary))
                    }
                    binding.tvGemsWeapon.text = "1分分の武器 / 10ジェム\n所持: ${s.gems}"
                    binding.tvMaxStar.text = "最高★: ${s.maxWeaponLevel()}  (上限 ★${GameState.MAX_WEAPON_STAR})"
                    binding.btnGemSynth.isEnabled = s.gems >= 10
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingMinutes.collect { minutes ->
                    binding.tvPendingMinutes.text = if (minutes == 0) "" else "×${minutes}分"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    updateAutoMergeUI(viewModel.state.value)
                    delay(1_000L)
                }
            }
        }
    }

    private fun showTrashSettingsDialog() {
        val ctx = requireContext()
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_trash_settings, null)
        val tvLevel = dialogView.findViewById<TextView>(R.id.tv_trash_level)
        val btnMinus = dialogView.findViewById<MaterialButton>(R.id.btn_trash_minus)
        val btnPlus = dialogView.findViewById<MaterialButton>(R.id.btn_trash_plus)

        fun updateDialogUI() {
            val s = viewModel.state.value
            val maxDel = s.maxAutoDeleteLevel()
            tvLevel.text = if (s.autoDeleteLevel == 0) "無効" else "★${s.autoDeleteLevel}以下を削除"
            btnMinus.isEnabled = s.autoDeleteLevel > 0
            btnPlus.isEnabled = s.autoDeleteLevel < maxDel
        }
        updateDialogUI()

        btnMinus.setOnClickListener {
            viewModel.setAutoDeleteLevel(viewModel.state.value.autoDeleteLevel - 1)
            updateDialogUI()
        }
        btnPlus.setOnClickListener {
            viewModel.setAutoDeleteLevel(viewModel.state.value.autoDeleteLevel + 1)
            updateDialogUI()
        }

        AlertDialog.Builder(ctx)
            .setTitle("ゴミ箱（自動削除）設定")
            .setView(dialogView)
            .setPositiveButton("閉じる") { _, _ -> }
            .show()
    }

    private fun updateAutoMergeUI(s: GameState) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val freeRemaining = s.autoMergeFreeRemainingToday(today)
        val onCooldown = s.autoMergeOnCooldown()
        val isActive = s.isAutoMergeActive()

        binding.tvAutoMergeFreeCount.text = "無料残り: $freeRemaining / ${GameState.AUTO_MERGE_DAILY_FREE} 回"

        if (isActive) {
            val remaining = s.autoMergeRemainingMs()
            val min = remaining / 60_000
            val sec = (remaining % 60_000) / 1_000
            binding.tvAutoMergeStatus.text = "動作中 残り${min}分${"%02d".format(sec)}秒"
        } else {
            binding.tvAutoMergeStatus.text = "停止中"
        }

        val showFree = isActive || onCooldown || freeRemaining > 0
        binding.btnAutoMergeFree.visibility = if (showFree) View.VISIBLE else View.GONE
        binding.btnAutoMergeAd.visibility   = if (showFree) View.GONE   else View.VISIBLE

        when {
            isActive -> {
                binding.btnAutoMergeFree.isEnabled = false
                binding.btnAutoMergeFree.text = "起動中"
            }
            onCooldown -> {
                val cdMs = s.autoMergeCooldownRemainingMs()
                val cdMin = cdMs / 60_000
                val cdSec = (cdMs % 60_000) / 1_000
                val cdText = "CT ${cdMin}分${"%02d".format(cdSec)}秒"
                if (freeRemaining > 0) {
                    binding.btnAutoMergeFree.isEnabled = false
                    binding.btnAutoMergeFree.text = cdText
                } else {
                    binding.btnAutoMergeAd.isEnabled = false
                    binding.btnAutoMergeAd.text = cdText
                }
            }
            freeRemaining > 0 -> {
                binding.btnAutoMergeFree.isEnabled = true
                binding.btnAutoMergeFree.text = "自動合成（無料）3分"
            }
            else -> {
                binding.btnAutoMergeAd.isEnabled = true
                binding.btnAutoMergeAd.text = "自動合成（広告）3分"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
