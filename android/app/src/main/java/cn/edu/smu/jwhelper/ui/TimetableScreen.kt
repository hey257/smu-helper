package cn.edu.smu.jwhelper.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.smu.jwhelper.core.Prefs
import cn.edu.smu.jwhelper.data.Course
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private val TIME_COL: Dp = 44.dp
private val ROW_H: Dp = 46.dp
private val DAY_NAMES = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(vm: AppVm) {
    var weekly by remember { mutableStateOf(true) }
    var hideOffline by remember { mutableStateOf(Prefs.hideOffline) }
    var detail by remember { mutableStateOf<List<Course>?>(null) }

    val all = vm.courses
    val courses = remember(all, hideOffline) { if (hideOffline) all.filter { !it.online } else all }

    val maxWeek = remember(all) { (all.flatMap { it.weeks }.maxOrNull() ?: 20).coerceAtLeast(1) }
    var week by remember { mutableIntStateOf(vm.curWeek.coerceIn(1, maxWeek)) }
    var weekInit by remember { mutableStateOf(vm.curWeek > 1) }
    var dir by remember { mutableIntStateOf(1) }

    // 当前周要等网络返回，第一次拿到时对齐到当前周；之后用户手动翻页就不再抢回
    LaunchedEffect(vm.curWeek, vm.timetableLoaded, maxWeek) {
        if (!weekInit && vm.timetableLoaded && vm.curWeek > 0) {
            week = vm.curWeek.coerceIn(1, maxWeek)
            weekInit = true
        }
    }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(week) { vm.loadWeekDates(week) }

    fun prev() {
        if (week > 1) {
            dir = -1
            week -= 1
        }
    }

    fun next() {
        if (week < maxWeek) {
            dir = 1
            week += 1
        }
    }

    fun goto(date: LocalDate) {
        day = date
        if (weekly && vm.week1.isNotBlank()) {
            val w = try {
                ChronoUnit.WEEKS.between(LocalDate.parse(vm.week1), date).toInt() + 1
            } catch (e: Exception) {
                0
            }
            if (w in 1..maxWeek) {
                dir = if (w >= week) 1 else -1
                week = w
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Card).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(if (weekly) "周" else "日", Modifier.clickable { weekly = !weekly })
            Spacer(Modifier.width(16.dp))
            SectionLabel(
                if (hideOffline) "显示全部" else "隐藏线上",
                Modifier.clickable {
                    hideOffline = !hideOffline
                    Prefs.hideOffline = hideOffline
                }
            )
            Spacer(Modifier.weight(1f))
            SectionLabel("刷新", Modifier.clickable { vm.loadTimetable() })
        }
        Hairline()

        if (!vm.timetableLoaded) {
            LoadingView("读取课表")
            return@Column
        }
        if (courses.isEmpty()) {
            EmptyView("这个学期没有课表数据\n可切换学期，或点「刷新」")
            return@Column
        }

        if (weekly) {
            Row(
                Modifier.fillMaxWidth().background(Card).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.clickable { prev() }.padding(10.dp)) { Text("‹", fontSize = 18.sp, color = Ink) }
                Column(
                    Modifier.weight(1f).clickable { showPicker = true }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("第 $week 周", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    val d0 = vm.weekDates.firstOrNull()
                    val d6 = vm.weekDates.getOrNull(6)
                    Text(
                        if (d0 != null && d6 != null && d0.length >= 10 && d6.length >= 10)
                            "${d0.substring(5, 7)}.${d0.substring(8, 10)} — ${d6.substring(5, 7)}.${d6.substring(8, 10)}  ⌄"
                        else "选择日期  ⌄",
                        fontSize = 10.sp, color = Cold
                    )
                }
                Box(Modifier.clickable { next() }.padding(10.dp)) { Text("›", fontSize = 18.sp, color = Ink) }
            }
            Hairline()

            AnimatedContent(
                targetState = week,
                transitionSpec = {
                    if (dir >= 0) {
                        (slideInHorizontally { it / 2 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 2 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 2 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 2 } + fadeOut())
                    }
                },
                label = "week",
                modifier = Modifier.fillMaxSize()
            ) { w ->
                WeekGrid(courses, all, w, vm.weekDates, onPrev = { prev() }, onNext = { next() }) { detail = it }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().background(Card).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.clickable { day = day.minusDays(1) }.padding(10.dp)) {
                    Text("‹", fontSize = 18.sp, color = Ink)
                }
                Column(
                    Modifier.weight(1f).clickable { showPicker = true }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${day.monthValue}月${day.dayOfMonth}日  周" + "一二三四五六日"[day.dayOfWeek.value - 1],
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink
                    )
                    Text(if (day == LocalDate.now()) "今天  ⌄" else "选择日期  ⌄", fontSize = 10.sp, color = Cold)
                }
                Box(Modifier.clickable { day = day.plusDays(1) }.padding(10.dp)) {
                    Text("›", fontSize = 18.sp, color = Ink)
                }
            }
            Hairline()
            DayList(courses, day, vm.week1) { detail = it }
        }
    }

    if (showPicker) {
        val initial = try {
            LocalDate.parse(
                if (weekly && vm.week1.isNotBlank())
                    LocalDate.parse(vm.week1).plusDays(((week - 1) * 7).toLong()).toString()
                else day.toString()
            ).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        goto(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("确定", color = Ink, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消", color = Sub, fontSize = 13.sp) }
            }
        ) {
            DatePicker(
                state = state,
                showModeToggle = false,
                title = {
                    Text(
                        "选择日期",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        fontSize = 13.sp, color = Sub
                    )
                }
            )
        }
    }

    detail?.let { list ->
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = Card,
            confirmButton = {
                TextButton(onClick = { detail = null }) { Text("关闭", color = Ink, fontSize = 13.sp) }
            },
            title = {
                Text(
                    if (list.size == 1) "课程" else "${list.size} 门课",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink
                )
            },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    list.forEach { c ->
                        CourseDetail(c, vm.wkhMap[c.name] ?: "")
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        )
    }
}

