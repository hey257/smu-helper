package cn.edu.smu.jwhelper.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.smu.jwhelper.core.Prefs
import cn.edu.smu.jwhelper.data.Answer
import cn.edu.smu.jwhelper.data.Cache
import cn.edu.smu.jwhelper.data.Course
import cn.edu.smu.jwhelper.data.ExamItem
import cn.edu.smu.jwhelper.data.PjItem
import cn.edu.smu.jwhelper.data.Questionnaire
import cn.edu.smu.jwhelper.data.Repo
import cn.edu.smu.jwhelper.net.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Pending(
    val item: PjItem,
    val q: Questionnaire,
    val ans: Answer
)

class AppVm(app: Application) : AndroidViewModel(app) {

    var loggedIn by mutableStateOf(false)
        private set

    /** 是否已经确认过登录态（没确认前显示启动页，不要求点击） */
    var sessionChecked by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var busyText by mutableStateOf("")
        private set
    var message by mutableStateOf<String?>(null)

    var semesters by mutableStateOf<List<Repo.Semester>>(emptyList())
    var xnxqdm by mutableStateOf("")
    var xnxqmc by mutableStateOf("")

    var courses by mutableStateOf<List<Course>>(emptyList())
    var exams by mutableStateOf<List<ExamItem>>(emptyList())
    var timetableLoaded by mutableStateOf(false)
    var curWeek by mutableStateOf(1)
    var week1 by mutableStateOf("")
    var weekDates by mutableStateOf<List<String>>(emptyList())
    var wkhMap by mutableStateOf<Map<String, String>>(emptyMap())

    var evalCourses by mutableStateOf<List<PjItem>>(emptyList())
    var evalClasses by mutableStateOf<List<PjItem>>(emptyList())
    var evalDate by mutableStateOf(today())
    var evalLoaded by mutableStateOf(false)
    var classQueried by mutableStateOf(false)

    var pending by mutableStateOf<Pending?>(null)
    var results by mutableStateOf<List<String>>(emptyList())
    var diagText by mutableStateOf("")

    var noteText by mutableStateOf("")
    var showAllClasses by mutableStateOf(true)

    var autoOn by mutableStateOf(false)
    var autoHour by mutableIntStateOf(21)
    var autoMinute by mutableIntStateOf(30)
    var autoLog by mutableStateOf("")

    init {
        Prefs.init(app)
        restoreCache()
        noteText = Prefs.evalText.ifBlank { "无" }
        showAllClasses = Prefs.showAllClasses
        autoOn = Prefs.autoClassEval
        autoHour = Prefs.autoHour
        autoMinute = Prefs.autoMinute
        autoLog = Prefs.autoLog
        Api.cookie = Prefs.cookie
        if (!Api.cookie.isNullOrBlank()) {
            probeSession(silent = true)
        } else {
            sessionChecked = true
        }
        cn.edu.smu.jwhelper.work.Scheduler.schedule(app)
        if (!Api.cookie.isNullOrBlank()) {
            cn.edu.smu.jwhelper.work.KeepAlive.start(app)
        }
    }

    fun setAuto(on: Boolean) {
        autoOn = on
        Prefs.autoClassEval = on
        cn.edu.smu.jwhelper.work.Scheduler.schedule(getApplication())
    }

    fun setAutoTime(h: Int, m: Int) {
        autoHour = h.coerceIn(0, 23)
        autoMinute = m.coerceIn(0, 59)
        Prefs.autoHour = autoHour
        Prefs.autoMinute = autoMinute
        cn.edu.smu.jwhelper.work.Scheduler.schedule(getApplication())
    }

