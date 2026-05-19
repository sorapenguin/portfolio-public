package com.example.idlegame.ui.achievement

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.idlegame.IdleGameApp
import com.example.idlegame.data.GameState
import com.example.idlegame.databinding.FragmentAchievementBinding
import com.example.idlegame.databinding.ItemAchievementBinding
import com.example.idlegame.ui.main.MainViewModel
import com.example.idlegame.util.formatNumber
import kotlinx.coroutines.launch

class AchievementFragment : Fragment() {

    private var _binding: FragmentAchievementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModel.Factory(requireActivity().application as IdleGameApp)
    }

    private data class Holder(val itemBinding: ItemAchievementBinding, val id: String)
    private val holders = mutableListOf<Holder>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAchievementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inflater = LayoutInflater.from(requireContext())
        for (def in GameState.ACHIEVEMENTS) {
            val item = ItemAchievementBinding.inflate(inflater, binding.llAchievementList, false)
            item.tvAchTitle.text = def.title
            item.tvAchDesc.text = "${def.description}  →  ジェム+${def.rewardGems}"
            item.btnAchClaim.setOnClickListener { viewModel.claimAchievement(def.id) }
            binding.llAchievementList.addView(item.root)
            holders.add(Holder(item, def.id))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s -> updateUI(s) }
            }
        }
    }

    private fun updateUI(s: GameState) {
        binding.tvAchGemTotal.text = "ジェム: ${formatNumber(s.gems.toLong())}"
        for (holder in holders) {
            val def = GameState.ACHIEVEMENTS.find { it.id == holder.id } ?: continue
            val earned = s.achievementTimesEarned(def)
            val claimed = s.achievementsClaimed[def.id] ?: 0
            val claimable = s.achievementClaimable(def)
            holder.itemBinding.tvAchStatus.text = "達成: ${earned}回 / 受取済: ${claimed}回"
            if (claimable > 0) {
                holder.itemBinding.btnAchClaim.visibility = View.VISIBLE
                holder.itemBinding.btnAchClaim.text = "受け取る (+${formatNumber((def.rewardGems * claimable).toLong())} ジェム)"
            } else {
                holder.itemBinding.btnAchClaim.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        holders.clear()
        super.onDestroyView()
        _binding = null
    }
}
