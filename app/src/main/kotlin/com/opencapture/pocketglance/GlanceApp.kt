package com.opencapture.pocketglance

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.opencapture.opencine.dji.DjiLog
import com.opencapture.pocketglance.diagnostics.GlanceLog
import com.opencapture.pocketglance.session.SwiftCoreBootstrap

class GlanceApp : Application() {
    private val sessionStore = ViewModelStore()
    val model: GlanceModel by lazy {
        ViewModelProvider(sessionStore, ViewModelProvider.AndroidViewModelFactory.getInstance(this))[GlanceModel::class.java]
    }

    override fun onCreate() {
        super.onCreate()
        GlanceLog.install(this)
        DjiLog.sink = { _, category, code, message ->
            val line = "$category/$code $message"
            when {
                category == "decoder" || category == "feed" || category == "recovery" ||
                    category == "media" || category == "usb" || category == "datalink" ->
                    GlanceLog.d("opencine", line)
                else -> GlanceLog.d("opencine", line)
            }
        }
        // 尽早加载无线 JNI，避免点「无线连接」时才发现未加载。
        val jni = SwiftCoreBootstrap.isAvailable
        GlanceLog.i("boot", "swift jni available=$jni")
    }
}
