package com.opencapture.pocketglance.ui

import com.opencapture.pocketglance.connection.ConnectionMode
import com.opencapture.pocketglance.connection.StartupPreference
import com.opencapture.pocketglance.gallery.MediaKind

/** 界面用中文文案。 */
object GlanceCopy {
    fun mode(mode: ConnectionMode?): String =
        when (mode) {
            ConnectionMode.Wireless -> "无线"
            ConnectionMode.Wired -> "有线"
            null -> "未连接"
        }

    fun startup(pref: StartupPreference): String =
        when (pref) {
            StartupPreference.Wireless -> "无线"
            StartupPreference.Wired -> "有线"
            StartupPreference.Last -> "上次"
        }

    fun kind(kind: MediaKind): String =
        when (kind) {
            MediaKind.Image -> "图片"
            MediaKind.Video -> "视频"
            MediaKind.Motion -> "动图"
        }
}
