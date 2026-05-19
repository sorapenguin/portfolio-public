package com.example.idlegame.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.idlegame.MainActivity
import com.example.idlegame.databinding.ActivityLoginBinding
import com.example.idlegame.network.TokenManager
import com.example.idlegame.sync.SyncScheduler
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()
    private var isLoginMode = true
    private var fromSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fromSettings = intent.getBooleanExtra("from_settings", false)
        if (fromSettings) {
            binding.btnCancel.visibility = android.view.View.VISIBLE
            binding.btnCancel.setOnClickListener { finish() }
        }

        setupTabs()
        setupObserver()
        binding.btnSubmit.setOnClickListener { onSubmit() }
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("ログイン"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("クラウドセーブ"))
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                isLoginMode = tab.position == 0
                // ログイン: ユーザー名あり / クラウドセーブ: ユーザー名不要（自動生成）
                binding.tilUsername.visibility = if (isLoginMode) View.VISIBLE else View.GONE
                binding.btnSubmit.text = if (isLoginMode) "ログイン" else "セーブデータを作成"
                viewModel.reset()
                binding.tvError.visibility = View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is AuthState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.btnSubmit.isEnabled = false
                            binding.tvError.visibility = View.GONE
                        }
                        is AuthState.Success -> {
                            TokenManager.saveAuth(
                                this@LoginActivity,
                                state.auth.token,
                                state.auth.userId,
                                state.auth.username
                            )
                            if (state.isNewAccount) {
                                // 自動生成されたユーザー名をログイン時に使うため必ず表示する
                                AlertDialog.Builder(this@LoginActivity)
                                    .setTitle("クラウドセーブ完了")
                                    .setMessage(
                                        "セーブデータを作成しました。\n\n" +
                                        "ユーザー名: ${state.auth.username}\n\n" +
                                        "このユーザー名とパスワードでログインできます。\nメモしておいてください。"
                                    )
                                    .setPositiveButton("ゲームを始める") { _, _ -> goToMain() }
                                    .setCancelable(false)
                                    .show()
                            } else {
                                goToMain()
                            }
                        }
                        is AuthState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnSubmit.isEnabled = true
                            binding.tvError.text = state.message
                            binding.tvError.visibility = View.VISIBLE
                        }
                        is AuthState.Idle -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnSubmit.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun goToMain() {
        SyncScheduler.syncNow(this)
        if (!fromSettings) {
            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
        }
        finish()
    }

    private fun onSubmit() {
        val password = binding.etPassword.text.toString()
        if (password.isBlank()) {
            binding.tvError.text = "パスワードを入力してください"
            binding.tvError.visibility = View.VISIBLE
            return
        }
        if (isLoginMode) {
            val username = binding.etUsername.text.toString().trim()
            if (username.isBlank()) {
                binding.tvError.text = "ユーザー名を入力してください"
                binding.tvError.visibility = View.VISIBLE
                return
            }
            viewModel.login(username, password)
        } else {
            viewModel.cloudSave(password)
        }
    }
}