    /** 立刻按“自动”的逻辑跑一次（用来验证自动化是否正常） */
    fun runAutoNow() {
        viewModelScope.launch {
            busy = true
            busyText = "正在按自动流程评价今天的随堂…"
            ensureSemester()
            val r = io { Repo.runClassEvalForDate(xnxqdm, today(), noteText) }
            busy = false
            if (r != null) {
                autoLog = "${today()} $r"
                Prefs.autoLog = autoLog
                Prefs.lastEvalDate = today()
                toast("执行完成：$r")
            }
        }
    }

    fun toast(msg: String) {
        message = msg
    }

    // ---------- 本地缓存 ----------

    private fun restoreCache() {
        // 解析规则改过就丢掉旧缓存，否则会先看到旧逻辑算出来的错误教室
        if (Prefs.cacheVer != CACHE_VER) return
        if (Prefs.cacheKey.isBlank()) return
        if (Prefs.cacheXnxqdm.isBlank()) return
        xnxqdm = Prefs.cacheXnxqdm
        xnxqmc = Prefs.cacheXnxqmc
        semesters = Cache.semestersFrom(Prefs.cacheSemesters)
        courses = Cache.coursesFrom(Prefs.cacheCourses)
        exams = Cache.examsFrom(Prefs.cacheExams)
        wkhMap = Cache.wkhFrom(Prefs.cacheWkh)
        week1 = Prefs.cacheWeek1
        curWeek = Prefs.cacheCurWeek.coerceAtLeast(1)
        timetableLoaded = courses.isNotEmpty()
    }

    private fun saveCache() {
        if (xnxqdm.isBlank()) return
        Prefs.cacheVer = CACHE_VER
        Prefs.cacheKey = xnxqdm
        Prefs.cacheXnxqdm = xnxqdm
        Prefs.cacheXnxqmc = xnxqmc
        Prefs.cacheWeek1 = week1
        Prefs.cacheCurWeek = curWeek
        if (semesters.isNotEmpty()) Prefs.cacheSemesters = Cache.semestersToJson(semesters)
        // 空数据不覆盖旧缓存，避免网络抖动把界面清空
        if (courses.isNotEmpty()) Prefs.cacheCourses = Cache.coursesToJson(courses)
        if (exams.isNotEmpty()) Prefs.cacheExams = Cache.examsToJson(exams)
        if (wkhMap.isNotEmpty()) Prefs.cacheWkh = Cache.wkhToJson(wkhMap)
        // 桌面小组件读的就是这份缓存，写完立刻让它重画
        runCatching { CourseWidgetProvider.refreshAll(getApplication()) }
    }

