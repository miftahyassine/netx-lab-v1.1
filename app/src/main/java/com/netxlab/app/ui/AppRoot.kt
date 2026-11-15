@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.netxlab.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.netxlab.app.data.AppViewModel
import com.netxlab.app.ui.screens.*
import kotlinx.coroutines.launch

private data class Dest(val route: String, val title: String, val icon: ImageVector)

private val DESTS = listOf(
    Dest("dashboard", "Dashboard", Icons.Default.Dashboard),
    Dest("wifi", "Wi-Fi Lab", Icons.Default.Wifi),
    Dest("tools/0", "Network Tools", Icons.Default.Build),
    Dest("packets", "Packet Lab", Icons.Default.Search),
    Dest("security/0", "Security Lab", Icons.Default.Security),
    Dest("terminal", "Terminal", Icons.Default.Terminal),
    Dest("more/0", "More Tools", Icons.Default.MoreHoriz),
    Dest("settings", "Settings", Icons.Default.Settings)
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dashboard"
    val title = when {
        route == "topology" -> "Network Map"
        else -> DESTS.firstOrNull { it.route.substringBefore("/") == route.substringBefore("/") }?.title ?: "NETX LAB"
    }
    fun go(r: String) {
        nav.navigate(r) { popUpTo("dashboard") { saveState = false }; launchSingleTop = true }
        scope.launch { drawer.close() }
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text("NETX LAB", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(20.dp))
                DESTS.forEach { d ->
                    NavigationDrawerItem(
                        label = { Text(d.title) }, icon = { Icon(d.icon, null) },
                        selected = route.substringBefore("/") == d.route.substringBefore("/"),
                        onClick = { go(d.route) }, modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Menu") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                NavHost(nav, startDestination = "dashboard") {
                    composable("dashboard") { DashboardScreen(vm, onNavigate = { go(it) }) }
                    composable("wifi") { WifiLabScreen(vm) }
                    composable("tools/{tab}", listOf(navArgument("tab") { type = NavType.IntType })) { ToolsScreen(vm, it.arguments?.getInt("tab") ?: 0) }
                    composable("packets") { PacketLabScreen(vm) }
                    composable("security/{tab}", listOf(navArgument("tab") { type = NavType.IntType })) { SecurityScreen(vm, it.arguments?.getInt("tab") ?: 0) }
                    composable("terminal") { TerminalScreen() }
                    composable("more/{tab}", listOf(navArgument("tab") { type = NavType.IntType })) { MoreScreen(vm, it.arguments?.getInt("tab") ?: 0) }
                    composable("settings") { SettingsScreen(vm) }
                    composable("topology") { TopologyScreen(vm) }
                }
            }
        }
    }
}