@Composable
private fun CourseDetail(c: Course, wkh: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            if (c.isExam) {
                Spacer(Modifier.width(6.dp))
                SolidTag("考试")
            } else if (c.online) {
                Spacer(Modifier.width(6.dp))
                Tag(c.specialTag.ifBlank { "不用到场" }, GreenLine, GreenLine.copy(alpha = 0.4f))
            }
        }
        Spacer(Modifier.height(6.dp))
        DetailLine("时间", "周${"一二三四五六日"[c.weekDay - 1]} ${c.startHm}–${c.endHm}")
        if (c.periodText.isNotBlank()) DetailLine("节次", c.periodText)
        if (c.teacher.isNotBlank()) DetailLine("教师", c.teacher)
        DetailLine("地点", c.place.ifBlank { "未写明" })
        if (c.stage.isNotBlank()) DetailLine("环节", c.stage)
        if (c.weeks.isNotEmpty()) DetailLine("周次", c.weeks.joinToString(","))
        if (c.classname.isNotBlank()) DetailLine("教学班", c.classname)
        DetailLine("网考号", wkh.ifBlank { "—" })
    }
}

@Composable
private fun DetailLine(k: String, v: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(k, fontSize = 11.sp, color = Sub, modifier = Modifier.width(46.dp))
        Text(v, fontSize = 12.sp, color = Ink)
    }
}

