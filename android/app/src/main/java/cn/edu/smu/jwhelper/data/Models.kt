package cn.edu.smu.jwhelper.data

import org.json.JSONArray
import org.json.JSONObject

/** 一节课（来自周课表接口，一条记录可能覆盖多个周次） */
data class Course(
    val name: String,
    val teacher: String,
    val place: String,
    val weekDay: Int,          // 1=周一
    val startTime: String,     // HH:mm:ss
    val endTime: String,
    val weeks: List<Int>,
    val startP: Int,             // 起始节次
    val endP: Int,               // 结束节次
    val kcbh: String,
    val stage: String,         // 教学环节：理论 / 自主学习 / 实验…
    val classname: String,
    val noRoom: Boolean        // 不用场地
) {
    val online: Boolean
        get() = !isExam && (stage.contains("自主") || noRoom || place.isBlank())

    /** 教学环节是“考试（理论）/考试（实操）”这种 */
    val isExam: Boolean
        get() = stage.contains("考试")

    val startHm: String get() = startTime.take(5)
    val endHm: String get() = endTime.take(5)

    /** 例如 "第1-2节"、 "第3节" */
    val periodText: String
        get() = when {
            startP <= 0 -> ""
            startP == endP -> "第${startP}节"
            else -> "第${startP}-${endP}节"
        }

    val specialTag: String
        get() = when {
            isExam -> "考试"
            stage.contains("自主") -> "自主学习"
            noRoom -> "不用场地"
            place.isBlank() -> "无地点"
            else -> ""
        }
}

data class ExamItem(
    val course: String,
    val date: String,
    val time: String,
    val place: String,
    val seat: String,
    val type: String,
    val note: String,
    val wkh: String
)

/** 待评价的课/课程 */
data class PjItem(
    val kind: Int,                 // 0=课程评价 1=随堂评价
    val title: String,
    val subtitle: String,
    val tag: String,               // 已评 / 未结课 / 空
    val params: Map<String, String>,
    val pagePath: String,
    val raw: JSONObject
)

data class Opt(
    val zbxmdm: String,
    val mc: String,
    val fz: Double,
    val index: Int
)

data class Question(
    val no: Int,
    val zbdm: String,
    val text: String,
    val txdm: String,
    val required: Boolean,
    val fullScore: Double,
    val options: List<Opt>
) {
    val typeName: String
        get() = when (txdm) {
            "1" -> "单选"
            "2" -> "多选"
            "3" -> "填空"
            "4" -> "滑块"
            "5" -> "星级"
            "6", "7", "8" -> "打分"
            "9" -> "日期"
            else -> "其他"
        }
}

data class Answer(
    val dt: JSONArray,
    val wtpf: Double,
    val pfsm: String,
    val lines: List<String>
)

/** 问卷页解析结果 */
data class Questionnaire(
    val questions: List<Question>,
    val submitPath: String,
    val submitParams: Map<String, String>,
    val hasR: Boolean,
    val minTotal: Double,
    val maxTotal: Double,
    val noteMin: Double,
    val noteMax: Double
)

object Parse {

