package com.example.idlegame.ui.recipe

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.idlegame.IdleGameApp
import com.example.idlegame.data.GameState
import com.example.idlegame.databinding.FragmentRecipeBinding
import com.example.idlegame.ui.main.MainViewModel
import kotlinx.coroutines.launch

class RecipeFragment : Fragment() {

    private var _binding: FragmentRecipeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModel.Factory(requireActivity().application as IdleGameApp)
    }

    private val adapter = RecipeAdapter { recipeId ->
        if (!viewModel.craftRecipe(recipeId)) {
            Toast.makeText(requireContext(), "素材が足りません", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvRecipes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecipes.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s -> updateUI(s) }
            }
        }
    }

    private fun updateUI(s: GameState) {
        binding.tvIronFragment.text = "鉄の欠片: ${s.ironFragments}"
        binding.tvSilverFragment.text = "銀の欠片: ${s.silverFragments}"
        binding.tvGoldFragment.text = "金の欠片: ${s.goldFragments}"

        val boosted = s.isCraftCoinBoosted()
        binding.tvCoinBoostStatus.visibility = if (boosted) View.VISIBLE else View.GONE
        if (boosted) {
            val sec = s.craftCoinBoostRemainingMs() / 1000
            binding.tvCoinBoostStatus.text = "コインブースト中！ 残り ${sec}秒"
        }

        adapter.submitList(GameState.RECIPES, s)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
