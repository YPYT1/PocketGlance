package com.opencapture.pocketglance.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.opencapture.pocketglance.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** GitHub Releases 在线更新：查 latest → 下 APK → 调起系统安装。 */
object AppUpdate {
    private const val OWNER = "YPYT1"
    private const val REPO = "PocketGlance"
    private const val RELEASES_LATEST = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    data class Release(
        val tag: String,
        val name: String,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val versionCode: Int?,
    )

    sealed class CheckResult {
        data class UpToDate(val current: String) : CheckResult()
        data class Available(val release: Release) : CheckResult()
        data class Failed(val message: String) : CheckResult()
    }

    suspend fun check(): CheckResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(RELEASES_LATEST).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "PocketGlance/${BuildConfig.VERSION_NAME}")
                }
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        return@runCatching CheckResult.Failed("无法读取 GitHub Release（HTTP ${connection.responseCode}）")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name").trim().removePrefix("v")
                    if (tag.isEmpty()) return@runCatching CheckResult.Failed("Release 没有 tag")
                    val assets = json.optJSONArray("assets") ?: return@runCatching CheckResult.Failed("Release 没有附件")
                    var apkUrl = ""
                    var apkName = ""
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            apkName = name
                            break
                        }
                    }
                    if (apkUrl.isEmpty()) return@runCatching CheckResult.Failed("Release 里没有 APK")
                    val remoteCode = versionCodeFrom(tag, json.optString("body"))
                    val newer =
                        when {
                            remoteCode != null -> remoteCode > BuildConfig.VERSION_CODE
                            else -> compareVersionNames(tag, BuildConfig.VERSION_NAME.substringBefore('-')) > 0
                        }
                    if (!newer) {
                        return@runCatching CheckResult.UpToDate(BuildConfig.VERSION_NAME)
                    }
                    CheckResult.Available(
                        Release(
                            tag = tag,
                            name = json.optString("name").ifBlank { tag },
                            notes = json.optString("body").orEmpty(),
                            apkUrl = apkUrl,
                            apkName = apkName.ifBlank { "PocketGlance-$tag.apk" },
                            versionCode = remoteCode,
                        ),
                    )
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { CheckResult.Failed(it.message ?: "检查更新失败") }
        }

    suspend fun downloadApk(context: Context, release: Release): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
            val out = File(dir, release.apkName)
            val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 120_000
                setRequestProperty("User-Agent", "PocketGlance/${BuildConfig.VERSION_NAME}")
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode !in 200..299) {
                    error("下载失败 HTTP ${connection.responseCode}")
                }
                connection.inputStream.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }
            out
        }

    fun canRequestInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openUnknownSourcesSettings(context: Context) {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installApk(context: Context, apk: File) {
        val uri =
            FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun displayVersion(): String =
        "PocketGlance ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）"

    /** Release 正文里的 `versionCode N`，或 tag `1.2.1` 无法推断 code 时返回 null。 */
    private fun versionCodeFrom(tag: String, body: String): Int? {
        val fromBody = Regex("""versionCode\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (fromBody != null) return fromBody
        return null
    }

    /** 简单 semver 比较：`1.2.1` vs `1.2.0` → 正数表示 left 更新。 */
    private fun compareVersionNames(left: String, right: String): Int {
        fun parts(value: String) =
            value.trim().removePrefix("v").split('.', '-', '_')
                .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val a = parts(left)
        val b = parts(right)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val d = (a.getOrNull(i) ?: 0) - (b.getOrNull(i) ?: 0)
            if (d != 0) return d
        }
        return 0
    }
}
