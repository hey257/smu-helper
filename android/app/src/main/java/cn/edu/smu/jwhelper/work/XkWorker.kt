package cn.edu.smu.jwhelper.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import cn.edu.smu.jwhelper.core.Prefs
import cn.edu.smu.jwhelper.data.XkRepo
import cn.edu.smu.jwhelper.net.Api
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 后台兜底的自动抢课：每 15 分钟跑一轮。
 * 真正快的抢课靠前台轮询（App 打开时每 2 秒查一次），这里只是「App 没开也不至于完全错过」。
 */
class XkWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {

    override fun doWork(): Result {
        Prefs.init(applicationContext)
        if (!Prefs.xkAuto) return Result.success()
        val cookie = Prefs.cookie
        if (cookie.isNullOrBlank()) {
            Prefs.xkLog = stamp() + " 未登录，跳过"
            return Result.success()
        }
        Api.cookie = cookie
        val wishes = XkStore.parseWishes(Prefs.xkWish)
        if (wishes.isEmpty()) return Result.success()

        var rest = wishes
        val logs = try {
            XkRepo.grabRound(wishes) { }
        } catch (e: Exception) {
            listOf("失败：" + (e.message ?: "未知错误"))
        }
        // 抢到的从清单里摘掉，避免后续每轮重复提交
        try {
            val dm = XkRepo.types().firstOrNull()?.dm
            if (dm != null) {
                val got = XkRepo.chosen(dm).map { it.kcdm }.toSet()
                if (got.isNotEmpty()) {
                    rest = wishes.filterNot { got.contains(it.kcdm) }
                    Prefs.xkWish = XkStore.dumpWishes(rest)
                }
            }
        } catch (_: Exception) {
        }
        if (logs.isNotEmpty()) {
            Prefs.xkLog = stamp() + " " + logs.takeLast(4).joinToString("；")
        }
        return Result.success()
    }

    private fun stamp(): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())
}

/** 清单的序列化，供界面与后台共用 */
object XkStore {
    fun parseWishes(raw: String): List<XkRepo.Wish> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { seg ->
            val p = seg.split("|")
            if (p.size < 2 || p[0].isBlank()) null
            else XkRepo.Wish(p[0], p[1], p.getOrNull(2)?.toIntOrNull() ?: -1)
        }
    }

    fun dumpWishes(list: List<XkRepo.Wish>): String =
        list.joinToString(";") { "${it.kcdm}|${it.kcmc}|${it.qz}" }
}

object XkScheduler {
    private const val WORK = "smu_xk_auto"
    private const val BURST = "smu_xk_burst"

    fun start(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<XkWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun stop(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
        WorkManager.getInstance(ctx).cancelAllWorkByTag(BURST)
    }

    /**
     * 在开放时刻附近布一串单次任务：开放前 10 秒起、每 30 秒一次、共 21 次（覆盖 10 分钟）。
     * 目的是「App 没开、也被系统冻结」时，仍比 15 分钟的周期任务更可能抢到。
     */
    fun burst(ctx: Context, openAtMillis: Long) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelAllWorkByTag(BURST)
        val now = System.currentTimeMillis()
        for (i in 0..20) {
            val at = openAtMillis - 10_000 + i * 30_000L
            val delay = at - now
            if (delay < 0) continue
            val req = OneTimeWorkRequestBuilder<XkWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(BURST)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            wm.enqueue(req)
        }
    }
}
