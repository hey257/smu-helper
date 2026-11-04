package cn.edu.smu.jwhelper.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import cn.edu.smu.jwhelper.MainActivity
import cn.edu.smu.jwhelper.R
import cn.edu.smu.jwhelper.core.Prefs
import cn.edu.smu.jwhelper.data.Cache
import cn.edu.smu.jwhelper.data.Course
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 桌面「今日课程」小组件。
 *
 * 只读本地缓存（Prefs.cacheCourses / cacheWeek1），不联网 —— 桌面刷新必须是瞬时的。
 * 缓存由 App 在 loadAll / saveCache 时写入，写完后调用 [refreshAll] 通知刷新。
 */
class CourseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 跨天、改时间、重启都要重画，否则显示的还是昨天的课
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {
                refreshAll(context)
                // 重启后闹钟会丢，需要重新排一次
                cn.edu.smu.jwhelper.work.KeepAlive.start(context)
            }
        }
    }

    companion object {

        /** 4x3 大约放得下 4 节课 */
        private const val MAX_ROWS = 4

        private val INK = 0xFF111417.toInt()
        private val SUB = 0xFF7A828B.toInt()
        private val FADE = 0xFFB5BAC0.toInt()
        private val GREEN = 0xFF2F6B4F.toInt()
        private val RED = 0xFF9B2C2C.toInt()

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context.applicationContext, CourseWidgetProvider::class.java)
            )
            ids.forEach { render(context, mgr, it) }
        }

        private fun render(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            Prefs.init(context)
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val today = LocalDate.now()
            val weekday = today.dayOfWeek.value          // 1=周一

            val week = weekOf(today)
            views.setTextViewText(R.id.w_title, "今天")
            views.setTextViewText(
                R.id.w_sub,
                "周" + "一二三四五六日"[weekday - 1] +
                    "  ${today.monthValue}.${String.format("%02d", today.dayOfMonth)}"
            )
            views.setTextViewText(R.id.w_week, if (week > 0) "第 $week 周" else "")

            // 点击整块进 App
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.w_rows, pi)

            val courses = Cache.coursesFrom(Prefs.cacheCourses)
            if (courses.isEmpty()) {
                views.removeAllViews(R.id.w_rows)
                views.setViewVisibility(R.id.w_empty, View.VISIBLE)
                views.setTextViewText(R.id.w_empty, "打开 App 登录后显示课表")
                views.setViewVisibility(R.id.w_more, View.GONE)
                mgr.updateAppWidget(widgetId, views)
                return
            }

            val todayCourses = courses
                .filter { it.weekDay == weekday }
                .filter { it.weeks.isEmpty() || week <= 0 || it.weeks.contains(week) }
                .sortedWith(compareBy({ it.startP }, { if (it.mine) 1 else 0 }))

            if (todayCourses.isEmpty()) {
                views.removeAllViews(R.id.w_rows)
                views.setViewVisibility(R.id.w_empty, View.VISIBLE)
                views.setTextViewText(R.id.w_empty, "今天没有课")
                views.setViewVisibility(R.id.w_more, View.GONE)
                mgr.updateAppWidget(widgetId, views)
                return
            }

            views.setViewVisibility(R.id.w_empty, View.GONE)
            views.removeAllViews(R.id.w_rows)

            val now = LocalTime.now()
            val show = todayCourses.take(MAX_ROWS)
            show.forEach { c ->
                views.addView(R.id.w_rows, rowOf(context, c, now, week))
            }

            val rest = todayCourses.size - show.size
            if (rest > 0) {
                views.setViewVisibility(R.id.w_more, View.VISIBLE)
                views.setTextViewText(R.id.w_more, "还有 $rest 节 · 点开看全部")
            } else {
                views.setViewVisibility(R.id.w_more, View.GONE)
            }

            mgr.updateAppWidget(widgetId, views)
        }

        private fun rowOf(context: Context, c: Course, now: LocalTime, week: Int): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            val passed = isOver(c, now)
            val nameColor = if (passed) FADE else INK
            val subColor = if (passed) FADE else SUB

            row.setTextViewText(R.id.r_time, c.startHm)
            row.setTextColor(R.id.r_time, subColor)
            row.setTextViewText(R.id.r_name, c.name)
            row.setTextColor(R.id.r_name, nameColor)

            val meta = StringBuilder()
            if (c.periodText.isNotBlank()) meta.append(c.periodText)
            when {
                c.isExam -> {
                    row.setViewVisibility(R.id.r_tag, View.VISIBLE)
                    row.setTextViewText(R.id.r_tag, "考试")
                    row.setTextColor(R.id.r_tag, RED)
                }
                c.online -> {
                    row.setViewVisibility(R.id.r_tag, View.VISIBLE)
                    row.setTextViewText(R.id.r_tag, "线上")
                    row.setTextColor(R.id.r_tag, GREEN)
                }
                else -> row.setViewVisibility(R.id.r_tag, View.GONE)
            }
            val room = c.placeIn(week)
            if (room.isNotBlank() && !c.online) {
                if (meta.isNotEmpty()) meta.append(" · ")
                meta.append(room)
            }
            row.setTextViewText(R.id.r_meta, meta.toString())
            row.setTextColor(R.id.r_meta, subColor)
            return row
        }

        /** 这节课是否已经结束 */
        private fun isOver(c: Course, now: LocalTime): Boolean {
            return try {
                val end = LocalTime.parse(c.endTime.take(8))
                now.isAfter(end)
            } catch (e: Exception) {
                false
            }
        }

        /** 今天是第几周；没有第 1 周日期时退回缓存里的当前周 */
        private fun weekOf(today: LocalDate): Int {
            val w1 = Prefs.cacheWeek1
            if (w1.isNotBlank()) {
                try {
                    val n = ChronoUnit.WEEKS.between(LocalDate.parse(w1), today).toInt() + 1
                    if (n in 1..30) return n
                } catch (e: Exception) {
                    // 落到下面用缓存值
                }
            }
            return Prefs.cacheCurWeek
        }
    }
}
