package com.example.idlegame.ui.settings

import android.app.ProgressDialog
import android.content.Intent
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
import com.example.idlegame.R
import com.example.idlegame.databinding.FragmentSettingsBinding
import com.example.idlegame.network.TokenManager
import com.example.idlegame.ui.auth.LoginActivity
import com.example.idlegame.ui.main.MainViewModel
import com.example.idlegame.util.formatNumber
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val gameViewModel: MainViewModel by activityViewModels {
        MainViewModel.Factory(requireActivity().application as IdleGameApp)
    }
    private val settingsViewModel: SettingsViewModel by viewModels()

    @Suppress("DEPRECATION")
    private var progressDialog: ProgressDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAchievements.setOnClickListener {
            findNavController().navigate(R.id.nav_achievement)
        }

        binding.btnStats.setOnClickListener {
            showStatsDialog()
        }

        binding.btnLoginLink.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                putExtra("from_settings", true)
            })
        }

        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("ログアウト")
                .setMessage("ログアウトしますか？\nゲームデータはこの端末に残ります。")
                .setPositiveButton("ログアウト") { _, _ ->
                    TokenManager.clearAuth(requireContext())
                    updateAccountUi()
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }

        binding.btnDeleteAccount.setOnClickListener {
            showDeleteAccountConfirmDialog()
        }

        observeSettings()
        observeDeleteState()
    }

    override fun onResume() {
        super.onResume()
        updateAccountUi()
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsViewModel.soundEffects.collect { enabled ->
                        binding.switchSoundEffects.setOnCheckedChangeListener(null)
                        binding.switchSoundEffects.isChecked = enabled
                        binding.switchSoundEffects.setOnCheckedChangeListener { _, checked ->
                            settingsViewModel.setSoundEffects(checked)
                        }
                    }
                }
                launch {
                    settingsViewModel.vibration.collect { enabled ->
                        binding.switchVibration.setOnCheckedChangeListener(null)
                        binding.switchVibration.isChecked = enabled
                        binding.switchVibration.setOnCheckedChangeListener { _, checked ->
                            settingsViewModel.setVibration(checked)
                        }
                    }
                }
            }
        }
    }

    private fun observeDeleteState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.deleteState.collect { state ->
                    when (state) {
                        is DeleteAccountState.Loading -> showProgress()
                        is DeleteAccountState.Success -> {
                            hideProgress()
                            TokenManager.clearAuth(requireContext())
                            settingsViewModel.resetDeleteState()
                            showPostDeleteDialog()
                        }
                        is DeleteAccountState.Error -> {
                            hideProgress()
                            settingsViewModel.resetDeleteState()
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        }
                        is DeleteAccountState.Idle -> hideProgress()
                    }
                }
            }
        }
    }

    private fun showDeleteAccountConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("アカウントを削除")
            .setMessage(
                "アカウントとサーバー上のすべてのデータを完全に削除します。\n\n" +
                "この操作は取り消せません。\n\n" +
                "本当に削除しますか？"
            )
            .setPositiveButton("削除する") { _, _ ->
                confirmDeleteWithSecondDialog()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun confirmDeleteWithSecondDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("最終確認")
            .setMessage("「削除する」を押すとアカウントが完全に削除されます。")
            .setPositiveButton("削除する") { _, _ ->
                val token = TokenManager.getToken(requireContext()) ?: return@setPositiveButton
                settingsViewModel.deleteAccount(token)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun showProgress() {
        if (progressDialog?.isShowing == true) return
        progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("削除中...")
            setCancelable(false)
            show()
        }
    }

    private fun hideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun updateAccountUi() {
        if (TokenManager.isLoggedIn(requireContext())) {
            val username = TokenManager.getUsername(requireContext())
            binding.tvAccountStatus.text = "ログイン中: $username"
            binding.btnLoginLink.visibility = View.GONE
            binding.btnLogout.visibility = View.VISIBLE
            binding.btnDeleteAccount.visibility = View.VISIBLE
        } else {
            binding.tvAccountStatus.text = "ゲストモード（データはこの端末のみ）"
            binding.btnLoginLink.visibility = View.VISIBLE
            binding.btnLogout.visibility = View.GONE
            binding.btnDeleteAccount.visibility = View.GONE
        }
    }

    private fun showPostDeleteDialog() {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("アカウントを削除しました")
            .setMessage("すべてのサーバーデータを削除しました。\n\nこのままゲストとして続けるか、\n新しいアカウントでログインしてください。")
            .setCancelable(false)
            .setPositiveButton("ログイン / 新規登録") { _, _ ->
                startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            .setNegativeButton("ゲストで続ける") { _, _ ->
                updateAccountUi()
                Snackbar.make(binding.root, "ゲストモードで続けます", Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showStatsDialog() {
        val s = gameViewModel.state.value
        val message = """
            総撃破数:　　　　${formatNumber(s.totalEnemiesDefeated)} 体
            総獲得コイン:　　${formatNumber(s.totalCoinsEarned)}
            現在ステージ:　　${s.stage}
            最高到達ステージ: ${s.maxMilestoneReached * 100}+
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("統計")
            .setMessage(message)
            .setPositiveButton("閉じる", null)
            .show()
    }

    override fun onDestroyView() {
        hideProgress()
        super.onDestroyView()
        _binding = null
    }
}
