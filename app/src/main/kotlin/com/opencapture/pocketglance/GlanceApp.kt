package com.opencapture.pocketglance

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.opencapture.opencine.dji.DjiLog
import com.opencapture.pocketglance.session.SwiftCoreBootstrap

class GlanceApp : Application() {
    private val sessionStore = ViewModelStore()
    val model: GlanceModel by lazy {
        ViewModelProvider(sessionStore, ViewModelProvider.AndroidViewModelFactory.getInstance(this))[GlanceModel::class.java]
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            DjiLog.sink = { _, category, code, message ->
                if (category == "decoder" || (category == "feed" && code == "cadence") ||
                    (category == "recovery" && message.startsWith("recovery: action="))
                ) {
                    Log.d("GlanceFeed", "$category/$code $message")
                }
            }
        }
        // 尽早加载无线 JNI，避免点「无线连接」时才发现未加载。
        SwiftCoreBootstrap.isAvailable
    }
}
