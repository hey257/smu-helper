package cn.edu.smu.jwhelper.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 从 GitHub Releases 检查并下载新版本。
 *
 * 走 api.github.com（国内一般能连），下载走 objects.githubusercontent.com（国内经常超时），
 * 所以下载失败时要给「用浏览器打开」的兜底，不能只报个错。
 */
object Update {

    const val REPO = "hey257/smu-helper"

    data class Info(
        val tag: String,
        val notes: String,
        val url: String,
        val size: Long,
        val name: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** 拿最新 Release；网络失败抛异常，由调用方区分「没网」和「已是最新」 */
    fun checkLatest(): Info? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "smu-helper-android")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string() ?: return null
            val o = JSONObject(text)
            val tag = o.optString("tag_name")
            val notes = o.optString("body")
            val assets = o.optJSONArray("assets")
            var url = ""
            var size = 0L
            var name = ""
            if (assets != null && assets.length() > 0) {
                val a = assets.optJSONObject(0)
                url = a.optString("browser_download_url")
                size = a.optLong("size")
                name = a.optString("name")
            }
            if (tag.isBlank() || url.isBlank()) return null
            return Info(tag, notes, url, size, name)
        }
    }

    /** 3.10.0 > 3.9.1，按段比较，不能直接比字符串 */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v")
            .split(".").mapNotNull { it.filter { ch -> ch.isDigit() }.toIntOrNull() }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 下载 APK 到缓存目录，返回文件 */
    fun download(ctx: Context, info: Info, onProgress: (Int) -> Unit): File {
        val req = Request.Builder()
            .url(info.url)
            .header("User-Agent", "smu-helper-android")
            .build()
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val out = File(dir, "smu-helper.apk")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("下载失败：HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("下载失败：空响应")
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != last) {
                                last = p
                                onProgress(p)
                            }
                        }
                    }
                }
            }
        }
        if (out.length() <= 0) throw IllegalStateException("下载到的文件是空的")
        return out
    }

    /** 调起系统安装器 */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }

    fun canInstall(ctx: Context): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.packageManager.canRequestPackageInstalls()
            else true
        } catch (e: Exception) {
            false
        }

    /** 去开「安装未知应用」权限 */
    fun openInstallSettings(ctx: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + ctx.packageName)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (e: Exception) {
            // 忽略
        }
    }

    /** 浏览器打开 Release 页，作为下载不通时的兜底 */
    fun openReleasePage(ctx: Context, url: String) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // 忽略
        }
    }
}
