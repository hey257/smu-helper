package cn.edu.smu.jwhelper.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import cn.edu.smu.jwhelper.core.Prefs
import cn.edu.smu.jwhelper.net.Api
import java.util.concurrent.TimeUnit

/**
 * 定时访问一次页面，让服务端会话不因闲置被回收。
 * 学校的登录态是服务端 session，闲置就失效，这是唯一不重新输入密码的办法。
 */
class KeepAliveWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        Prefs.init(applicationContext)
        val c = Prefs.cookie
        if (c.isNullOrBlank()) return Result.success()
        Api.cookie = c
        val ok = Api.isLoggedIn()
        Prefs.sessionAlive = ok
        Prefs.lastAliveAt =
            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                .format(java.util.Date())
        return Result.success()
    }
}

object KeepAlive {
    private const val WORK = "smu_session_keepalive"

    fun start(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun stop(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
    }
}
