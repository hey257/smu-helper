package cn.edu.smu.jwhelper.data

import cn.edu.smu.jwhelper.net.Api
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Repo {

    data class Semester(val code: String, val name: String)

    // ---------- 学期 ----------

    fun bootstrap(): Pair<String, List<Semester>> {
        val main = Api.getHtml("/new/student/xsgrkb/main.page")
        val cur = Regex("week\\.page\\?xnxqdm=(\\d+)").find(main)?.groupValues?.get(1)
            ?: Regex("xnxqdm=(\\d{6})").find(main)?.groupValues?.get(1)
            ?: ""

        val list = ArrayList<Semester>()
        if (cur.isNotBlank()) {
            try {
                val html = Api.getHtml("/new/student/xsgrkb/week.page?xnxqdm=$cur")
                val seg = html.substring(html.indexOf("id='xnxqdm'").coerceAtLeast(0))
                Regex("<option value='([^']*)'\\s*>([^<]*)</option>").findAll(seg).forEach {
                    list.add(Semester(it.groupValues[1], Parse.unescape(it.groupValues[2])))
                }
            } catch (e: Exception) {
                // 忽略
            }
        }
        if (list.isEmpty() && cur.isNotBlank()) list.add(Semester(cur, cur))
        return cur to list
    }

    // ---------- 课表 ----------

    /**
     * @param myJxb 我自己的教学班代码集合（来自「上课任务」）。
     *   课表接口会把同一门课其他分组班（如 2024临床医学(八年制)[58-115]）的排课一起返回，
     *   不过滤就会表现为「同一门课同时有好几个教室」。
     */
    fun timetable(xnxqdm: String, myJxb: Set<String> = emptySet(), showAll: Boolean = false): List<Course> {
        val (d1, d2) = rangeOf(xnxqdm)
        val obj = Api.postJson(
            "/new/student/xsgrkb/getCalendarWeekDatas",
            mapOf(
                "d1" to "$d1 00:00:00",
                "d2" to "$d2 00:00:00",
                "zc" to "",
                "xnxqdm" to xnxqdm,
                "szxqdm" to xnxqdm,
                "yxdm" to ""
            ),
            referer = "${Api.ROOT}/new/student/xsgrkb/week.page?xnxqdm=$xnxqdm"
        )
        val arr = obj.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<Course>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = Parse.json(o, "kcmc")
            if (name.isBlank()) continue
            val jxb = Parse.json(o, "jxbdm")
            val isMine = myJxb.isEmpty() || jxb.isBlank() || myJxb.contains(jxb)
            if (!showAll && !isMine) continue
            val place = Parse.json(o, "jxcdmc")
            val placeByWeek = parsePlaceWeeks(Parse.json(o, "jxcdmc2"))
            val noRoom = Parse.json(o, "bapjxcd") == "1"
            val stage = Parse.json(o, "jxhjmc")
            // 重要：学校的 qsxq 用 Java Calendar 编码，1=周日、2=周一 … 7=周六
            val raw = o.optString("qsxq").toIntOrNull() ?: 0
            if (raw !in 1..7) continue
            val wd = if (raw == 1) 7 else raw - 1
            val sp = o.optString("ps").toIntOrNull() ?: 0
            val ep = o.optString("pe").toIntOrNull() ?: sp
            out.add(
                Course(
                    name = name,
                    teacher = Parse.json(o, "teaxms"),
                    place = place,
                    placeByWeek = placeByWeek,
                    mine = isMine,
                    weekDay = wd,
                    startTime = Parse.json(o, "qssj"),
                    endTime = Parse.json(o, "jssj"),
                    weeks = Parse.weeks(Parse.json(o, "zc")),
                    startP = if (sp in 1..20) sp else 0,
                    endP = if (ep in 1..20 && ep >= sp) ep else sp,
                    kcbh = Parse.json(o, "kcbh"),
                    stage = stage,
                    classname = Parse.json(o, "jxbmc"),
                    noRoom = noRoom
                )
            )
        }
        return out.sortedWith(compareBy({ it.weekDay }, { it.startTime }))
    }

    private fun rangeOf(xnxqdm: String): Pair<String, String> {
        val y = xnxqdm.take(4).toIntOrNull() ?: 2026
        val term = xnxqdm.takeLast(2).toIntOrNull() ?: 1
        return if (term == 1) "$y-08-01" to "${y + 1}-02-28" else "$y-02-01" to "$y-08-31"
    }

    /** 当前教学周（按校历第 1 周推算），返回 (周次, 第1周周一日期) */
    fun currentWeek(xnxqdm: String): Pair<Int, String> {
        return try {
            val res = Api.postForm(
                "/new/xlxx/getDatesOfWeek",
                mapOf("xnxqdm" to xnxqdm, "zc" to "1"),
                referer = "${Api.ROOT}/new/student/xsgrkb/week.page?xnxqdm=$xnxqdm"
            )
            val first = Regex("\"rq\":\"(\\d{4}-\\d{2}-\\d{2})\"").find(res)?.groupValues?.get(1)
                ?: return 1 to ""
            val start = java.time.LocalDate.parse(first)
            val w = java.time.temporal.ChronoUnit.WEEKS.between(start, java.time.LocalDate.now()).toInt() + 1
            (if (w < 1) 1 else w) to first
        } catch (e: Exception) {
            1 to ""
        }
    }

    /** 某一周的 7 天日期 */
    fun weekDates(xnxqdm: String, zc: Int): List<String> {
        return try {
            val res = Api.postForm(
                "/new/xlxx/getDatesOfWeek",
                mapOf("xnxqdm" to xnxqdm, "zc" to zc.toString()),
                referer = "${Api.ROOT}/new/student/xsgrkb/week.page?xnxqdm=$xnxqdm"
            )
            Regex("\"rq\":\"(\\d{4}-\\d{2}-\\d{2})\"").findAll(res).map { it.groupValues[1] }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- 网考号（上课任务） ----------

    /** 按括号层级切分，教室名里本身可能带逗号 */
    private fun splitTop(s: String): List<String> {
        val out = ArrayList<String>()
        val buf = StringBuilder()
        var depth = 0
        for (ch in s) {
            when (ch) {
                '(', '（' -> { depth++; buf.append(ch) }
                ')', '）' -> { depth--; buf.append(ch) }
                ',' -> if (depth <= 0) { out.add(buf.toString()); buf.setLength(0) } else buf.append(ch)
                else -> buf.append(ch)
            }
        }
        if (buf.isNotEmpty()) out.add(buf.toString())
        return out
    }

    /**
     * jxcdmc2 形如 "501智慧教室-10,601教室-12"，是「教室-周次」的逐条列表。
     * 用它就能知道某一周到底在哪个教室，而不是把整串教室都显示出来。
     */
    private fun parsePlaceWeeks(raw: String): Map<Int, String> {
        if (raw.isBlank()) return emptyMap()
        val out = HashMap<Int, String>()
        val re = Regex("^(.*)-(\\d+)$")
        splitTop(raw).forEach { seg ->
            val m = re.find(seg.trim()) ?: return@forEach
            val p = m.groupValues[1].trim()
            val w = m.groupValues[2].toIntOrNull() ?: return@forEach
            if (p.isNotEmpty()) out[w] = p
        }
        return out
    }

    /** 网考号 + 我的教学班代码，一次请求拿全 */
    fun workTasks(xnxqdm: String): Pair<Map<String, String>, Set<String>> {
        val empty = emptyMap<String, String>() to emptySet<String>()
        return try {
            val obj = Api.postJson(
                "/new/student/xskcrw/skrwDatas",
                mapOf("xnxqdm" to xnxqdm),
                referer = "${Api.ROOT}/new/student/xskcrw/list.page"
            )
            val rows = obj.optJSONArray("rows") ?: return empty
            val map = HashMap<String, String>()
            val jxb = HashSet<String>()
            for (i in 0 until rows.length()) {
                val o = rows.optJSONObject(i) ?: continue
                val kc = Parse.json(o, "kcmc")
                val xid = Parse.json(o, "xid")
                val code = Parse.json(o, "jxbdm")
                if (kc.isNotBlank() && xid.isNotBlank()) map[kc] = xid
                if (code.isNotBlank()) jxb.add(code)
            }
            map to jxb
        } catch (e: Exception) {
            empty
        }
    }

    fun wkhMap(xnxqdm: String): Map<String, String> = workTasks(xnxqdm).first

    // ---------- 考试 ----------

    /** 查一次考试，返回行数 */
    private fun examQuery(xnxqdm: String, ksaplxdm: String): List<ExamItem> {
        val obj = Api.postJson(
            "/new/student/xsksrw/paginateXsksrw",
            mapOf(
                "page" to "1", "rows" to "300",
                "xnxqdm" to xnxqdm, "ksaplxdm" to ksaplxdm, "kslbdm" to ""
            ),
            referer = "${Api.ROOT}/new/student/xsksrw/list.page"
        )
        val rows = obj.optJSONArray("rows") ?: return emptyList()
        val out = ArrayList<ExamItem>()
        for (i in 0 until rows.length()) {
            val o = rows.optJSONObject(i) ?: continue
            val kc = Parse.json(o, "kcmc")
            if (kc.isBlank()) continue
            out.add(
                ExamItem(
                    course = kc,
                    date = Parse.json(o, "ksrq"),
                    time = Parse.json(o, "kssj"),
                    place = Parse.json(o, "kscdmc").ifBlank { Parse.json(o, "xqmc") },
                    seat = Parse.json(o, "zwh"),
                    type = Parse.json(o, "ksaplxmc").ifBlank { Parse.json(o, "kslbmc") },
                    note = Parse.json(o, "bz"),
                    wkh = ""
                )
            )
        }
        return out
    }

    /**
     * 多路兜底：全部学期 → 当前学期 → 逐个考试类型。
     * 学校的考试安排常常按“安排类型”分批发布，只查当前学期可能会空。
     */
    fun exams(xnxqdm: String): List<ExamItem> {
        val wkh = wkhMap(xnxqdm)
        val all = LinkedHashMap<String, ExamItem>()
        fun put(list: List<ExamItem>) {
            list.forEach { e ->
                val key = e.course + "|" + e.date + "|" + e.time + "|" + e.place
                all.putIfAbsent(key, e)
            }
        }
        // 1. 全部学期
        try {
            put(examQuery("", ""))
        } catch (e: Exception) {
            // 忽略，继续
        }
        // 2. 当前学期
        if (all.isEmpty()) {
            try {
                put(examQuery(xnxqdm, ""))
            } catch (e: Exception) {
                // 忽略
            }
        }
        // 3. 逐个类型
        if (all.isEmpty()) {
            for (lx in listOf("03", "01", "02", "05")) {
                try {
                    put(examQuery("", lx))
                    if (all.isNotEmpty()) break
                } catch (e: Exception) {
                    // 忽略
                }
            }
        }
        // 4. 从课表里取“考试”环节（学校常常不在考试安排里发布，但课表有）
        try {
            val (_, w1) = currentWeek(xnxqdm)
            if (w1.isNotBlank()) {
                val start = java.time.LocalDate.parse(w1)
                timetable(xnxqdm).filter { it.stage.contains("考试") }.forEach { c ->
                    c.weeks.forEach { w ->
                        val d = start.plusDays(((w - 1) * 7 + (c.weekDay - 1)).toLong()).toString()
                        val key = c.name + "|" + d + "|" + c.startHm
                        all.putIfAbsent(
                            key,
                            ExamItem(
                                course = c.name,
                                date = d,
                                time = "${c.startHm}-${c.endHm}",
                                place = c.place,
                                seat = "",
                                type = c.stage.ifBlank { "考试" },
                                note = "来自课表",
                                wkh = ""
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略
        }
        return all.values
            .map { it.copy(wkh = wkh[it.course] ?: "") }
            .sortedBy { it.date + it.time }
    }

    /** 诊断用：每种查询各有多少条 */
    fun examProbe(xnxqdm: String): String {
        val sb = StringBuilder()
        fun one(label: String, xq: String, lx: String) {
            val n = try {
                examQuery(xq, lx).size
            } catch (e: Exception) {
                -1
            }
            sb.append(label).append("=").append(if (n < 0) "失败" else n.toString()).append("  ")
        }
        one("全部学期", "", "")
        one("当前学期", xnxqdm, "")
        one("统考", "", "03")
        one("随堂考", "", "01")
        one("学院考", "", "02")
        return sb.toString()
    }

    // ---------- 评课 ----------

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())

    /** 随堂评价列表（必须给日期） */
    fun classEvalList(xnxqdm: String, date: String): List<PjItem> {
        val obj = Api.postJson(
            "/new/student/ktpj/xsktpjData",
            mapOf("page" to "1", "rows" to "200", "jsrq" to date),
            referer = "${Api.ROOT}/new/student/ktpj"
        )
        val rows = obj.optJSONArray("rows") ?: return emptyList()
        val out = ArrayList<PjItem>()
        for (i in 0 until rows.length()) {
            val o = rows.optJSONObject(i) ?: continue
            val kc = Parse.json(o, "kcmc")
            if (kc.isBlank()) continue
            val done = Parse.json(o, "pjdm").isNotBlank()
            val sub = listOf(
                Parse.json(o, "teaxm"),
                Parse.json(o, "jxhjmc"),
                "第" + Parse.json(o, "jcdm2") + "节",
                Parse.json(o, "jxcdmc"),
                Parse.json(o, "jsrq")
            ).filter { it.isNotBlank() && it != "第节" }.joinToString(" · ")
            out.add(
                PjItem(
                    kind = 1,
                    title = kc,
                    subtitle = sub,
                    tag = if (done) "已评" else "",
                    params = mapOf(
                        "pjlxdm" to "6",
                        "teadm" to Parse.json(o, "teadm"),
                        "dgksdm" to Parse.json(o, "dgksdm"),
                        "wjdm" to Parse.json(o, "ktpj")
                    ),
                    pagePath = "/new/student/ktpj/showXsktpjwj.page",
                    raw = o
                )
            )
        }
        return out.sortedBy { it.subtitle }
    }

    /** 课程评价列表 */
    fun courseEvalList(xnxqdm: String): List<PjItem> {
        val obj = Api.postJson(
            "/new/student/kcpj/xskcpjData",
            mapOf(
                "page" to "1", "rows" to "100", "xnxqdm" to xnxqdm,
                "primarySort" to " kcbh asc"
            ),
            referer = "${Api.ROOT}/new/student/kcpj"
        )
        val rows = obj.optJSONArray("rows") ?: return emptyList()
        val out = ArrayList<PjItem>()
        for (i in 0 until rows.length()) {
            val o = rows.optJSONObject(i) ?: continue
            val kc = Parse.json(o, "kcmc")
            if (kc.isBlank()) continue
            val done = Parse.json(o, "pjdm").isNotBlank()
            val jksj = Parse.json(o, "jksj")
            out.add(
                PjItem(
                    kind = 0,
                    title = kc,
                    subtitle = buildString {
                        val kcbh = Parse.json(o, "kcbh")
                        if (kcbh.isNotBlank()) append(kcbh)
                        if (jksj.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("结课 ").append(jksj.take(10))
                        }
                    },
                    // 按用户要求：不判断是否结课，只要没评过就能手动评
                    tag = if (done) "已评" else "",
                    params = mapOf(
                        "kcrwdm" to Parse.json(o, "kcrwdm"),
                        "wjdm" to Parse.json(o, "wjdm"),
                        "xnxqdm" to Parse.json(o, "xnxqdm").ifBlank { xnxqdm }
                    ),
                    pagePath = "/new/student/kcpj/pj.page",
                    raw = o
                )
            )
        }
        return out
    }

    /** 拉取并解析问卷 */
    fun questionnaire(item: PjItem): Questionnaire {
        val q = item.params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val referer = if (item.kind == 0) "${Api.ROOT}/new/student/kcpj" else "${Api.ROOT}/new/student/ktpj"
        val html = Api.getHtml(item.pagePath + "?" + q, referer)
        return Parse.questionnaire(html)
    }

    /** 提交评价 */
    fun submit(item: PjItem, q: Questionnaire, ans: Answer) {
        val body = LinkedHashMap(q.submitParams)
        body["wtpf"] = if (ans.wtpf % 1.0 == 0.0) ans.wtpf.toInt().toString() else ans.wtpf.toString()
        body["pfsm"] = ans.pfsm
        body["dt"] = ans.dt.toString()
        val referer = if (item.kind == 0) "${Api.ROOT}/new/student/kcpj" else "${Api.ROOT}/new/student/ktpj"
        val res = Api.postForm(q.submitPath, body, referer)
        val obj = try {
            JSONObject(res.trim().removePrefix("\uFEFF"))
        } catch (e: Exception) {
            throw Exception("提交返回异常：${res.take(60)}")
        }
        val code = obj.optInt("code", -99)
        if (code < 0) throw Exception(Parse.json(obj, "message").ifBlank { "提交失败" })
    }

    /** 一键评完某天的随堂评价（自动/手动共用），返回结果摘要 */
    fun runClassEvalForDate(xnxqdm: String, date: String, note: String): String {
        val list = classEvalList(xnxqdm, date).filter { it.tag != "已评" }
        if (list.isEmpty()) return "无待评课程"
        var ok = 0
        var fail = 0
        val errs = ArrayList<String>()
        for (item in list) {
            try {
                val q = questionnaire(item)
                if (q.questions.isEmpty()) {
                    fail++
                } else {
                    val ans = Parse.buildAnswer(q, note, false)
                    submit(item, q, ans)
                    ok++
                }
            } catch (e: Exception) {
                fail++
                errs.add(item.title + "：" + (e.message ?: "失败"))
            }
        }
        return "成功 $ok，失败 $fail" + if (errs.isNotEmpty()) "（${errs.first()}）" else ""
    }

    // ---------- 诊断 ----------

    fun diagnose(): List<String> {
        val out = ArrayList<String>()
        fun probe(label: String, block: () -> String) {
            out.add(
                try {
                    "$label → ${block().take(200)}"
                } catch (e: Exception) {
                    "$label → ✗ ${e.message}"
                }
            )
        }
        out.add("Cookie → " + (Api.cookie ?: "（空）"))
        probe("落地页") {
            val h = Api.getHtml("/new/welcome.page?ui=new")
            "长度=${h.length}，是登录页=${h.contains("login-form")}"
        }
        probe("学期") {
            val (cur, list) = bootstrap()
            "当前=$cur，共${list.size}个学期"
        }
        probe("课表") {
            val (cur, _) = bootstrap()
            "共 ${timetable(cur).size} 条排课"
        }
        probe("考试") {
            val (cur, _) = bootstrap()
            "共 ${exams(cur).size} 场；各查询：" + examProbe(cur)
        }
        probe("网考号") {
            val (cur, _) = bootstrap()
            val m = wkhMap(cur)
            "共 ${m.size} 门课有网考号：" + m.entries.take(3).joinToString(", ") { "${it.key}=${it.value}" }
        }
        return out
    }
}
