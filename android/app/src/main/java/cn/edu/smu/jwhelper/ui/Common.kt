package cn.edu.smu.jwhelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- 极简 / 冷峻：黑白灰 + 一根冷调蓝灰 ----
val Ink = Color(0xFF111417)          // 主文字
val Sub = Color(0xFF7A828B)          // 次文字
val Line = Color(0xFFE4E6E9)         // 分隔线
val Bg = Color(0xFFFCFCFD)           // 页面底
val Card = Color(0xFFFFFFFF)         // 卡片
val Fill = Color(0xFFF3F4F6)         // 弱底
val Accent = Color(0xFF2B333B)       // 近黑强调
val Cold = Color(0xFF45616F)         // 冷调蓝灰
val Danger = Color(0xFF9B2C2C)       // 只给考试
val GreenLine = Color(0xFF2F6B4F)    // 线上课

// 兼容旧引用
val Teal = Accent
val TealLight = Fill
val WarnRed = Danger
val WarnRedBg = Color(0xFFF7F1F1)
val OfflineGray = Sub
val OfflineBg = Fill
val PageBg = Bg

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            primaryContainer = Fill,
            onPrimaryContainer = Ink,
            background = Bg,
            surface = Card,
            onSurface = Ink,
            outline = Line,
            error = Danger
        ),
        content = content
    )
}

/** 方角细边框标签 */
@Composable
fun Tag(
    text: String,
    fg: Color = Sub,
    border: Color = Line,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .border(0.8.dp, border, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(text, color = fg, fontSize = 9.sp, fontWeight = FontWeight.Medium, lineHeight = 11.sp)
    }
}

/** 实心标签（考试专用） */
@Composable
fun SolidTag(text: String, bg: Color = Danger, fg: Color = Color.White, modifier: Modifier = Modifier) {
    Box(modifier.background(bg, RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
        Text(text, color = fg, fontSize = 9.sp, fontWeight = FontWeight.Medium, lineHeight = 11.sp)
    }
}

@Composable
fun Hairline(color: Color = Line, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.8.dp).background(color))
}

@Composable
fun EmptyView(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(56.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Sub, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingView(text: String = "读取中") {
    Box(Modifier.fillMaxWidth().padding(56.dp), contentAlignment = Alignment.Center) {
        Text("$text…", color = Sub, fontSize = 13.sp)
    }
}

/** 小区块标题 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = Sub,
        fontSize = 10.sp,
        letterSpacing = 1.1.sp,
        fontWeight = FontWeight.Medium
    )
}

/** 黑色主按钮 */
@Composable
fun InkButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (enabled) Accent else Fill, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            color = if (enabled) Color.White else Sub,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 描边次按钮 */
@Composable
fun LineButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .border(0.8.dp, if (enabled) Accent else Line, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = if (enabled) Ink else Sub, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
