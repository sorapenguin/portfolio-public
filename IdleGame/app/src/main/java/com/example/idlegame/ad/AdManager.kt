package com.example.idlegame.ad

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.idlegame.BuildConfig

class AdManager(private val context: Context) {

    val isReady: Boolean get() = true

    fun load() {}

    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onFailed: () -> Unit) {
        if (BuildConfig.FAKE_AD_MODE) {
            showMockAd(activity, onRewarded)
        } else {
            // TODO [storeRelease]: 本物AdMob rewarded ad の実装
            showMockAd(activity, onRewarded)
        }
    }

    private fun showMockAd(activity: Activity, onRewarded: () -> Unit) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("📺 広告")
            .setMessage("再生中... 3")
            .setCancelable(false)
            .create()
        dialog.show()

        var count = 3
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                count--
                if (count <= 0) {
                    dialog.dismiss()
                    onRewarded()
                } else {
                    dialog.setMessage("再生中... $count")
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.postDelayed(tick, 1000)
    }
}