/** 一天分若干节；每节课按 ps~pe 占若干节，块按节次定位 */
@Composable
private fun WeekGrid(
    courses: List<Course>,
    allCourses: List<Course>,
    week: Int,
    dates: List<String>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDetail: (List<Course>) -> Unit
) {
    val inWeek = courses.filter { it.weeks.isEmpty() || it.weeks.contains(week) }
    val maxP = remember(allCourses) {
        (allCourses.mapNotNull { it.endP.takeIf { p -> p > 0 } }.maxOrNull() ?: 12)
            .coerceIn(6, 20)
    }
    // 每节的起止时间：排课里有真实时间的优先用，缺的按 40 分钟一节 + 课间 5 分钟补齐
    val periods = remember(allCourses, maxP) {
        val st = HashMap<Int, String>()
        val en = HashMap<Int, String>()
        allCourses.forEach { c ->
            if (c.startP in 1..20 && c.startHm.isNotBlank()) {
                val old = st[c.startP]
                if (old == null || c.startHm < old) st[c.startP] = c.startHm
            }
            if (c.endP in 1..20 && c.endHm.isNotBlank()) {
                val old = en[c.endP]
                if (old == null || c.endHm > old) en[c.endP] = c.endHm
            }
        }

        fun toMin(s: String): Int? {
            val parts = s.split(":")
            if (parts.size < 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return h * 60 + m
        }

        fun fmt(v: Int): String = String.format("%02d:%02d", v / 60, v % 60)

        val out = ArrayList<Pair<String, String>>(maxP)
        var prevStart = -1
        for (p in 1..maxP) {
            val s = toMin(st[p] ?: "") ?: if (prevStart >= 0) prevStart + 45 else -1
            val e = toMin(en[p] ?: "") ?: if (s >= 0) s + 40 else -1
            out.add(if (s < 0 || e < 0) "" to "" else fmt(s) to fmt(e))
            prevStart = s
        }
        out
    }

    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val bodyH = ROW_H * maxP

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dayW = (maxWidth - TIME_COL) / 7

        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(week) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { _, drag -> total += drag },
                        onDragEnd = {
                            if (total < -threshold) onNext()
                            else if (total > threshold) onPrev()
                        }
                    )
                }
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // 星期表头（和网格同一套宽度）
                Row(Modifier.fillMaxWidth().height(42.dp).background(Card)) {
                    Box(Modifier.width(TIME_COL))
                    for (d in 1..7) {
                        Column(
                            Modifier.width(dayW).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(DAY_NAMES[d - 1], fontSize = 10.sp, color = Ink)
                            val dt = dates.getOrNull(d - 1)
                            Text(
                                if (dt != null && dt.length >= 10)
                                    "${dt.substring(5, 7)}/${dt.substring(8, 10)}" else "  ",
                                fontSize = 9.sp, color = Color(0xFF9AA1A8)
                            )
                        }
                    }
                }
                Hairline()

                Row(Modifier.fillMaxWidth().height(bodyH)) {
                    // 节次 + 时间
                    Column(Modifier.width(TIME_COL)) {
                        for (p in 1..maxP) {
                            Box(
                                Modifier.fillMaxWidth().height(ROW_H).padding(top = 2.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$p", fontSize = 10.sp, color = Ink, fontWeight = FontWeight.Medium)
                                    val (ts, te) = periods.getOrElse(p - 1) { "" to "" }
                                    if (ts.isNotBlank()) {
                                        Text(ts, fontSize = 7.5.sp, lineHeight = 9.sp, color = Sub)
                                        Text(
                                            te, fontSize = 7.5.sp, lineHeight = 9.sp,
                                            color = Color(0xFF9AA1A8)
                                        )
                                    }
                                }
                            }
                            Hairline(Color(0xFFF4F5F6))
                        }
                    }
                    // 每一天
                    for (d in 1..7) {
                        Box(Modifier.width(dayW).fillMaxHeight()) {
                            val list = inWeek.filter { it.weekDay == d }
                            list.forEach { c ->
                                val sp = c.startP.coerceIn(1, maxP)
                                val ep = c.endP.coerceIn(sp, maxP)
                                val bg = Card
                                val edge = when {
                                    c.isExam -> Danger.copy(alpha = 0.6f)
                                    c.online -> GreenLine.copy(alpha = 0.6f)
                                    else -> Line
                                }
                                Column(
                                    Modifier
                                        .offset(y = ROW_H * (sp - 1))
                                        .width(dayW)
                                        .height(ROW_H * (ep - sp + 1))
                                        .padding(1.dp)
                                        .background(bg, RoundedCornerShape(2.dp))
                                        .border(
                                            if (c.isExam || c.online) 1.dp else 0.8.dp,
                                            edge,
                                            RoundedCornerShape(2.dp)
                                        )
                                        .clickable { onDetail(listOf(c)) }
                                        .padding(3.dp)
                                ) {
                                    Text(
                                        c.name,
                                        fontSize = 8.5.sp,
                                        lineHeight = 10.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Ink,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (c.isExam) {
                                        Text("考试", fontSize = 7.5.sp, color = Danger, fontWeight = FontWeight.Bold)
                                    } else if (c.online) {
                                        Text("线上", fontSize = 7.5.sp, color = GreenLine)
                                    }
                                    if (c.teacher.isNotBlank() && (ep - sp) >= 1) {
                                        Text(
                                            c.teacher, fontSize = 7.5.sp, lineHeight = 9.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = Sub
                                        )
                                    }
                                    if (c.place.isNotBlank()) {
                                        Text(
                                            c.place, fontSize = 7.5.sp, lineHeight = 9.sp,
                                            maxLines = if ((ep - sp) >= 2) 2 else 1,
                                            overflow = TextOverflow.Ellipsis, color = Sub
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (inWeek.isEmpty()) {
                    Text(
                        "第 $week 周没有课",
                        fontSize = 12.sp, color = Sub,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun DayList(courses: List<Course>, day: LocalDate, week1: String, onDetail: (List<Course>) -> Unit) {
    val wd = day.dayOfWeek.value
    val w = try {
        if (week1.isBlank()) 0
        else ChronoUnit.WEEKS.between(LocalDate.parse(week1), day).toInt() + 1
    } catch (e: Exception) {
        0
    }
    val list = courses.filter { c ->
        c.weekDay == wd && (c.weeks.isEmpty() || w <= 0 || c.weeks.contains(w))
    }.sortedBy { it.startP }

    if (list.isEmpty()) {
        EmptyView("这一天没有课")
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        list.forEach { c ->
            Row(
                Modifier.fillMaxWidth().clickable { onDetail(listOf(c)) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column(Modifier.width(58.dp)) {
                    Text(
                        if (c.periodText.isNotBlank()) c.periodText else c.startHm,
                        fontSize = 11.sp, color = Ink, fontWeight = FontWeight.Medium
                    )
                    Text("${c.startHm}–${c.endHm}", fontSize = 9.sp, color = Sub)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                        if (c.isExam) {
                            Spacer(Modifier.width(6.dp))
                            SolidTag("考")
                        } else if (c.online) {
                            Spacer(Modifier.width(6.dp))
                            Tag(c.specialTag.ifBlank { "线上" }, GreenLine, GreenLine.copy(alpha = 0.4f))
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        (if (c.teacher.isNotBlank()) c.teacher + "  " else "") + c.place.ifBlank { "地点未写明" },
                        fontSize = 11.sp, color = Sub
                    )
                }
            }
            Hairline()
        }
        Spacer(Modifier.height(24.dp))
    }
}
