package cn.edu.smu.jwhelper.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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

/**
 * 登录页里账号密码的回收口。只写进 Keystore 加密后的 Prefs，不外传。
 * 用户在登录页手输一次，下次打开就不用再输。
 */
private class LoginBridge {
    @JavascriptInterface
    fun save(account: String?, pwd: String?) {
        val a = account?.trim().orEmpty()
        val p = pwd.orEmpty()
        if (a.isBlank() || p.isBlank()) return
        if (a.length > 64 || p.length > 128) return
        if (Prefs.username != a) {
            Prefs.username = a
            Prefs.password = p
            return
        }
        if (Prefs.password != p) Prefs.password = p
    }
}

/**
 * 登录页的自动填充脚本。
 *
 * 学校每次提交登录都强制弹 Tianai 人机验证（滑块/拼图/点选），而且 captchaToken
 * 参与密码的 AES 加密，没有它连请求都构造不出来——所以"完全不碰"的自动登录做不到。
 * 这里只做两件事：把账号密码填好、点一下登录，让用户拖一次验证码就能进去。
 *
 * 顺带挂一个轮询，用户手输的账号密码会被记下来（加密存本机），下次自动填。
 */
private fun autoFillScript(): String {
    val acc = org.json.JSONObject.quote(Prefs.username)
    val pwd = org.json.JSONObject.quote(Prefs.password)
    val allowAuto = Prefs.autoLogin &&
        Prefs.username.isNotBlank() && Prefs.password.isNotBlank()
    return """
(function(){
  var a=document.getElementById('L_account'), p=document.getElementById('L_pass');
  if(!a||!p) return 'nologin';

  if(!window.__smuWatch){
    window.__smuWatch=1;
    var la='', lp='';
    setInterval(function(){
      var x=document.getElementById('L_account'), y=document.getElementById('L_pass');
      if(!x||!y||!x.value||!y.value) return;
      if(x.value===la && y.value===lp) return;
      la=x.value; lp=y.value;
      try{ SMUBridge.save(x.value,y.value); }catch(e){}
    },1500);
  }

  if(!${if (allowAuto) "true" else "false"}) return 'off';
  if(a.value||p.value) return 'occupied';

  a.value=$acc;
  p.value=$pwd;
  ['input','change','keyup'].forEach(function(t){
    a.dispatchEvent(new Event(t,{bubbles:true}));
    p.dispatchEvent(new Event(t,{bubbles:true}));
  });

  setTimeout(function(){
    if(window.__smuClicked) return;
    var b=document.querySelector('button[lay-filter="login"]');
    if(b){ window.__smuClicked=1; b.click(); }
  },600);

  return 'filled';
})()
""".trimIndent()
}

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
                            // 登录页：先记下用户输入的账号密码，再尝试自动填充
                            view?.evaluateJavascript(autoFillScript()) { }
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
                    addJavascriptInterface(LoginBridge(), "SMUBridge")
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
