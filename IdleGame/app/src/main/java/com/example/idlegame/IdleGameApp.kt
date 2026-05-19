package com.example.idlegame

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.idlegame.ad.AdManager
import com.example.idlegame.data.GameRepository
import com.example.idlegame.data.local.GameDatabase
import com.example.idlegame.network.ApiRepository
import com.example.idlegame.settings.SettingsRepository
import com.example.idlegame.sync.SyncScheduler
import com.google.android.gms.ads.MobileAds

class IdleGameApp : Application() {
    lateinit var repository: GameRepository
        private set
    lateinit var apiRepository: ApiRepository
        private set
    lateinit var adManager: AdManager
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        val db = GameDatabase.getInstance(this)
        repository = GameRepository(db.gameStateDao())
        apiRepository = ApiRepository()
        settingsRepository = SettingsRepository(this)
        if (com.example.idlegame.BuildConfig.ADS_ENABLED) MobileAds.initialize(this)
        adManager = AdManager(this)
        SyncScheduler.schedulePeriodicSync(this)
    }
}
