package cn.edu.smu.jwhelper.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.smu.jwhelper.core.Prefs

@Composable
fun MineScreen(vm: AppVm) {
    var showDiag by remember { mutableStateOf(false) }
    val clip = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        Block {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("显示其他教学班的课", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "分组课（PBL、见习等）可能排在别的教学班里，只按自己的班过滤会漏课。默认全部列出，格子里优先显示你自己的班。",
                        fontSize = 10.sp, color = Sub, lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = vm.showAllClasses,
                    onCheckedChange = { vm.applyShowAll(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFCBD0D5),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
        }
        Hairline()

        Block {
            SectionLabel("填空题填写内容")
            Spacer(Modifier.height(8.dp))
            Text(
                "课程评价的填空是必答题，用这段文字填；随堂评价不填填空题。",
                fontSize = 10.sp, color = Sub, lineHeight = 15.sp
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(38.dp)
                    .border(0.8.dp, Line, RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = vm.noteText,
                    onValueChange = { vm.saveNote(it) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp, color = Ink),
                    cursorBrush = SolidColor(Ink),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Hairline()

        Block {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动随堂评价", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
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
            if (vm.autoOn) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("每天")
                    Spacer(Modifier.width(8.dp))
                    Stepper(vm.autoHour, 0, 23) { vm.setAutoTime(it, vm.autoMinute) }
                    Text(":", fontSize = 12.sp, color = Ink, modifier = Modifier.padding(horizontal = 2.dp))
                    Stepper(vm.autoMinute, 0, 59) { vm.setAutoTime(vm.autoHour, it) }
                }
                Spacer(Modifier.height(10.dp))
                LineButton("立即按自动流程跑一次") { vm.runAutoNow() }
            }
            if (vm.autoLog.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("上次：${vm.autoLog}", fontSize = 10.sp, color = Sub, lineHeight = 15.sp)
            }
        }
        Hairline()

        KeepAliveBlock()
        Hairline()

        Block {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("异常诊断", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                }
                if (vm.diagText.isNotBlank()) {
                    LineButton("复制") { clip.setText(AnnotatedString(vm.diagText)) }
                    Spacer(Modifier.width(8.dp))
                }
                LineButton("运行") { vm.runDiagnose(); showDiag = true }
            }
            if (showDiag && vm.diagText.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(vm.diagText, fontSize = 9.sp, color = Sub, lineHeight = 14.sp)
            }
        }
        Hairline()

        Block {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("账号", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (vm.loggedIn) "已登录" else "未登录",
                        fontSize = 10.sp, color = if (vm.loggedIn) Cold else Danger
                    )
                }
                LineButton("重新登录") { vm.logout() }
            }
        }
        Hairline()
        Spacer(Modifier.height(20.dp))
        Text(
            "数据来自 zhjw.smu.edu.cn，只在本机处理。",
            fontSize = 9.sp, color = Color(0xFFB5BAC0),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
        )
        Spacer(Modifier.height(28.dp))
    }
}

/** 会话维持（无通知、无常驻服务） */
@Composable
private fun KeepAliveBlock() {
    val ctx = LocalContext.current
    var ignoreBattery by remember { mutableStateOf(isIgnoringBattery(ctx)) }
    val alive = Prefs.sessionAlive
    val last = Prefs.lastAliveAt

    Column(Modifier.fillMaxWidth().background(Card).padding(horizontal = 14.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("会话维持", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                Spacer(Modifier.height(3.dp))
                Text(
                    "后台每 15 分钟检查一次登录状态。进程被杀不影响登录——会话在服务器上，定时任务会把进程唤醒接着续。",
                    fontSize = 10.sp, color = Sub, lineHeight = 15.sp
                )
            }
            Box(
                Modifier.size(7.dp).background(
                    if (alive) GreenLine else Color(0xFFC9CED4),
                    RoundedCornerShape(4.dp)
                )
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "最近检查：" + last.ifBlank { "尚未检查" } + "   ·   " + if (alive) "登录正常" else "登录已失效",
            fontSize = 10.sp, color = Sub
        )

        Spacer(Modifier.height(12.dp))
        Row {
            LineButton(if (ignoreBattery) "已忽略电池优化" else "关闭电池优化") {
                if (!ignoreBattery) requestIgnoreBattery(ctx) else openBatterySettings(ctx)
            }
            Spacer(Modifier.width(8.dp))
            LineButton("自启动设置") { openAppDetail(ctx) }
        }
    }
}

private fun isIgnoringBattery(ctx: Context): Boolean = try {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    pm.isIgnoringBatteryOptimizations(ctx.packageName)
} catch (e: Exception) {
    false
}

private fun requestIgnoreBattery(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + ctx.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        openBatterySettings(ctx)
    }
}

private fun openBatterySettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        openAppDetail(ctx)
    }
}

private fun openAppDetail(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + ctx.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        // 忽略
    }
}

@Composable
private fun Block(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Card).padding(horizontal = 14.dp, vertical = 16.dp)) {
        content()
    }
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.border(0.8.dp, Line, RoundedCornerShape(2.dp))
                .clickable { onChange(if (value <= min) max else value - 1) }
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) { Text("−", fontSize = 12.sp, color = Ink) }
        Text(
            String.format("%02d", value),
            fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Box(
            Modifier.border(0.8.dp, Line, RoundedCornerShape(2.dp))
                .clickable { onChange(if (value >= max) min else value + 1) }
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) { Text("+", fontSize = 12.sp, color = Ink) }
    }
}
