package cn.edu.smu.jwhelper.core

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore 里的一把 AES 密钥加密本地保存的密码。
 * 密钥不可导出，只在本机使用。
 */
object Secret {
    private const val ALIAS = "smu_helper_key"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    fun decrypt(stored: String): String {
        return try {
            val parts = stored.split(":")
            if (parts.size != 2) return ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("smu_helper", Context.MODE_PRIVATE)
    }

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    var cookie: String?
        get() = sp.getString("cookie", null)
        set(v) = sp.edit().putString("cookie", v).apply()

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(v) = sp.edit().putString("username", v).apply()

    var password: String
        get() = Secret.decrypt(sp.getString("pwd", "") ?: "")
        set(v) = sp.edit().putString("pwd", Secret.encrypt(v)).apply()

    /** 当前学期代码，如 2026-2027-1 */
    var xnxqdm: String?
        get() = sp.getString("xnxqdm", null)
        set(v) = sp.edit().putString("xnxqdm", v).apply()

    var xnxqmc: String
        get() = sp.getString("xnxqmc", "") ?: ""
        set(v) = sp.edit().putString("xnxqmc", v).apply()

    /** 评课固定选项：选择第几个选项（0 开始，-1 表示选最后一个） */
    var evalOptionIndex: Int
        get() = sp.getInt("eval_opt", -1)
        set(v) = sp.edit().putInt("eval_opt", v).apply()

    /** 数值型题目填的百分比 */
    var evalNumber: Int
        get() = sp.getInt("eval_num", 100)
        set(v) = sp.edit().putInt("eval_num", v).apply()

    /** 主观题固定填写的文字（可空） */
    var evalText: String
        get() = sp.getString("eval_text", "") ?: ""
        set(v) = sp.edit().putString("eval_text", v).apply()

    /** 随堂评价自动执行总开关 */
    var autoClassEval: Boolean
        get() = sp.getBoolean("auto_class", false)
        set(v) = sp.edit().putBoolean("auto_class", v).apply()

    /** 自动执行时间（分钟数，从 0 点算起），默认 21:30 */
    var autoHour: Int
        get() = sp.getInt("auto_hour", 21)
        set(v) = sp.edit().putInt("auto_hour", v).apply()

    var autoMinute: Int
        get() = sp.getInt("auto_min", 30)
        set(v) = sp.edit().putInt("auto_min", v).apply()

    var lastEvalDate: String
        get() = sp.getString("last_eval_date", "") ?: ""
        set(v) = sp.edit().putString("last_eval_date", v).apply()

    /** 是否已经问过通知权限 */
    var askedNotif: Boolean
        get() = sp.getBoolean("asked_notif", false)
        set(v) = sp.edit().putBoolean("asked_notif", v).apply()

    /** 最近一次后台检查登录态的时间 */
    var lastAliveAt: String
        get() = sp.getString("last_alive_at", "") ?: ""
        set(v) = sp.edit().putString("last_alive_at", v).apply()

    /** 前台保活服务是否在跑 */
    var keepAliveRunning: Boolean
        get() = sp.getBoolean("keepalive_running", false)
        set(v) = sp.edit().putBoolean("keepalive_running", v).apply()

    /** 会话是否还活着（心跳结果） */
    var sessionAlive: Boolean
        get() = sp.getBoolean("session_alive", true)
        set(v) = sp.edit().putBoolean("session_alive", v).apply()

    /** 最近一次自动执行的结果摘要 */
    var autoLog: String
        get() = sp.getString("auto_log", "") ?: ""
        set(v) = sp.edit().putString("auto_log", v).apply()

    /** 隐藏“不用到场”的课程 */
    var hideOffline: Boolean
        get() = sp.getBoolean("hide_offline", false)
        set(v) = sp.edit().putBoolean("hide_offline", v).apply()

    // ---------- 本地缓存（打开即有内容） ----------

    var cacheSemesters: String
        get() = sp.getString("cache_semesters", "") ?: ""
        set(v) = sp.edit().putString("cache_semesters", v).apply()

    var cacheCourses: String
        get() = sp.getString("cache_courses", "") ?: ""
        set(v) = sp.edit().putString("cache_courses", v).apply()

    var cacheExams: String
        get() = sp.getString("cache_exams", "") ?: ""
        set(v) = sp.edit().putString("cache_exams", v).apply()

    var cacheWkh: String
        get() = sp.getString("cache_wkh", "") ?: ""
        set(v) = sp.edit().putString("cache_wkh", v).apply()

    var cacheXnxqdm: String
        get() = sp.getString("cache_xnxqdm", "") ?: ""
        set(v) = sp.edit().putString("cache_xnxqdm", v).apply()

    var cacheXnxqmc: String
        get() = sp.getString("cache_xnxqmc", "") ?: ""
        set(v) = sp.edit().putString("cache_xnxqmc", v).apply()

    var cacheWeek1: String
        get() = sp.getString("cache_week1", "") ?: ""
        set(v) = sp.edit().putString("cache_week1", v).apply()

    var cacheCurWeek: Int
        get() = sp.getInt("cache_cur_week", 0)
        set(v) = sp.edit().putInt("cache_cur_week", v).apply()

    /** 课表缓存对应哪个学期，避免切换学期后串数据 */
    var cacheKey: String
        get() = sp.getString("cache_key", "") ?: ""
        set(v) = sp.edit().putString("cache_key", v).apply()

    // ---------- 选课 ----------

    /** 自动抢课总开关 */
    var xkAuto: Boolean
        get() = sp.getBoolean("xk_auto", false)
        set(v) = sp.edit().putBoolean("xk_auto", v).apply()

    /** 抢课清单，格式 kcdm|课名|权重;... */
    var xkWish: String
        get() = sp.getString("xk_wish", "") ?: ""
        set(v) = sp.edit().putString("xk_wish", v).apply()

    /** 抢课日志 */
    var xkLog: String
        get() = sp.getString("xk_log", "") ?: ""
        set(v) = sp.edit().putString("xk_log", v).apply()

    fun clearLogin() {
        sp.edit().remove("token").remove("pwd").remove("cookie").apply()
    }
}
