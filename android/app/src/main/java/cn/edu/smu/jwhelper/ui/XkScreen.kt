package cn.edu.smu.jwhelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.smu.jwhelper.data.XkRepo

@Composable
fun XkScreen(vm: XkVm) {
    var expandWish by remember { mutableStateOf(false) }
    var confirmDrop by remember { mutableStateOf<XkRepo.XkCourse?>(null) }

    val cfg = vm.cfg
    val mineKcdm = remember(vm.mine) { vm.mine.map { it.kcdm }.toSet() }

    Column(Modifier.fillMaxSize().background(Bg)) {
        // ---- 顶部状态 ----
        Column(Modifier.fillMaxWidth().background(Card).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cfg?.name ?: "选课报名",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink
                )
                Spacer(Modifier.width(8.dp))
                when {
                    cfg == null -> Tag("读取中")
                    cfg.canPick -> Tag(cfg.stageName + "进行中", GreenLine, GreenLine.copy(alpha = 0.4f))
                    cfg.open -> Tag(cfg.stageName, Cold, Cold.copy(alpha = 0.35f))
                    else -> Tag("未开放")
                }
                Spacer(Modifier.weight(1f))
                SectionLabel("刷新", Modifier.clickable { vm.refresh() })
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    cfg == null -> "正在读取选课批次…"
                    cfg.canPick && cfg.stageEnd.isNotBlank() ->
                        "${cfg.xnxqmc} · ${cfg.stageName}至 ${cfg.stageEnd.take(16)}" +
                            if (cfg.canCancel) " · 可退选" else " · 不可退选"
                    cfg.open -> "${cfg.xnxqmc} · 当前是${cfg.stageName}，不能选课"
                    else -> "${cfg.xnxqmc} · " + XkRepo.nextStageText(cfg)
                },
                fontSize = 10.sp, color = Sub, lineHeight = 15.sp
            )
            if (cfg != null && cfg.allStages.isNotEmpty() && !cfg.canPick) {
                Spacer(Modifier.height(6.dp))
                cfg.allStages.forEach { (name, s, e) ->
                    Text(
                        "$name  ${s.take(16)} — ${e.take(16)}",
                        fontSize = 9.sp, color = Color(0xFF9AA1A8), lineHeight = 14.sp
                    )
                }
            }
        }
        Hairline()

        // ---- 自动抢课 ----
        Column(Modifier.fillMaxWidth().background(Card).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).clickable { expandWish = !expandWish }
                ) {
                    Text("自动抢课", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "清单 ${vm.wishes.size} 门" + if (vm.autoOn) " · 已开启" else "",
                        fontSize = 10.sp, color = if (vm.autoOn) GreenLine else Sub
                    )
                }
                Switch(
                    checked = vm.autoOn,
                    onCheckedChange = { vm.setAuto(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFCBD0D5),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }

            if (expandWish || vm.wishes.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                if (vm.wishes.isEmpty()) {
                    Text(
                        "在下面「可选」列表里把想选的课加进来，开启后到点自动提交。",
                        fontSize = 9.sp, color = Color(0xFF9AA1A8), lineHeight = 14.sp
                    )
                } else {
                    vm.wishes.forEach { w ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(w.kcmc, fontSize = 12.sp, color = Ink, modifier = Modifier.weight(1f))
                            if (cfg?.enableQz == true) {
                                QzStepper(w.qz, cfg.myQz) { vm.setWishQz(w.kcdm, it) }
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                "移除", fontSize = 10.sp, color = Sub,
                                modifier = Modifier.clickable { vm.removeWish(w.kcdm) }.padding(4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineButton("立即抢一次") { vm.grabNow() }
                Spacer(Modifier.width(8.dp))
                if (vm.wishes.isNotEmpty()) {
                    SectionLabel(if (expandWish) "收起清单" else "管理清单",
                        Modifier.clickable { expandWish = !expandWish })
                }
            }
            if (vm.log.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(vm.log, fontSize = 9.sp, color = Sub, lineHeight = 14.sp)
            }
        }
        Hairline()

        // ---- 子标签 ----
        Row(Modifier.fillMaxWidth().background(Card)) {
            listOf("可选 ${vm.listed.size}", "已选 ${vm.mine.size}").forEachIndexed { i, t ->
                Column(
                    Modifier.weight(1f).clickable { vm.tab = i }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        t, fontSize = 12.sp,
                        color = if (vm.tab == i) Ink else Sub,
                        fontWeight = if (vm.tab == i) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.width(20.dp).height(1.5.dp)
                            .background(if (vm.tab == i) Ink else Color.Transparent)
                    )
                }
            }
        }
        Hairline()

        // ---- 列表 ----
        when {
            vm.loading -> LoadingView("读取选课信息")
            cfg == null -> EmptyView("这个学期没有选课批次\n有选课任务时这里会自动出现")
            vm.tab == 0 && vm.listed.isEmpty() -> EmptyView("暂无可选课程\n开放选课后点右上角刷新")
            vm.tab == 1 && vm.mine.isEmpty() -> EmptyView("还没选任何课")
            else -> {
                val data = if (vm.tab == 0) vm.listed else vm.mine
                LazyColumn(Modifier.fillMaxSize()) {
                    items(data, key = { (if (vm.tab == 0) "l" else "m") + it.kcrwdm }) { c ->
                        if (vm.tab == 0) {
                            ListedRow(
                                c = c,
                                inWish = vm.wishes.any { it.kcdm == c.kcdm },
                                mine = mineKcdm.contains(c.kcdm),
                                canPick = cfg.canPick,
                                onWish = { vm.toggleWish(c) },
                                onPick = { vm.pick(c) }
                            )
                        } else {
                            MineRow(c, cfg.canCancel) { confirmDrop = c }
                        }
                        Hairline()
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    confirmDrop?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDrop = null },
            containerColor = Card,
            title = { Text("退选", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink) },
            text = { Text("确定退选《${c.kcmc}》？", fontSize = 13.sp, color = Ink) },
            confirmButton = {
                TextButton(onClick = { vm.drop(c); confirmDrop = null }) {
                    Text("退选", fontSize = 13.sp, color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDrop = null }) { Text("取消", fontSize = 13.sp, color = Sub) }
            }
        )
    }

    vm.msg?.let {
        AlertDialog(
            onDismissRequest = { vm.msg = null },
            containerColor = Card,
            confirmButton = {
                TextButton(onClick = { vm.msg = null }) { Text("好", fontSize = 13.sp, color = Ink) }
            },
            text = { Text(it, fontSize = 13.sp, color = Ink, lineHeight = 20.sp) }
        )
    }

    if (vm.busy) {
        Box(Modifier.fillMaxSize().background(Color(0x22000000)), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(2.dp), color = Card) {
                Text("处理中…", fontSize = 12.sp, color = Ink, modifier = Modifier.padding(18.dp))
            }
        }
    }
}

@Composable
private fun ListedRow(
    c: XkRepo.XkCourse,
    inWish: Boolean,
    mine: Boolean,
    canPick: Boolean,
    onWish: () -> Unit,
    onPick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().background(Card).padding(horizontal = 14.dp, vertical = 11.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.kcmc, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (mine) {
                    Spacer(Modifier.width(6.dp))
                    Tag("已选", GreenLine, GreenLine.copy(alpha = 0.4f))
                } else if (c.full) {
                    Spacer(Modifier.width(6.dp))
                    Tag("满")
                }
                if (inWish) {
                    Spacer(Modifier.width(6.dp))
                    Tag("清单", Cold, Cold.copy(alpha = 0.35f))
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                (if (c.teaxm.isNotBlank()) c.teaxm + " · " else "") + c.jxbmc,
                fontSize = 10.sp, color = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${fmt(c.xf)} 学分 · 已选 ${c.jxbrs}/${if (c.pkrs <= 0) "—" else c.pkrs}" +
                    (if (c.kcflmc.isNotBlank()) " · ${c.kcflmc}" else ""),
                fontSize = 9.sp, color = Color(0xFF9AA1A8)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            if (!mine && canPick && !c.full) {
                LineButton("选") { onPick() }
            }
            if (!mine) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (inWish) "移出清单" else "加入清单",
                    fontSize = 9.sp, color = Sub,
                    modifier = Modifier.clickable { onWish() }.padding(2.dp)
                )
            }
        }
    }
}

