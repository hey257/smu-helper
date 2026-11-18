package cn.edu.smu.jwhelper.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import cn.edu.smu.jwhelper.data.Course
import java.io.File
import java.time.LocalDate

/**
 * 把课表导成 iCalendar（.ics）。
 *
 * 每节课按「第几周 + 星期几」换算成真实日期，逐条生成 VEVENT —— 不用 RRULE，
 * 因为课表的周次经常是跳着的（3,5,7,9…），RRULE 很难精确表达。
 */
object Ics {

    /** 生成并调起系统分享，用户选「日历」就能直接导入 */
    fun exportAndShare(ctx: Context, courses: List<Course>, week1: String, calName: String) {
        val file = build(ctx, courses, week1, calName) ?: return
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, calName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(
            Intent.createChooser(send, "导出课表").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun build(ctx: Context, courses: List<Course>, week1: String, calName: String): File? {
        val base = try {
            LocalDate.parse(week1)
        } catch (e: Exception) {
            return null
        }

        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//SMU Helper//Timetable//CN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")
        sb.append("X-WR-CALNAME:").append(esc(calName)).append("\r\n")

        // 同一门课同一时段可能有多条（不同教学班），日历里不该重复
        val seen = HashSet<String>()
        var seq = 0

        // 自己的班先占位，去重时留下的就是自己的教室
        courses.sortedByDescending { it.mine }.forEach { c ->
            val start = hm(c.startTime) ?: return@forEach
            val end = hm(c.endTime) ?: return@forEach
            c.weeks.forEach { w ->
                if (w <= 0) return@forEach
                val date = base.plusDays(((w - 1) * 7 + (c.weekDay - 1)).toLong())
                val day = date.toString().replace("-", "")
                val key = "$day|$start|${c.name}"
                if (!seen.add(key)) return@forEach

                seq++
                val uid = "smu-$seq-${c.kcrwdm.ifBlank { c.name.hashCode().toString() }}@smu-helper"
                val desc = buildString {
                    if (c.teacher.isNotBlank()) append("教师：").append(c.teacher)
                    if (c.stage.isNotBlank()) append(if (isNotEmpty()) "\\n" else "").append("环节：").append(c.stage)
                    if (c.content.isNotBlank()) append(if (isNotEmpty()) "\\n" else "").append("内容：").append(c.content)
                    if (c.periodText.isNotBlank()) append(if (isNotEmpty()) "\\n" else "").append(c.periodText)
                }

                sb.append("BEGIN:VEVENT\r\n")
                sb.append("UID:").append(esc(uid)).append("\r\n")
                sb.append("DTSTAMP:").append(utcStamp()).append("\r\n")
                sb.append("DTSTART;TZID=Asia/Shanghai:").append(day).append("T").append(start).append("\r\n")
                sb.append("DTEND;TZID=Asia/Shanghai:").append(day).append("T").append(end).append("\r\n")
                sb.append("SUMMARY:").append(esc(c.name)).append("\r\n")
                if (c.placeIn(w).isNotBlank()) {
                    sb.append("LOCATION:").append(esc(c.placeIn(w))).append("\r\n")
                }
                if (desc.isNotBlank()) {
                    sb.append("DESCRIPTION:").append(esc(desc)).append("\r\n")
                }
                sb.append("END:VEVENT\r\n")
            }
        }

        sb.append("END:VCALENDAR\r\n")

        return try {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "课表.ics")
            f.writeText(sb.toString(), Charsets.UTF_8)
            f
        } catch (e: Exception) {
            null
        }
    }

    /** "08:00:00" -> "080000" */
    private fun hm(t: String): String? {
        val parts = t.trim().split(":")
        if (parts.size < 2) return null
        val h = parts[0].padStart(2, '0')
        val m = parts[1].padStart(2, '0')
        return h + m + "00"
    }

    private fun utcStamp(): String =
        java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
            .format(java.util.Date())

    /** iCalendar 里 , ; \ 和换行都要转义 */
    private fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
}
