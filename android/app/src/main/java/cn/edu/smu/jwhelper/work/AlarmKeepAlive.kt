package cn.edu.smu.jwhelper.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cn.edu.smu.jwhelper.core.Prefs

/**
 * 用 AlarmManager 穿透 Doze 保活。
 *
 * WorkManager 的 15 分钟周期任务在打瞌睡/省电模式下会被推迟到几小时一次，
 * 学校的 session 早就闲置回收了。setAndAllowWhileIdle 不需要额外权限，
 * 且能在 Doze 里唤醒（系统限频约 9 分钟一次，15 分钟的间隔刚好合适）。
 */
object AlarmKeepAlive {
    private const val REQ = 20261103
    private const val INTERVAL_MS = 15 * 60 * 1000L

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = System.currentTimeMillis() + INTERVAL_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx))
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pending(ctx))
            }
        } catch (e: Exception) {
            // 系统拒绝就不排，WorkManager 那层还在
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            am.cancel(pending(ctx))
        } catch (e: Exception) {
            // 忽略
        }
    }

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, KeepAliveReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx, REQ, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** 闹钟响一次：跑一次心跳，然后排下一次 */
class KeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)

        val cookie = Prefs.cookie
        if (!cookie.isNullOrBlank()) {
            val req = OneTimeWorkRequestBuilder<KeepAliveWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }

        // 无论这次有没有跑，都把下一次排上
        AlarmKeepAlive.schedule(context)
    }
}
