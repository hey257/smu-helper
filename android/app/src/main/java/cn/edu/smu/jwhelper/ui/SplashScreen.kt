package cn.edu.smu.jwhelper.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 两种用法：
 *  - onEnter == null：启动时短暂显示，自动进入（不需要点）
 *  - onEnter != null：需要登录时显示，轻触进入登录页
 */
@Composable
fun SplashScreen(onEnter: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val clickableMod = if (onEnter != null) {
        Modifier.clickable(interactionSource = interaction, indication = null) { onEnter() }
    } else {
        Modifier
    }
    val transition = rememberInfiniteTransition(label = "hint")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Card)
            .then(clickableMod)
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 极简标记：一个方框 + 两条横线，像课表格
            Column(
                Modifier
                    .size(52.dp)
                    .background(Ink, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.width(24.dp).height(1.5.dp).background(Color.White))
                Spacer(Modifier.height(5.dp))
                Box(Modifier.width(24.dp).height(1.5.dp).background(Color.White.copy(alpha = 0.55f)))
                Spacer(Modifier.height(5.dp))
                Box(Modifier.width(24.dp).height(1.5.dp).background(Color.White.copy(alpha = 0.3f)))
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "南医大助手",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "课表 · 考试 · 评课",
                fontSize = 10.sp,
                color = Sub,
                letterSpacing = 2.sp
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (onEnter != null) {
                Text(
                    "轻触屏幕登录",
                    fontSize = 10.sp,
                    color = Sub.copy(alpha = alpha),
                    letterSpacing = 1.5.sp
                )
            } else {
                // 自动进入时给一条极细的进度线，不要求点击
                Box(Modifier.width(28.dp).height(1.5.dp).background(Color(0xFFE4E6E9)))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "数据来自学校教务系统，只在本机处理",
                fontSize = 9.sp,
                color = Color(0xFFBFC5CB),
                textAlign = TextAlign.Center
            )
        }
    }
}
