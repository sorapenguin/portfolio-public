package com.example.idlegame.ui.minigame

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.idlegame.IdleGameApp
import com.example.idlegame.databinding.FragmentTapGameBinding
import com.example.idlegame.ui.main.MainViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class TapGameFragment : Fragment() {

    private var _binding: FragmentTapGameBinding? = null
    private val binding get() = _binding!!

    private val tapViewModel: TapGameViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels {
        MainViewModel.Factory(requireActivity().application as IdleGameApp)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTapGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStart.setOnClickListener {
            tapViewModel.startGame()
            binding.btnStart.isEnabled = false
        }

        binding.btnTap.setOnClickListener {
            tapViewModel.onTap()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tapViewModel.state.collect { s ->
                    binding.tvTimer.text = "残り: ${s.timeLeftSeconds}秒"
                    binding.tvTapCount.text = "タップ数: ${s.tapCount}"
                    binding.btnTap.isEnabled = s.isRunning
                    if (s.isFinished) {
                        showResult(s.gemsEarned)
                    }
                }
            }
        }
    }

    private fun showResult(gems: Int) {
        mainViewModel.addGems(gems)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("ミニゲーム終了!")
            .setMessage("獲得ジェム: $gems 個")
            .setCancelable(false)
            .setPositiveButton("戻る") { _, _ ->
                findNavController().popBackStack()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
