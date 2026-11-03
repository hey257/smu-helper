package cn.edu.smu.jwhelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.smu.jwhelper.data.PjItem
import java.time.LocalDate

@Composable
fun EvalScreen(vm: AppVm) {
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab) {
        if (tab == 0) {
            if (vm.evalClasses.isEmpty() && !vm.classQueried) vm.loadEvalClasses()
        } else {
            if (vm.evalCourses.isEmpty()) vm.loadEvalCourses()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = Card,
            contentColor = Ink,
            divider = { Hairline() },
            indicator = {}
        ) {
            listOf("随堂评价", "课程评价").forEachIndexed { i, t ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = {
                        Text(
                            t,
                            fontSize = 12.sp,
                            fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (tab == i) Ink else Sub
                        )
                    }
                )
            }
        }
        Hairline()
        if (tab == 0) ClassEval(vm) else CourseEval(vm)
    }
}

@Composable
private fun CourseEval(vm: AppVm) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Card).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("${vm.evalCourses.size} 门")
            Spacer(Modifier.weight(1f))
            SectionLabel("刷新", Modifier.clickable { vm.loadEvalCourses() })
            Spacer(Modifier.width(14.dp))
            InkButton("全部评完") { vm.batchEval(0) }
        }
        Hairline()

        if (vm.evalCourses.isEmpty()) {
            EmptyView("没有待评课程\n点「刷新」重试")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.evalCourses) { t -> EvalRow(t) { vm.prepare(t) } }
            resultsBlock(vm)
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ClassEval(vm: AppVm) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Card).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.clickable { vm.evalDate = shift(vm.evalDate, -1) }.padding(8.dp)) {
                Text("‹", fontSize = 16.sp, color = Ink)
            }
            Text(vm.evalDate, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Medium)
            Box(Modifier.clickable { vm.evalDate = shift(vm.evalDate, 1) }.padding(8.dp)) {
                Text("›", fontSize = 16.sp, color = Ink)
            }
            SectionLabel("今天", Modifier.padding(start = 4.dp).clickable { vm.evalDate = AppVm.today() })
            Spacer(Modifier.weight(1f))
            InkButton("查询") { vm.loadEvalClasses() }
        }
        Hairline()

        if (!vm.classQueried) {
            EmptyView("选定日期后点「查询」")
            return@Column
        }
        if (vm.evalClasses.isEmpty()) {
            EmptyView("这一天没有可评的课\n换个日期")
            return@Column
        }

        Row(
            Modifier.fillMaxWidth().background(Card).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("${vm.evalClasses.size} 节")
            Spacer(Modifier.weight(1f))
            InkButton("一键提交") { vm.batchEval(1) }
        }
        Hairline()

        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.evalClasses) { t -> EvalRow(t) { vm.prepare(t) } }
            resultsBlock(vm)
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun LazyListScope.resultsBlock(vm: AppVm) {
    if (vm.results.isNotEmpty()) {
        item {
            Column(Modifier.padding(14.dp)) {
                SectionLabel("上次结果")
                Spacer(Modifier.height(6.dp))
                vm.results.forEach { Text("· $it", fontSize = 10.sp, color = Sub, lineHeight = 16.sp) }
            }
        }
    }
}

@Composable
private fun EvalRow(t: PjItem, onEval: () -> Unit) {
    val done = t.tag == "已评"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                if (t.tag.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Tag(t.tag)
                }
            }
            if (t.subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(t.subtitle, fontSize = 10.sp, color = Sub, lineHeight = 14.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        if (done) {
            Text("已评", fontSize = 11.sp, color = Sub)
        } else {
            InkButton("评价") { onEval() }
        }
    }
    Hairline()
}

private fun shift(date: String, delta: Int): String = try {
    LocalDate.parse(date).plusDays(delta.toLong()).toString()
} catch (e: Exception) {
    AppVm.today()
}
