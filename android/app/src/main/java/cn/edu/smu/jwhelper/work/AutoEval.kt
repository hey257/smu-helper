package cn.edu.smu.jwhelper.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import cn.edu.smu.jwhelper.core.Prefs
import cn.edu.smu.jwhelper.data.Repo
import cn.edu.smu.jwhelper.net.Api
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 每天定时跑一次“随堂评价” */
class AutoEvalWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {

    override fun doWork(): Result {
        Prefs.init(applicationContext)
        if (!Prefs.autoClassEval) return Result.success()
        val cookie = Prefs.cookie
        if (cookie.isNullOrBlank()) {
            Prefs.autoLog = stamp() + " 未登录，跳过"
            Scheduler.schedule(applicationContext)
            return Result.success()
        }
        Api.cookie = cookie
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        if (Prefs.lastEvalDate == today) {
            Scheduler.schedule(applicationContext)
            return Result.success()
        }
        val result = try {
            Repo.runClassEvalForDate(Prefs.xnxqdm ?: "", today, Prefs.evalText)
        } catch (e: Exception) {
            "失败：" + (e.message ?: "未知错误")
        }
        Prefs.autoLog = "$today $result"
        Prefs.lastEvalDate = today
        Scheduler.schedule(applicationContext)   // 排下一天
        return Result.success()
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
}

object Scheduler {
    private const val WORK = "smu_auto_class_eval"

    /** 开关或时间变动后都要调一次 */
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelUniqueWork(WORK)
        if (!Prefs.autoClassEval) return
        val req = OneTimeWorkRequestBuilder<AutoEvalWorker>()
            .setInitialDelay(delayToNext(Prefs.autoHour, Prefs.autoMinute), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
    }

    fun delayToNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_MONTH, 1)
        return target.timeInMillis - now.timeInMillis
    }
}
