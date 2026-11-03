package cn.edu.smu.jwhelper.data

import cn.edu.smu.jwhelper.net.Api
import cn.edu.smu.jwhelper.net.ApiException
import org.json.JSONArray
import org.json.JSONObject

/**
 * 选课报名（/new/student/xsxk/）。
 *
 * 网页端流程：
 *  1. 选课首页按「选课类型」渲染若干块，块里带 xklxdm 和阶段时间
 *  2. /xklx/{xklxdm}/config 拿阶段（xkjd）与开关（权重、学习要求）
 *  3. /xklx/{xklxdm}/kxkc 可选课程、/yxkc 已选课程
 *  4. /xklx/{xklxdm}/add 选课（kcrwdm + kcmc + qz + xxyqdm + hlct）
 *  5. /xklx/{xklxdm}/cancel 退课（jxbdm + kcrwdm + kcmc）
 */
object XkRepo {

    /** 一个选课类型（如「公共选修课第二轮」） */
    data class XkType(val dm: String, val name: String, val hint: String, val enabled: Boolean)

    /** 选课配置 */
    data class XkConfig(
        val dm: String,
        val name: String,        // 公共选修课第二轮
        val xnxqdm: String,
        val xnxqmc: String,
        val xkjd: Int,           // 0 未开始 / 1 一选 / 2 二选 / 3 退选 / 4 补选
        val stageName: String,   // 一选 / 二选 / 退选 / 补选
        val canCancel: Boolean,  // 当前阶段能否退选
        val enableQz: Boolean,
        val myQz: Int,
        val enableXxyq: Boolean,
        val xxyq: List<Pair<String, String>>, // dm to mc
        val note: String,
        val stageStart: String,  // 当前/下一次阶段开始
        val stageEnd: String,
        val allStages: List<Triple<String, String, String>> // 名称、开始、结束
    ) {
        val open: Boolean get() = xkjd != 0
        /** 选课时允许点选的阶段（退选阶段不显示选课按钮） */
        val canPick: Boolean get() = xkjd == 1 || xkjd == 2 || xkjd == 4
    }

    /** 一门可选/已选课程 */
    data class XkCourse(
        val kcrwdm: String,
        val kcdm: String,
        val jxbdm: String,
        val kcmc: String,
        val jxbmc: String,
        val teaxm: String,
        val kcflmc: String,
        val kcdlmc: String,
        val xf: Double,
        val zxs: Double,
        val pkrs: Int,      // 限选人数
        val jxbrs: Int,     // 已选人数
        val chosen: Boolean,
        val kcptdm: String,
        val rwdm: String
    ) {
        val full: Boolean get() = pkrs > 0 && jxbrs >= pkrs
        val ratio: Float get() = if (pkrs <= 0) 0f else (jxbrs.toFloat() / pkrs).coerceIn(0f, 1f)
    }

    /** 一次提交的结果 */
    data class XkResult(val ok: Boolean, val msg: String, val askAgain: Boolean)

    /** 用户想抢的课（按课程代码匹配，同一门课的任一教学班都算） */
    data class Wish(val kcdm: String, val kcmc: String, val qz: Int = -1)

    private const val REF_HOME = "${Api.ROOT}/new/student/xsxk/"

    // ---------- 解析选课首页 ----------

    /** 抓选课首页，解析出所有选课类型 */
    fun types(): List<XkType> {
        val html = Api.getHtml("/new/student/xsxk/")
        val out = ArrayList<XkType>()
        // lay-iframe="名称" ... data-href="/xsxk.html?xklxdm=02&mode=list&bp=/"
        val re = Regex(
            "lay-iframe=\"([^\"]*)\"[^>]*?data-href=\"([^\"]*xklxdm=(\\d+)[^\"]*)\"",
            RegexOption.DOT_MATCHES_ALL
        )
        for (m in re.findAll(html)) {
            val name = m.groupValues[1].trim()
            val dm = m.groupValues[3]
            if (out.any { it.dm == dm }) continue
            out.add(XkType(dm, name.ifBlank { "选课类型 $dm" }, "", true))
        }
        // 兜底：只有 xklxdm 没有名称
        if (out.isEmpty()) {
            for (m in Regex("xklxdm=(\\d+)&").findAll(html)) {
                val dm = m.groupValues[1]
                if (out.none { it.dm == dm }) out.add(XkType(dm, "选课类型 $dm", "", true))
            }
        }
        return out
    }

    // ---------- 配置 ----------

