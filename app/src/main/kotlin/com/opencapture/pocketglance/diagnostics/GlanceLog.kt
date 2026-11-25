package com.opencapture.pocketglance.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.opencapture.pocketglance.BuildConfig
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 应用内诊断日志：写 logcat + 环形缓冲 + 文件，平板不连电脑也能导出分享。
 */
object GlanceLog {
    private const val TAG = "GlanceDiag"
    private const val MAX_LINES = 4_000
    private const val FILE_NAME = "pocketglance-diag.log"

    private val lock = ReentrantLock()
    private val ring = ArrayDeque<String>(MAX_LINES)
    private var writer: BufferedWriter? = null
    private var logDir: File? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun install(context: Context) {
        lock.withLock {
            if (writer != null) return
            val dir = File(context.applicationContext.filesDir, "logs").also { it.mkdirs() }
            logDir = dir
            val file = File(dir, FILE_NAME)
            writer =
                BufferedWriter(
                    OutputStreamWriter(FileOutputStream(file, true), StandardCharsets.UTF_8),
                    8_192,
                )
            i(
                "boot",
                "PocketGlance ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
                    "sdk=${Build.VERSION.SDK_INT} ${Build.MANUFACTURER} ${Build.MODEL}",
            )
        }
    }

    fun d(topic: String, message: String) = write('D', topic, message)

    fun i(topic: String, message: String) = write('I', topic, message)

    fun w(topic: String, message: String) = write('W', topic, message)

    fun e(topic: String, message: String, error: Throwable? = null) {
        write('E', topic, if (error == null) message else "$message | ${error.javaClass.simpleName}: ${error.message}")
        error?.stackTraceToString()?.lineSequence()?.take(12)?.forEach { write('E', topic, "  $it") }
    }

    fun snapshot(topic: String, block: () -> String) {
        runCatching { i(topic, block()) }.onFailure { e(topic, "snapshot failed", it) }
    }

    /** 持久日志路径（adb：`…/files/logs/pocketglance-diag.log`）。 */
    fun persistentFile(context: Context): File {
        val dir = logDir ?: File(context.applicationContext.filesDir, "logs").also { it.mkdirs() }
        return File(dir, FILE_NAME)
    }

    /**
     * 打包环形缓冲 + 本进程 logcat + 持久文件。
     * 同时写一份到 externalFilesDir，平板连回电脑后可用 adb pull。
     */
    fun exportBundle(context: Context): File {
        flush()
        val app = context.applicationContext
        val dir = logDir ?: File(app.filesDir, "logs").also { it.mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(dir, "pocketglance-diag-$stamp.txt")
        out.bufferedWriter(StandardCharsets.UTF_8).use { sink ->
            sink.appendLine("=== PocketGlance diagnostics $stamp ===")
            sink.appendLine("app=${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME}")
            sink.appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")
            sink.appendLine()
            sink.appendLine("--- ring buffer ---")
            lock.withLock { ring.forEach(sink::appendLine) }
            sink.appendLine()
            sink.appendLine("--- logcat (own process, last ~2000 lines) ---")
            appendLogcat(sink)
            sink.appendLine()
            sink.appendLine("--- persistent file ---")
            val persistent = File(dir, FILE_NAME)
            if (persistent.exists()) {
                persistent.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.toList().takeLast(3_000).forEach(sink::appendLine)
                }
            }
        }
        val external = app.getExternalFilesDir("logs")?.also { it.mkdirs() }
        if (external != null) {
            runCatching {
                val copy = File(external, out.name)
                out.copyTo(copy, overwrite = true)
                File(dir, FILE_NAME).takeIf { it.exists() }?.copyTo(File(external, FILE_NAME), overwrite = true)
                i("export", "external copy ${copy.absolutePath}")
            }.onFailure { e("export", "external copy failed", it) }
        }
        i("export", "wrote ${out.absolutePath} (${out.length()} bytes)")
        return out
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file,
            )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PocketGlance 诊断日志")
            putExtra(Intent.EXTRA_TEXT, "PocketGlance 诊断日志 ${file.name}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun flush() {
        lock.withLock { runCatching { writer?.flush() } }
    }

    private fun write(level: Char, topic: String, message: String) {
        val line = "${timeFormat.format(Date())} $level/$topic $message"
        when (level) {
            'E' -> Log.e(TAG, "$topic $message")
            'W' -> Log.w(TAG, "$topic $message")
            'I' -> Log.i(TAG, "$topic $message")
            else -> Log.d(TAG, "$topic $message")
        }
        lock.withLock {
            if (ring.size >= MAX_LINES) ring.removeFirst()
            ring.addLast(line)
            runCatching {
                writer?.apply {
                    append(line)
                    newLine()
                }
            }
        }
    }

    private fun appendLogcat(sink: BufferedWriter) {
        val cmd =
            arrayOf(
                "logcat",
                "-d",
                "-t",
                "2000",
                "--pid=${android.os.Process.myPid()}",
            )
        val process = runCatching { Runtime.getRuntime().exec(cmd) }.getOrNull() ?: run {
            sink.appendLine("(logcat unavailable)")
            return
        }
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach(sink::appendLine)
            }
        } catch (error: Exception) {
            sink.appendLine("(logcat read failed: ${error.message})")
        } finally {
            process.destroy()
        }
    }
}
