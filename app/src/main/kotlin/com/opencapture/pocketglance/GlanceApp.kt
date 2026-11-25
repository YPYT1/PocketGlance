package com.opencapture.pocketglance

import android.app.Application
import com.opencapture.pocketglance.session.SwiftCoreBootstrap

class GlanceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 尽早加载无线 JNI，避免点「无线连接」时才发现未加载。
        SwiftCoreBootstrap.isAvailable
    }
}
