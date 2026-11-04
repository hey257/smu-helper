package cn.edu.smu.jwhelper.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, val msg: String) : Exception(msg)

/**
 * 走学校网页端（/new/...）。登录由 WebView 完成，这里只带 Cookie 请求数据。
 * 注意：/dev-api/appapi 那套接口只认 token，网页 Cookie 进不去，所以全部走 PC 端。
 */
object Api {

    const val ROOT = "https://zhjw.smu.edu.cn"

    private val FORM = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    @Volatile
    var cookie: String? = null

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private fun headers(b: Request.Builder, referer: String?, xhr: Boolean) {
        b.header("User-Agent", UA)
        b.header("Accept", if (xhr) "application/json, text/javascript, */*; q=0.01" else "text/html,application/xhtml+xml,*/*;q=0.8")
        b.header("Accept-Language", "zh-CN,zh;q=0.9")
        if (xhr) b.header("X-Requested-With", "XMLHttpRequest")
        b.header("Referer", referer ?: "$ROOT/new/welcome.page?ui=new")
        cookie?.takeIf { it.isNotBlank() }?.let { b.header("Cookie", it) }
    }

    private fun exec(req: Request): String {
        val resp = try {
            client.newCall(req).execute()
        } catch (e: Exception) {
            throw ApiException(-1, "网络连接失败：${e.message ?: "请检查网络"}")
        }
        resp.use {
            val text = it.body?.string() ?: ""
            if (!it.isSuccessful) throw ApiException(it.code, "服务器返回 ${it.code}")
            return text
        }
    }

    /** 取网页 HTML */
    fun getHtml(path: String, referer: String? = null): String {
        val b = Request.Builder().url(if (path.startsWith("http")) path else ROOT + path)
        headers(b, referer, false)
        return exec(b.build())
    }

    /** 表单 POST，返回原始文本 */
    fun postForm(path: String, params: Map<String, String>, referer: String? = null): String {
        val body = params.entries.joinToString("&") {
            enc(it.key) + "=" + enc(it.value)
        }
        val b = Request.Builder().url(if (path.startsWith("http")) path else ROOT + path)
        headers(b, referer, true)
        b.post(body.toRequestBody(FORM))
        return exec(b.build())
    }

    /** 表单 POST，返回 JSON */
    fun postJson(path: String, params: Map<String, String>, referer: String? = null): JSONObject {
        val text = postForm(path, params, referer)
        return parseJson(text)
    }

    fun getJson(path: String, params: Map<String, String>, referer: String? = null): JSONObject {
        val q = params.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        val url = (if (path.startsWith("http")) path else ROOT + path) + (if (q.isEmpty()) "" else "?$q")
        return parseJson(getHtml(url, referer))
    }

    private fun parseJson(text: String): JSONObject {
        val t = text.trim().removePrefix("\uFEFF")
        return try {
            JSONObject(t)
        } catch (e: Exception) {
            if (t.startsWith("<!DOCTYPE") || t.startsWith("<")) {
                throw ApiException(-2, "登录状态已失效，请重新登录")
            }
            throw ApiException(-3, "无法解析返回：${t.take(60)}")
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /**
     * 登录态判定。返回 null 表示**网络失败，无法判断** —— 这种情况绝不能当成掉线，
     * 否则网差的时候一启动就被踢回登录页。
     */
    fun checkSession(): Boolean? {
        return try {
            val html = getHtml("/new/student/xsgrkb/main.page")
            html.contains("week.page?xnxqdm=") || html.contains("id='xnxqdm'")
        } catch (e: ApiException) {
            if (e.code == -1) null else false
        } catch (e: Exception) {
            null
        }
    }

    /** 兼容旧调用：无法判断时按「已登录」处理，避免误踢 */
    fun isLoggedIn(): Boolean = checkSession() ?: true

    fun logout() {
        cookie = null
    }
}
