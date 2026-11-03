package cn.edu.smu.jwhelper.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import cn.edu.smu.jwhelper.WebHolder
import cn.edu.smu.jwhelper.core.Prefs
import cn.edu.smu.jwhelper.net.Api

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(vm: AppVm) {
    var progress by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            progress = 5
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            progress = 100
                            val c = CookieManager.getInstance().getCookie(Api.ROOT)
                            CookieManager.getInstance().flush()
                            vm.onWebPageLoaded(c)
                        }
                    }

                    // 把上次的 Cookie 塞回 WebView，会话还活着就不用再登录
                    val saved = Prefs.cookie
                    if (!saved.isNullOrBlank()) {
                        try {
                            CookieManager.getInstance().setCookie(Api.ROOT, saved)
                            CookieManager.getInstance().flush()
                        } catch (e: Exception) {
                            // 忽略
                        }
                    }
                    loadUrl(Api.ROOT)
                    WebHolder.view = this
                }
            }
        )

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            )
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = Card
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "在这里登录你的账号",
                    fontSize = 12.sp,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "异常反馈",
                    fontSize = 12.sp,
                    color = Cold,
                    modifier = Modifier.clickable { showHelp = true; vm.runDiagnose() }.padding(8.dp)
                )
            }
        }
    }

    if (showHelp) {
        val clip = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = {
                TextButton(onClick = {
                    clip.setText(AnnotatedString(vm.diagText))
                    showHelp = false
                }) { Text("复制全部") }
            },
            dismissButton = { TextButton(onClick = { showHelp = false }) { Text("关闭") } },
            title = { Text("诊断信息", fontSize = 14.sp) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text("1. 确认已经在这个网页里登录成功（能看到系统主界面）。", fontSize = 12.sp)
                    Text("2. 点「复制全部」把内容发我，比截图准。", fontSize = 12.sp, color = OfflineGray)
                    Spacer(Modifier.height(8.dp))
                    Text(vm.diagText.ifBlank { "正在诊断…" }, fontSize = 9.sp)
                }
            }
        )
    }
}
