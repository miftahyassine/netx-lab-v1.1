package com.netxlab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.netxlab.app.data.AppViewModel
import com.netxlab.app.ui.AppRoot
import com.netxlab.app.ui.LocalShowInfo
import com.netxlab.app.ui.theme.NetxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Root() }
    }
}

@Composable
private fun Root() {
    val vm: AppViewModel = hiltViewModel()
    val s by vm.settings.collectAsState()
    NetxTheme(s.theme) {
        CompositionLocalProvider(LocalShowInfo provides s.infoIcons) { AppRoot(vm) }
    }
}
