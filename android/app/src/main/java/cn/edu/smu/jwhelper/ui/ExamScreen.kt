package cn.edu.smu.jwhelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.smu.jwhelper.data.ExamItem

@Composable
fun ExamScreen(vm: AppVm) {
    var query by remember { mutableStateOf("") }
    var bySubject by remember { mutableStateOf(false) }

    val list = remember(vm.exams, query, bySubject) {
        val f = if (query.isBlank()) vm.exams
        else vm.exams.filter { it.course.contains(query, true) || it.place.contains(query, true) }
        if (bySubject) f.sortedBy { it.course } else f.sortedBy { it.date + it.time }
    }

    Column(Modifier.fillMaxSize()) {
        // 搜索行
        Row(
            Modifier.fillMaxWidth().background(Card).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.weight(1f).height(32.dp)
                    .border(0.8.dp, Line, RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text("搜索科目 / 考场", fontSize = 12.sp, color = Color(0xFFB5BAC0))
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp, color = Ink),
                    cursorBrush = SolidColor(Ink),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(12.dp))
            SectionLabel(if (bySubject) "按科目" else "按时间", Modifier.clickable { bySubject = !bySubject })
            Spacer(Modifier.width(12.dp))
            SectionLabel("刷新", Modifier.clickable { vm.loadExams() })
        }
        Hairline()

        if (vm.exams.isEmpty()) {
            EmptyView("没有考试安排\n（如果确实有考试，点「刷新」）")
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(list) { ExamRow(it) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ExamRow(e: ExamItem) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Column(Modifier.width(64.dp)) {
            Text(
                e.date.ifBlank { "—" },
                fontSize = 11.sp, color = Ink, fontWeight = FontWeight.Medium
            )
            Text(e.time.ifBlank { "" }, fontSize = 10.sp, color = Sub)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.course, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                if (e.type.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Tag(e.type)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "考场 " + e.place.ifBlank { "未公布" },
                fontSize = 11.sp, color = if (e.place.isBlank()) Sub else Ink
            )
            Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (e.seat.isNotBlank()) {
                    Tag("座位 " + e.seat)
                    Spacer(Modifier.width(6.dp))
                }
                if (e.wkh.isNotBlank()) Tag("网考号 " + e.wkh, Ink, Color(0xFFCCD1D6))
                else Tag("网考号 —")
            }
            if (e.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(e.note, fontSize = 10.sp, color = Sub)
            }
        }
    }
    Hairline()
}