    fun config(dm: String): XkConfig? {
        val obj = Api.postJson(
            "/new/student/xsxk/xklx/$dm/config",
            emptyMap(),
            referer = REF_HOME
        )
        val code = obj.optInt("code", -1)
        if (code < 0) return null

        val data = obj.optJSONObject("data") ?: return null
        val kz = data.optJSONObject("xkkz") ?: JSONObject()
        val names = ArrayList<String>()
        data.optJSONArray("xkjdmc")?.let { a ->
            for (i in 0 until a.length()) names.add(a.optString(i))
        }
        val xkjd = kz.optString("xkjd").toIntOrNull() ?: kz.optInt("xkjd", 0)

        val stages = ArrayList<Triple<String, String, String>>()
        val label = listOf("一选", "二选", "退选", "补选")
        for (i in 1..4) {
            val s = kz.optString("qssj$i")
            val e = kz.optString("jssj$i")
            if (s.isNotBlank() || e.isNotBlank()) {
                stages.add(Triple(names.getOrNull(i - 1) ?: label[i - 1], s, e))
            }
        }

        val curIdx = if (xkjd in 1..4) xkjd else 0
        val start = if (curIdx > 0) kz.optString("qssj$curIdx") else ""
        val end = if (curIdx > 0) kz.optString("jssj$curIdx") else ""

        val xxyq = ArrayList<Pair<String, String>>()
        data.optJSONArray("xxyqlist")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                xxyq.add(Parse.json(o, "dm") to Parse.json(o, "mc"))
            }
        }

        val canCancel = when (xkjd) {
            1 -> kz.optString("iscancel1") == "1"
            2 -> kz.optString("iscancel2") == "1"
            3 -> kz.optString("iscancel3") == "1"
            4 -> kz.optString("iscancel4") == "1"
            else -> false
        }

        return XkConfig(
            dm = dm,
            name = kz.optString("xklxmc").ifBlank { "选课" },
            xnxqdm = kz.optString("xnxqdm"),
            xnxqmc = kz.optString("xnxqmc"),
            xkjd = xkjd,
            stageName = names.getOrNull(xkjd - 1) ?: if (xkjd == 0) "未开放" else "第 ${xkjd} 阶段",
            canCancel = canCancel,
            enableQz = data.optBoolean("enableXkqz", false),
            myQz = data.optString("dqqz").toIntOrNull() ?: 0,
            enableXxyq = data.optBoolean("enableXxyq", false),
            xxyq = xxyq,
            note = kz.optString("bz"),
            stageStart = start,
            stageEnd = end,
            allStages = stages
        )
    }

    // ---------- 课程列表 ----------

    private fun courseOf(o: JSONObject): XkCourse = XkCourse(
        kcrwdm = Parse.json(o, "kcrwdm"),
        kcdm = Parse.json(o, "kcdm"),
        jxbdm = Parse.json(o, "jxbdm"),
        kcmc = Parse.json(o, "kcmc"),
        jxbmc = Parse.json(o, "jxbmc"),
        teaxm = Parse.json(o, "teaxm"),
        kcflmc = Parse.json(o, "kcflmc"),
        kcdlmc = Parse.json(o, "kcdlmc"),
        xf = o.optString("xf").toDoubleOrNull() ?: 0.0,
        zxs = o.optString("zxs").toDoubleOrNull() ?: 0.0,
        pkrs = o.optString("pkrs").toIntOrNull() ?: 0,
        jxbrs = o.optString("jxbrs").toIntOrNull() ?: 0,
        chosen = o.optString("isyxkc") == "1",
        kcptdm = Parse.json(o, "kcptdm"),
        rwdm = Parse.json(o, "rwdm")
    )

    /** 可选课程。返回 total 与当前页 */
    fun courses(dm: String, page: Int = 1, rows: Int = 200, key: String = "", value: String = ""): Pair<Int, List<XkCourse>> {
        val params = linkedMapOf(
            "page" to page.toString(),
            "rows" to rows.toString(),
            "sortName" to "kcrwdm",
            "order" to "asc"
        )
        if (key.isNotBlank() && value.isNotBlank()) {
            params["searchKey"] = key
            params["searchValue"] = value
        }
        val obj = Api.getJson("/new/student/xsxk/xklx/$dm/kxkc", params, referer = REF_HOME)
        val list = ArrayList<XkCourse>()
        obj.optJSONArray("rows")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { list.add(courseOf(it)) }
        }
        return (obj.optString("total").toIntOrNull() ?: list.size) to list
    }

    /** 已选课程 */
    fun chosen(dm: String): List<XkCourse> {
        val obj = Api.getJson(
            "/new/student/xsxk/xklx/$dm/yxkc",
            linkedMapOf("page" to "1", "rows" to "200"),
            referer = REF_HOME
        )
        val list = ArrayList<XkCourse>()
        obj.optJSONArray("rows")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { list.add(courseOf(it)) }
        }
        return list
    }

    // ---------- 提交 ----------

    fun add(dm: String, c: XkCourse, qz: Int, xxyqdm: String): XkResult {
        val obj = try {
            Api.postJson(
                "/new/student/xsxk/xklx/$dm/add",
                linkedMapOf(
                    "kcrwdm" to c.kcrwdm,
                    "kcmc" to c.kcmc,
                    "qz" to qz.toString(),
                    "xxyqdm" to xxyqdm,
                    "hlct" to "0"
                ),
                referer = REF_HOME
            )
        } catch (e: ApiException) {
            return XkResult(false, e.message ?: "提交失败", false)
        }
        val code = obj.optInt("code", -1)
        val msg = obj.optString("message").ifBlank { if (code >= 0) "选课成功" else "提交失败" }
        val data = obj.optString("data")
        return XkResult(code >= 0, msg, code < 0 && data.isNotBlank())
    }

    /** 需要二次确认时（hlct=1）再调一次 */
    fun addConfirm(dm: String, c: XkCourse, qz: Int, xxyqdm: String): XkResult {
        val obj = try {
            Api.postJson(
                "/new/student/xsxk/xklx/$dm/add",
                linkedMapOf(
                    "kcrwdm" to c.kcrwdm,
                    "kcmc" to c.kcmc,
                    "qz" to qz.toString(),
                    "xxyqdm" to xxyqdm,
                    "hlct" to "1"
                ),
                referer = REF_HOME
            )
        } catch (e: ApiException) {
            return XkResult(false, e.message ?: "提交失败", false)
        }
        val code = obj.optInt("code", -1)
        return XkResult(code >= 0, obj.optString("message").ifBlank { if (code >= 0) "选课成功" else "提交失败" }, false)
    }

    fun cancel(dm: String, c: XkCourse): XkResult {
        val obj = try {
            Api.postJson(
                "/new/student/xsxk/xklx/$dm/cancel",
                linkedMapOf("jxbdm" to c.jxbdm, "kcrwdm" to c.kcrwdm, "kcmc" to c.kcmc),
                referer = REF_HOME
            )
        } catch (e: ApiException) {
            return XkResult(false, e.message ?: "退课失败", false)
        }
        val code = obj.optInt("code", -1)
        return XkResult(code >= 0, obj.optString("message").ifBlank { if (code >= 0) "退课成功" else "退课失败" }, false)
    }

    // ---------- 自动抢课 ----------

    /**
     * 跑一轮自动选课。
     * 返回逐条日志；[onProgress] 用来实时回显。
     */
    fun grabRound(wishes: List<Wish>, onLog: (String) -> Unit): List<String> {
        val logs = ArrayList<String>()
        fun log(s: String) {
            logs.add(s)
            onLog(s)
        }
        if (wishes.isEmpty()) {
            log("清单为空")
            return logs
        }
        val types = try {
            types()
        } catch (e: Exception) {
            log("读取选课类型失败：" + (e.message ?: ""))
            return logs
        }
        if (types.isEmpty()) {
            log("没有选课批次")
            return logs
        }

        var any = false
        for (t in types) {
            val cfg = try {
                config(t.dm)
            } catch (e: Exception) {
                log("${t.name}：读取配置失败")
                continue
            } ?: continue

            if (!cfg.open) {
                log("${cfg.name}：尚未开放（${nextStageText(cfg)}）")
                continue
            }
            if (!cfg.canPick) {
                log("${cfg.name}：当前是${cfg.stageName}，不能选课")
                continue
            }
            any = true

            val listed: List<XkCourse>
            val mine: List<XkCourse>
            try {
                listed = courses(t.dm).second
                mine = chosen(t.dm)
            } catch (e: Exception) {
                log("${cfg.name}：拉课程失败")
                continue
            }
            val mineKcdm = mine.map { it.kcdm }.toSet()

            for (w in wishes) {
                if (mineKcdm.contains(w.kcdm)) {
                    log("${w.kcmc}：已经在已选里")
                    continue
                }
                val c = listed
                    .filter { it.kcdm == w.kcdm && !it.chosen }
                    .filter { cfg.xkjd == 1 || !it.full }
                    .minByOrNull { if (it.pkrs <= 0) 0f else it.jxbrs.toFloat() / it.pkrs }
                if (c == null) {
                    log("${w.kcmc}：没有可选的班（满员或不在本次批次）")
                    continue
                }
                val qz = if (cfg.enableQz) w.qz.coerceAtLeast(0) else -1
                val xxyq = if (cfg.enableXxyq) cfg.xxyq.firstOrNull()?.first ?: "" else ""
                var r = add(t.dm, c, qz, xxyq)
                if (!r.ok && r.askAgain) {
                    log("${w.kcmc}：${r.msg}，强制确认")
                    r = addConfirm(t.dm, c, qz, xxyq)
                }
                log(if (r.ok) "${w.kcmc}：选课成功（${c.teaxm}）" else "${w.kcmc}：${r.msg}")
            }
        }
        if (!any) log("还没有任何一个批次开放")
        return logs
    }

    /** 下一次开放时间的人话描述 */
    fun nextStageText(cfg: XkConfig): String {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
            .format(java.util.Date())
        val next = cfg.allStages.firstOrNull { it.second.isNotBlank() && it.second > now }
        if (next != null) return "${next.first} ${next.second.take(16)} 开始"
        val anyTime = cfg.allStages.any { it.second.isNotBlank() }
        val lastEnd = cfg.allStages.map { it.third }.filter { it.isNotBlank() }.maxOrNull()
        return if (anyTime) ("本批次已结束" + if (lastEnd != null) "（${lastEnd.take(16)} 截止）" else "")
        else "时间未公布"
    }
}
