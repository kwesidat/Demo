package com.demo.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.demo.tv.ui.Bg
import com.demo.tv.ui.HomeScreen
import com.demo.tv.ui.LoginScreen
import com.demo.tv.ui.PlayerScreen
import com.demo.tv.ui.SeriesScreen
import com.demo.tv.ui.TvTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvTheme { Root(vm) } }
    }
}

@Composable
private fun Root(vm: AppViewModel) {
    val request = vm.playing
    BackHandler(enabled = request == null && vm.screen == Screen.SeriesDetail) { vm.closeSeries() }

    Box(Modifier.fillMaxSize().background(Bg)) {
        when (vm.screen) {
            Screen.Login -> LoginScreen(vm.savedLogin, vm.loginBusy, vm.loginError, vm::login)
            Screen.Home -> HomeScreen(vm)
            Screen.SeriesDetail -> SeriesScreen(vm)
        }
        if (request != null) {
            PlayerScreen(vm, request)
        }
    }
}
