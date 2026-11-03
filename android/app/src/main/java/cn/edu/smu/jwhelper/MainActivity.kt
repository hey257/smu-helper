package cn.edu.smu.jwhelper

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cn.edu.smu.jwhelper.ui.AppTheme
import cn.edu.smu.jwhelper.ui.AppVm
import cn.edu.smu.jwhelper.ui.HomeScreen
import cn.edu.smu.jwhelper.ui.PageBg
import cn.edu.smu.jwhelper.ui.SplashScreen
import cn.edu.smu.jwhelper.ui.WebLoginScreen

/** 让返回键在网页登录时能回退 */
object WebHolder {
    var view: WebView? = null
}

class MainActivity : ComponentActivity() {

    private val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = PageBg) {
                    var loginTapped by remember { mutableStateOf(false) }

                    // 登录成功后复位，下次失效时仍从“轻触登录”开始
                    LaunchedEffect(vm.loggedIn) {
                        if (vm.loggedIn) loginTapped = false
                    }

                    when {
                        // 还在确认登录态：短暂显示品牌页，自动进入，不需要点
                        !vm.sessionChecked -> SplashScreen()
                        // 有登录态：直接进主界面
                        vm.loggedIn -> HomeScreen(vm)
                        // 没登录态：只有这时才需要点一下去登录
                        loginTapped -> WebLoginScreen(vm)
                        else -> SplashScreen { loginTapped = true }
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        val w = WebHolder.view
        if (!vm.loggedIn && w != null && w.canGoBack()) {
            w.goBack()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