    fun unescape(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun stripTags(s: String): String = unescape(s.replace(Regex("<[^>]+>"), ""))

    fun json(s: JSONObject, key: String): String {
        val v = s.optString(key, "")
        return if (v == "null") "" else v
    }

    /** "13,17" → [13,17] */
    fun weeks(s: String): List<Int> =
        s.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()

    /** 解析问卷 HTML */
    fun questionnaire(html: String): Questionnaire {
        val questions = ArrayList<Question>()
        // 提交参数
        val submitPath = Regex("entss\\.post\\(\\s*'([^']*savePj[^']*)'").find(html)?.groupValues?.get(1) ?: ""
        val params = LinkedHashMap<String, String>()
        val objBody = if (submitPath.isNotBlank()) {
            val i = html.indexOf(submitPath)
            val start = html.indexOf("{", i)
            val end = html.indexOf("}", start)
            if (start > 0 && end > start) html.substring(start, end) else ""
        } else ""
        Regex("(\\w+)\\s*:\\s*'([^']*)'").findAll(objBody).forEach {
            params[it.groupValues[1]] = it.groupValues[2]
        }

        // 总分区间
        val cfgIdx = html.indexOf("thisQuestionnaireHasR")
        val cfgTag = if (cfgIdx > 0) html.substring(cfgIdx, minOf(html.length, cfgIdx + 400)) else ""
        fun attr(name: String): Double? =
            Regex("data-$name=\"([^\"]*)\"").find(cfgTag)?.groupValues?.get(1)?.toDoubleOrNull()
        val hasR = Regex("value=\"1\"").find(cfgTag) != null
        val minTotal = attr("zdf") ?: 0.0
        val maxTotal = attr("zgf") ?: 100.0
        val noteMin = attr("smzdf") ?: -2000.0
        val noteMax = attr("smzgf") ?: 2000.0

        // 逐题解析
        val parts = html.split("<div class=\"question\"")
        var no = 0
        for (p in parts.drop(1)) {
            val block = p.substringBefore("</form>")
            val head = block.substringBefore(">")
            val sfbt = Regex("data-sfbt=\"(\\d)\"").find(head)?.groupValues?.get(1) == "1"
            val txdm = Regex("data-txdm=\"(\\d+)\"").find(head)?.groupValues?.get(1) ?: ""
            val zbdm = Regex("data-zbdm=\"([^\"]*)\"").find(head)?.groupValues?.get(1) ?: ""
            val h3 = Regex("<h3>(.*?)</h3>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1) ?: ""
            val text = stripTags(h3)
            no++

            val options = ArrayList<Opt>()
            var full = 0.0
            when (txdm) {
                "5" -> {
                    full = Regex("data-fz=\"([\\d.]+)\"").find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                    val wtxm = Regex("data-wtxm='(\\[.*?\\])'", RegexOption.DOT_MATCHES_ALL)
                        .find(block)?.groupValues?.get(1)
                    if (wtxm != null) {
                        try {
                            val arr = JSONArray(wtxm)
                            for (k in 0 until arr.length()) {
                                val o = arr.optJSONObject(k) ?: continue
                                options.add(
                                    Opt(
                                        o.optString("zbxmdm"),
                                        o.optString("zbxmmc"),
                                        o.optString("fzbl").toDoubleOrNull() ?: 0.0,
                                        k + 1
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // 忽略
                        }
                    }
                }
                "1", "2" -> {
                    val re = Regex("<input[^>]*type=\"(?:radio|checkbox)\"[^>]*>")
                    for (m in re.findAll(block)) {
                        val tag = m.value
                        val value = Regex("value=\"([^\"]*)\"").find(tag)?.groupValues?.get(1) ?: continue
                        val mc = Regex("data-mc=\"([^\"]*)\"").find(tag)?.groupValues?.get(1) ?: ""
                        val fz = Regex("data-fz=\"([\\-\\.\\d]+)\"").find(tag)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                        val idx = Regex("data-index=\"(\\d+)\"").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: (options.size + 1)
                        options.add(Opt(value, unescape(mc), fz, idx))
                    }
                }
                "3" -> {
                    full = Regex("<textarea[^>]*data-fz=\"([\\-\\.\\d]+)\"").find(block)
                        ?.groupValues?.get(1)?.toDoubleOrNull()
                        ?: Regex("class=\"[^\"]*pjzb[^\"]*\"[^>]*data-fz=\"([\\-\\.\\d]+)\"").find(block)
                            ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                }
                else -> {
                    full = Regex("data-fz=\"([\\-\\.\\d]+)\"").find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                }
            }
            questions.add(Question(no, zbdm, text, txdm, sfbt, full, options))
        }

        return Questionnaire(questions, submitPath, params, hasR, minTotal, maxTotal, noteMin, noteMax)
    }

    /** 按问卷规则生成答案（含南医的“至少两条不能满星”“不能全选同一项”） */
    fun buildAnswer(q: Questionnaire, note: String, fillText: Boolean): Answer {
        val stars = HashMap<Int, Int>()      // 题号 -> 星数
        val chosen = HashMap<Int, Opt>()     // 题号 -> 选中项
        val dt = JSONArray()
        val lines = ArrayList<String>()

        // 1. 初值：星级给满，单选给最后一项（“无职务”之类）
        for (qq in q.questions) {
            when (qq.txdm) {
                "5" -> stars[qq.no] = qq.options.size.coerceAtLeast(1)
                "1", "2" -> {
                    val opt = qq.options.lastOrNull { it.mc.contains("无") } ?: qq.options.lastOrNull()
                    if (opt != null) chosen[qq.no] = opt
                }
            }
        }

        fun total(): Double {
            var s = 0.0
            for (qq in q.questions) {
                when (qq.txdm) {
                    "5" -> s += if (qq.options.isEmpty()) 0.0 else
                        qq.fullScore / qq.options.size * (stars[qq.no] ?: 0)
                    "1", "2" -> s += chosen[qq.no]?.fz ?: 0.0
                }
            }
            return Math.round(s * 100) / 100.0
        }

        // 2. 南医硬性规则：星星题至少 2 条不是满星
        val starQs = q.questions.filter { it.txdm == "5" && it.options.size > 1 }
        val byWeight = starQs.sortedBy { if (it.options.isEmpty()) 0.0 else it.fullScore / it.options.size }
        var downgraded = 0
        for (qq in byWeight) {
            if (downgraded >= 2) break
            val cur = stars[qq.no] ?: continue
            if (cur > 1) {
                stars[qq.no] = cur - 1
                downgraded++
            }
        }

        // 3. 总分不能超过上限（超过则继续降分，从权重大的开始）
        var guard = 0
        while (total() > q.maxTotal && guard++ < 200) {
            val target = starQs.maxByOrNull { if (it.options.isEmpty()) 0.0 else it.fullScore / it.options.size }
                ?: break
            val cur = stars[target.no] ?: continue
            if (cur <= 1) {
                // 星题已经降不动了，避免死循环
                if (starQs.all { (stars[it.no] ?: 1) <= 1 }) break
                continue
            }
            stars[target.no] = cur - 1
        }

        // 4. 不能全部题目选同一项
        if (q.hasR) {
            val idxs = q.questions.filter { it.txdm == "5" }.map { stars[it.no] ?: 0 }
            if (idxs.size > 1 && idxs.distinct().size == 1) {
                val first = q.questions.firstOrNull { it.txdm == "5" }
                if (first != null) stars[first.no] = ((stars[first.no] ?: 2) - 1).coerceAtLeast(1)
            }
        }

        // 5. 组装 dt
        for (qq in q.questions) {
            val e = JSONObject().put("txdm", qq.txdm).put("zbdm", qq.zbdm).put("zbmc", qq.text)
            when (qq.txdm) {
                "5" -> {
                    val n = (stars[qq.no] ?: 0).coerceIn(0, qq.options.size)
                    if (n <= 0) {
                        if (qq.required) lines.add("第${qq.no}题未答（无选项）")
                        continue
                    }
                    val opt = qq.options[n - 1]
                    val fz = (qq.fullScore / qq.options.size * n).let { Math.round(it * 100) / 100.0 }
                    e.put("zbxmdm", opt.zbxmdm).put("fz", fz).put("dtjg", "★".repeat(n))
                    lines.add("${qq.no}. ${qq.text.take(22)} → ${"★".repeat(n)}（${opt.mc}）")
                }
                "1", "2" -> {
                    val opt = chosen[qq.no]
                    if (opt == null) {
                        if (qq.required) lines.add("第${qq.no}题未答（无选项）")
                        continue
                    }
                    e.put("zbxmdm", opt.zbxmdm).put("fz", opt.fz).put("dtjg", opt.mc)
                    lines.add("${qq.no}. ${qq.text.take(22)} → ${opt.mc}")
                }
                "3" -> {
                    // 课程评价：填空题必须答（学校校验必答）；随堂评价：不答
                    if (!fillText) {
                        lines.add("${qq.no}. ${qq.text.take(24)} —— 跳过（随堂评价不答填空题）")
                        continue
                    }
                    val txt = note.ifBlank { "无" }
                    e.put("fz", qq.fullScore).put("dtjg", txt)
                    lines.add("${qq.no}. ${qq.text.take(24)} → 填空「$txt」")
                }
                "6", "7", "8" -> {
                    e.put("fz", qq.fullScore).put("dtjg", qq.fullScore.toInt().toString())
                    lines.add("${qq.no}. ${qq.text.take(22)} → ${qq.fullScore.toInt()}分")
                }
                else -> {
                    if (qq.required && qq.options.isEmpty()) lines.add("第${qq.no}题未答（题型 ${qq.txdm}）")
                    continue
                }
            }
            dt.put(e)
        }

        val t = total()
        // 评分说明：只有分数落到规定区间外才必须填，否则留空（不代写文字）
        val pfsm = if (t < q.noteMin || t > q.noteMax) {
            note.ifBlank { "教学认真负责，讲解清晰，课堂互动充分，收获很大。" }
        } else ""

        return Answer(dt, t, pfsm, lines)
    }
}