    private suspend fun <T> io(block: () -> T): T? = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { toast(e.message ?: "操作失败") }
            null
        }
    }

    /** 检查网页登录态 */
    fun probeSession(silent: Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Api.isLoggedIn() }
            sessionChecked = true
            if (ok) {
                loggedIn = true
                Prefs.cookie = Api.cookie
                Prefs.sessionAlive = true
                cn.edu.smu.jwhelper.work.KeepAlive.start(getApplication())
                startPing()
                loadAll()
            } else {
                loggedIn = false
                if (!silent) toast("登录状态无效，请重新登录")
            }
        }
    }

    private var lastProbeAt = 0L
    fun onWebPageLoaded(cookie: String?) {
        if (!cookie.isNullOrBlank()) {
            Api.cookie = cookie
            Prefs.cookie = cookie
        }
        val now = System.currentTimeMillis()
        if (now - lastProbeAt < 2500) return
        lastProbeAt = now
        if (loggedIn) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Api.isLoggedIn() }
            sessionChecked = true
            if (ok) {
                loggedIn = true
                Prefs.sessionAlive = true
                cn.edu.smu.jwhelper.work.KeepAlive.start(getApplication())
                startPing()
                loadAll()
            }
        }
    }

    fun loadAll() {
        // 有缓存就直接用缓存渲染，网络请求在后台悄悄刷新，不弹"正在读取…"
        val hasCache = courses.isNotEmpty()
        viewModelScope.launch {
            if (!hasCache) {
                busy = true
                busyText = "正在读取学期信息…"
            }
            val boot = io { Repo.bootstrap() }
            if (boot != null) {
                val (cur, list) = boot
                semesters = list
                if (xnxqdm.isBlank()) xnxqdm = cur
                xnxqmc = list.firstOrNull { it.code == xnxqdm }?.name ?: xnxqdm
            }
            loadTimetableInternal()
            loadExamsInternal()
            saveCache()
            busy = false
        }
    }

    /** 切换「显示其他教学班」：重拉课表并刷新缓存 */
    fun applyShowAll(on: Boolean) {
        showAllClasses = on
        Prefs.showAllClasses = on
        viewModelScope.launch {
            busy = true
            busyText = "正在重新读取课表…"
            loadTimetableInternal()
            saveCache()
            busy = false
        }
    }

    fun loadTimetable() {
        viewModelScope.launch {
            busy = true
            busyText = "正在读取课表…"
            loadTimetableInternal()
            busy = false
        }
    }

    private suspend fun loadTimetableInternal() {
        if (xnxqdm.isNotBlank()) {
            val w = io { Repo.currentWeek(xnxqdm) }
            if (w != null) {
                curWeek = w.first
                week1 = w.second
            }
        }
        // 「上课任务」既能拿网考号，也能拿到我自己的教学班代码；课表要靠它过滤掉别人的分组班
        val tasks = io { Repo.workTasks(xnxqdm) }
        var myJxb = emptySet<String>()
        if (tasks != null) {
            wkhMap = tasks.first
            myJxb = tasks.second
        }
        val list = io { Repo.timetable(xnxqdm, myJxb, showAllClasses) }
        if (list != null) {
            courses = list
            timetableLoaded = true
        }
        val d = io { Repo.weekDates(xnxqdm, curWeek) }
        if (d != null) weekDates = d
    }

    fun loadWeekDates(week: Int) {
        viewModelScope.launch {
            val d = io { Repo.weekDates(xnxqdm, week) }
            if (d != null) weekDates = d
        }
    }

    fun loadExams() {
        viewModelScope.launch {
            busy = true
            busyText = "正在读取考试安排…"
            loadExamsInternal()
            busy = false
        }
    }

    private suspend fun loadExamsInternal() {
        val m = io { Repo.wkhMap(xnxqdm) }
        if (m != null) wkhMap = m
        val list = io { Repo.exams(xnxqdm) }
        if (list != null) exams = list
    }

    fun switchSemester(code: String) {
        xnxqdm = code
        xnxqmc = semesters.firstOrNull { it.code == code }?.name ?: code
        viewModelScope.launch {
            busy = true
            busyText = "正在切换学期…"
            loadTimetableInternal()
            loadExamsInternal()
            evalCourses = emptyList()
            evalClasses = emptyList()
            evalLoaded = false
            classQueried = false
            saveCache()
            busy = false
        }
    }

    // ---------- 评课 ----------

    private suspend fun ensureSemester() {
        if (xnxqdm.isNotBlank()) return
        val boot = io { Repo.bootstrap() }
        if (boot != null) {
            xnxqdm = boot.first
            semesters = boot.second
            xnxqmc = boot.second.firstOrNull { it.code == boot.first }?.name ?: boot.first
        }
    }

    fun loadEvalCourses() {
        viewModelScope.launch {
            busy = true
            busyText = "正在读取待评课程…"
            ensureSemester()
            val list = io { Repo.courseEvalList(xnxqdm) }
            if (list != null) {
                evalCourses = list
                evalLoaded = true
            }
            busy = false
        }
    }

    fun loadEvalClasses() {
        viewModelScope.launch {
            busy = true
            busyText = "正在查询 $evalDate 的随堂评价…"
            ensureSemester()
            val list = io { Repo.classEvalList(xnxqdm, evalDate) }
            if (list != null) {
                evalClasses = list
                classQueried = true
            }
            busy = false
        }
    }

    /** 打开问卷，生成待确认答案 */
    fun prepare(item: PjItem) {
        viewModelScope.launch {
            busy = true
            busyText = "正在读取题目…"
            val res = io {
                val q = Repo.questionnaire(item)
                q to cn.edu.smu.jwhelper.data.Parse.buildAnswer(q, noteText, item.kind == 0)
            }
            busy = false
            if (res == null) return@launch
            val (q, ans) = res
            if (q.questions.isEmpty()) {
                toast("没有取到题目，可能课程还没结课或评价未开放")
                return@launch
            }
            pending = Pending(item, q, ans)
        }
    }

    fun confirmPending() {
        val p = pending ?: return
        viewModelScope.launch {
            busy = true
            busyText = "正在提交评价…"
            val ok = io { Repo.submit(p.item, p.q, p.ans); true }
            busy = false
            if (ok == true) {
                toast("已提交：${p.item.title}")
                pending = null
                if (p.item.kind == 0) loadEvalCourses() else loadEvalClasses()
            }
        }
    }

    fun batchEval(kind: Int) {
        viewModelScope.launch {
            val src = if (kind == 0) evalCourses else evalClasses
            val todo = src.filter { it.tag != "已评" && it.tag != "未结课" }
            if (todo.isEmpty()) {
                toast("没有待评价的")
                return@launch
            }
            busy = true
            val logs = ArrayList<String>()
            for ((i, item) in todo.withIndex()) {
                busyText = "正在评价 ${i + 1}/${todo.size}：${item.title}"
                val r = io {
                    val q = Repo.questionnaire(item)
                    if (q.questions.isEmpty()) {
                        "跳过（无题目）"
                    } else {
                        val ans = cn.edu.smu.jwhelper.data.Parse.buildAnswer(q, noteText, item.kind == 0)
                        Repo.submit(item, q, ans)
                        "成功 ${ans.wtpf} 分"
                    }
                }
                logs.add("${item.title}：${r ?: "失败"}")
            }
            results = logs
            busy = false
            if (kind == 0) loadEvalCourses() else loadEvalClasses()
        }
    }

    fun runDiagnose() {
        viewModelScope.launch {
            busy = true
            busyText = "正在诊断…"
            val r = io { Repo.diagnose() }
            diagText = (r ?: emptyList()).joinToString("\n\n")
            busy = false
        }
    }

    private var pingStarted = false

    /** 前台每 5 分钟戳一下，配合后台 15 分钟心跳，避免会话闲置失效 */
    private fun startPing() {
        if (pingStarted) return
        pingStarted = true
        viewModelScope.launch {
            while (loggedIn) {
                kotlinx.coroutines.delay(5 * 60 * 1000L)
                if (!loggedIn) break
                val alive = withContext(Dispatchers.IO) { Api.isLoggedIn() }
                Prefs.sessionAlive = alive
                Prefs.lastAliveAt = java.text.SimpleDateFormat(
                    "MM-dd HH:mm", java.util.Locale.CHINA
                ).format(java.util.Date())
                if (!alive) {
                    loggedIn = false
                    toast("登录已过期，请重新登录一次")
                    break
                }
            }
            pingStarted = false
        }
    }

    fun logout() {
        sessionChecked = true
        cn.edu.smu.jwhelper.work.KeepAlive.stop(getApplication())
        Api.logout()
        Prefs.clearLogin()
        loggedIn = false
        courses = emptyList()
        exams = emptyList()
        evalCourses = emptyList()
        evalClasses = emptyList()
        timetableLoaded = false
        evalLoaded = false
        classQueried = false
    }

    fun saveNote(t: String) {
        noteText = t
        Prefs.evalText = t
    }

    companion object {
        /** 课表/考试缓存的结构版本；改动解析规则时 +1，旧缓存会被丢弃 */
        private const val CACHE_VER = 3

        fun today(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
    }
}
