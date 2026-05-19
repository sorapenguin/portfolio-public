package com.example.idlegame

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.idlegame.databinding.ActivityMainBinding
import com.example.idlegame.network.TokenManager
import com.example.idlegame.sync.SyncScheduler
import com.example.idlegame.ui.main.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application as IdleGameApp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        setupActionBarWithNavController(navController)
        binding.bottomNav.setupWithNavController(navController)
        // nav_achievement のような非ボトムナビ画面から「ホーム」を再タップした場合、
        // setupWithNavController が「再選択」と判定して何もしないため、手動でポップする
        binding.bottomNav.setOnItemReselectedListener { item ->
            navController.popBackStack(item.itemId, false)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isIdleSleeping.collect { sleeping ->
                    binding.sleepOverlay.visibility = if (sleeping) View.VISIBLE else View.GONE
                }
            }
        }

        binding.sleepOverlay.setOnClickListener {
            viewModel.recordInteraction()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.pauseTicking()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumeTicking()
        if (TokenManager.isLoggedIn(this)) {
            SyncScheduler.syncNow(this)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            viewModel.recordInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }
}
