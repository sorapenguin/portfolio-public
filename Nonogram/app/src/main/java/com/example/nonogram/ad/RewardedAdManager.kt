package com.example.nonogram.ad

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.example.nonogram.BuildConfig
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class RewardedAdManager(context: Context) {

    private val appContext = context.applicationContext
    private var rewardedAd: RewardedAd? = null

    init {
        MobileAds.initialize(appContext)
        load()
    }

    private fun load() {
        RewardedAd.load(
            appContext,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            },
        )
    }

    fun show(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit,
    ) {
        val ad = rewardedAd
        if (ad == null) {
            onDismissed()
            load()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                load()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                load()
                onDismissed()
            }
        }

        ad.show(activity) { onRewarded() }
    }

    val isLoaded: Boolean get() = rewardedAd != null
}