@Composable
private fun MineRow(c: XkRepo.XkCourse, canCancel: Boolean, onDrop: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Card).padding(horizontal = 14.dp, vertical = 11.dp)) {
        Column(Modifier.weight(1f)) {
            Text(c.kcmc, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
            Spacer(Modifier.height(3.dp))
            Text(
                (if (c.teaxm.isNotBlank()) c.teaxm + " · " else "") + fmt(c.xf) + " 学分",
                fontSize = 10.sp, color = Sub
            )
        }
        if (canCancel) {
            Text(
                "退选", fontSize = 10.sp, color = Danger,
                modifier = Modifier.clickable { onDrop() }.padding(6.dp)
            )
        }
    }
}

@Composable
private fun QzStepper(qz: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "−", fontSize = 12.sp, color = Ink,
            modifier = Modifier
                .border(0.8.dp, Line, RoundedCornerShape(2.dp))
                .clickable { onChange(if (qz <= 0) 0 else qz - 1) }
                .padding(horizontal = 7.dp, vertical = 1.dp)
        )
        Text("$qz", fontSize = 11.sp, color = Ink, modifier = Modifier.padding(horizontal = 6.dp))
        Text(
            "+", fontSize = 12.sp, color = Ink,
            modifier = Modifier
                .border(0.8.dp, Line, RoundedCornerShape(2.dp))
                .clickable { if (qz < max) onChange(qz + 1) }
                .padding(horizontal = 7.dp, vertical = 1.dp)
        )
    }
}

private fun fmt(v: Double): String =
    if (v == v.toInt().toDouble()) v.toInt().toString() else String.format("%.1f", v)
