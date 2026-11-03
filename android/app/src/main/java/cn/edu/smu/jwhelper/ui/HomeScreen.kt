package cn.edu.smu.jwhelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(vm: AppVm) {
    val xkVm: XkVm = androidx.lifecycle.viewmodel.compose.viewModel()
    var tab by remember { mutableIntStateOf(0) }
    var semMenu by remember { mutableStateOf(false) }
    val titles = listOf("课表", "考试", "评课", "选课", "我的")

    Column(Modifier.fillMaxSize().background(Bg)) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().background(Card).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(titles[tab], fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.width(10.dp))
            if (tab != 4) {
                Box {
                    Text(
                        (vm.xnxqmc.ifBlank { "—" }) + if (vm.semesters.isNotEmpty()) " ▾" else "",
                        fontSize = 10.sp,
                        color = Sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable(enabled = vm.semesters.isNotEmpty()) { semMenu = true }
                            .padding(vertical = 2.dp)
                    )
                    DropdownMenu(
                        expanded = semMenu,
                        onDismissRequest = { semMenu = false },
                        containerColor = Card
                    ) {
                        vm.semesters.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name.ifBlank { s.code }, fontSize = 12.sp, color = Ink) },
                                onClick = { semMenu = false; vm.switchSemester(s.code) }
                            )
                        }
                    }
                }
            }
        }
        Hairline()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> TimetableScreen(vm)
                1 -> ExamScreen(vm)
                2 -> EvalScreen(vm)
                3 -> XkScreen(xkVm)
                else -> MineScreen(vm)
            }
        }

        Hairline()
        // 底栏
        Row(Modifier.fillMaxWidth().background(Card).padding(vertical = 10.dp)) {
            listOf("课表", "考试", "评课", "选课", "我的").forEachIndexed { i, t ->
                Column(
                    Modifier.weight(1f).clickable { tab = i },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        t,
                        fontSize = 11.sp,
                        color = if (tab == i) Ink else Color(0xFFA8AEB5),
                        fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.size(width = 12.dp, height = 1.5.dp)
                            .background(if (tab == i) Ink else Color.Transparent)
                    )
                }
            }
        }
    }

    if (vm.busy) {
        Box(Modifier.fillMaxSize().background(Color(0x22000000)), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(2.dp), color = Card) {
                Text(vm.busyText, fontSize = 12.sp, color = Ink, modifier = Modifier.padding(18.dp))
            }
        }
    }

    vm.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.message = null },
            containerColor = Card,
            confirmButton = {
                TextButton(onClick = { vm.message = null }) { Text("好", color = Ink, fontSize = 13.sp) }
            },
            text = { Text(msg, fontSize = 13.sp, color = Ink, lineHeight = 20.sp) }
        )
    }

    vm.pending?.let { p -> PendingDialog(p, vm) }
}

@Composable
private fun PendingDialog(p: Pending, vm: AppVm) {
    var all by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = { vm.pending = null },
        containerColor = Card,
        title = {
            Column {
                Text(p.item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    (if (p.item.kind == 0) "课程评价" else "随堂评价") +
                        " · ${p.q.questions.size} 题 · ${p.ans.wtpf} 分",
                    fontSize = 10.sp, color = Cold
                )
            }
        },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(scroll)) {
                val show = if (all) p.ans.lines else p.ans.lines.take(5)
                show.forEach {
                    Text("· $it", fontSize = 10.sp, color = Ink, lineHeight = 16.sp)
                }
                if (!all && p.ans.lines.size > 5) {
                    Text("… 共 ${p.ans.lines.size} 条", fontSize = 10.sp, color = Sub)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "星级题至少 2 条不满星、总分不超上限、不会全选同一项 —— 这是学校的硬校验。",
                    fontSize = 9.sp, color = Sub, lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.confirmPending() }) {
                Text("提交", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { all = !all }) { Text(if (all) "收起" else "全部", fontSize = 12.sp, color = Sub) }
                TextButton(onClick = { vm.pending = null }) { Text("取消", fontSize = 12.sp, color = Sub) }
            }
        }
    )
}
