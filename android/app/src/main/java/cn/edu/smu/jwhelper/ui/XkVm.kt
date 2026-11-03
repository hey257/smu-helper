package cn.edu.smu.jwhelper.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.smu.jwhelper.core.Prefs
import cn.edu.smu.jwhelper.data.XkRepo
import cn.edu.smu.jwhelper.net.Api
import cn.edu.smu.jwhelper.work.XkScheduler
import cn.edu.smu.jwhelper.work.XkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class XkVm(app: Application) : AndroidViewModel(app) {

    var types by mutableStateOf<List<XkRepo.XkType>>(emptyList())
    var dm by mutableStateOf("")
    var cfg by mutableStateOf<XkRepo.XkConfig?>(null)
    var listed by mutableStateOf<List<XkRepo.XkCourse>>(emptyList())
    var mine by mutableStateOf<List<XkRepo.XkCourse>>(emptyList())

    var wishes by mutableStateOf<List<XkRepo.Wish>>(emptyList())
    var autoOn by mutableStateOf(false)
    var log by mutableStateOf("")
    var loading by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var msg by mutableStateOf<String?>(null)
    var searched by mutableStateOf(false)

    var tab by mutableStateOf(0)   // 0 可选 1 已选

    init {
        Prefs.init(app)
        autoOn = Prefs.xkAuto
        log = Prefs.xkLog
        wishes = XkStore.parseWishes(Prefs.xkWish)
        refresh()
        if (autoOn) startPoll()
    }

    fun toast(s: String) {
        msg = s
    }

    private suspend fun <T> io(block: () -> T): T? = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { toast(e.message ?: "操作失败") }
            null
        }
    }

    /** 载入选课类型 + 配置 + 课程 */
    fun refresh() {
        viewModelScope.launch {
            loading = true
            val ts = io { XkRepo.types() } ?: emptyList()
            types = ts
            if (ts.isNotEmpty()) {
                if (dm.isBlank() || ts.none { it.dm == dm }) dm = ts.first().dm
                loadConfig()
            }
            loading = false
        }
    }

    private suspend fun loadConfig() {
        val c = io { XkRepo.config(dm) }
        cfg = c
        if (c == null) return
        reloadCourses()
    }

    fun reloadCourses() {
        viewModelScope.launch {
            if (dm.isBlank()) return@launch
            busy = true
            val pair = io { XkRepo.courses(dm) }
            val chosen = io { XkRepo.chosen(dm) }
            if (pair != null) listed = pair.second
            if (chosen != null) mine = chosen
            busy = false
        }
    }

    fun switchType(d: String) {
        if (d == dm) return
        dm = d
        listed = emptyList()
        mine = emptyList()
        viewModelScope.launch {
            loading = true
            loadConfig()
            loading = false
        }
    }

    // ---------- 清单 ----------

    private fun saveWishes() {
        Prefs.xkWish = XkStore.dumpWishes(wishes)
    }

    fun toggleWish(c: XkRepo.XkCourse) {
        wishes = if (wishes.any { it.kcdm == c.kcdm }) {
            wishes.filterNot { it.kcdm == c.kcdm }
        } else {
            wishes + XkRepo.Wish(c.kcdm, c.kcmc, -1)
        }
        saveWishes()
    }

    fun removeWish(kcdm: String) {
        wishes = wishes.filterNot { it.kcdm == kcdm }
        saveWishes()
    }

    fun setWishQz(kcdm: String, qz: Int) {
        wishes = wishes.map { if (it.kcdm == kcdm) it.copy(qz = qz) else it }
        saveWishes()
    }

    // ---------- 自动 ----------

    fun setAuto(on: Boolean) {
        autoOn = on
        Prefs.xkAuto = on
        if (on) {
            XkScheduler.start(getApplication())
            nextOpenMillis()?.let { XkScheduler.burst(getApplication(), it) }
            startPoll()
            toast("已开启：App 开着时每 2 秒查一次，关掉也有后台兜底")
        } else {
            XkScheduler.stop(getApplication())
            stopPoll()
        }
    }

    /** 下一次选课开放的毫秒时间（没有则 null） */
    private fun nextOpenMillis(): Long? {
        val c = cfg ?: return null
        if (c.canPick) return System.currentTimeMillis()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
        val now = System.currentTimeMillis()
        return c.allStages
            .filter { it.second.isNotBlank() }
            .mapNotNull { runCatching { fmt.parse(it.second)?.time }.getOrNull() }
            .filter { it > now }
            .minOrNull()
    }

    private var pollJob: Job? = null
    private var lastGrabAt = 0L
    private var grabbing = false

    private fun startPoll() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val open = cfg?.canPick == true
                if (autoOn && wishes.isNotEmpty() && !Api.cookie.isNullOrBlank()) {
                    val c = withContext(Dispatchers.IO) {
                        try {
                            XkRepo.config(dm.ifBlank { types.firstOrNull()?.dm ?: "" })
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (c != null) {
                        cfg = c
                        if (c.canPick && System.currentTimeMillis() - lastGrabAt > 3000) {
                            lastGrabAt = System.currentTimeMillis()
                            grabNow(silent = true)
                        }
                    }
                }
                delay(if (open) 3000 else 10000)
            }
        }
    }

    private fun stopPoll() {
        pollJob?.cancel()
        pollJob = null
    }

    /** 抢一轮 */
    fun grabNow(silent: Boolean = false) {
        if (wishes.isEmpty()) {
            if (!silent) toast("清单是空的，先把想选的课加进来")
            return
        }
        if (grabbing) return
        grabbing = true
        viewModelScope.launch {
            try {
            if (!silent) busy = true
            // 日志回调在 IO 线程，先收集，结束后统一切回主线程写状态
            val collected = ArrayList<String>()
            val logs = io {
                XkRepo.grabRound(wishes) { line -> collected.add(line) }
            } ?: emptyList()
            if (collected.isNotEmpty()) log = stamp() + " " + collected.last()
            if (logs.isNotEmpty()) {
                log = stamp() + " " + logs.takeLast(3).joinToString("；")
                Prefs.xkLog = log
                // 抢到的从清单移除
                val after = io { XkRepo.chosen(dm) } ?: emptyList()
                val nowMine = after.map { it.kcdm }.toSet()
                if (nowMine.isNotEmpty()) {
                    wishes = wishes.filterNot { nowMine.contains(it.kcdm) }
                    saveWishes()
                    mine = after
                }
                if (!silent) reloadCourses()
            }
            if (!silent) busy = false
            if (!silent && logs.isNotEmpty()) toast(logs.last())
            } finally {
                grabbing = false
            }
        }
    }

    /** 选课/退课（手动） */
    fun pick(c: XkRepo.XkCourse) {
        val conf = cfg ?: return
        viewModelScope.launch {
            busy = true
            val r = io {
                val qz = if (conf.enableQz) 0 else -1
                val xxyq = if (conf.enableXxyq) conf.xxyq.firstOrNull()?.first ?: "" else ""
                var res = XkRepo.add(dm, c, qz, xxyq)
                if (!res.ok && res.askAgain) res = XkRepo.addConfirm(dm, c, qz, xxyq)
                res
            }
            busy = false
            if (r != null) {
                toast(if (r.ok) "${c.kcmc}：选课成功" else r.msg)
                reloadCourses()
            }
        }
    }

    fun drop(c: XkRepo.XkCourse) {
        viewModelScope.launch {
            busy = true
            val r = io { XkRepo.cancel(dm, c) }
            busy = false
            if (r != null) {
                toast(if (r.ok) "${c.kcmc}：已退选" else r.msg)
                reloadCourses()
            }
        }
    }

    fun stamp(): String =
        java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())

    /** 当前学期名（顶部展示用） */
    fun semesterName(): String = Prefs.xnxqmc
}
