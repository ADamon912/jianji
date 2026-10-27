package com.example.jianji.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.jianji.JianjiApplication
import com.example.jianji.domain.LoadState
import com.example.jianji.domain.ThemeMode

@Composable
fun JianjiApp() {
    val app = LocalContext.current.applicationContext as JianjiApplication
    val container = app.container
    val appModel: AppViewModel = viewModel(factory = viewModelFactory { initializer { AppViewModel(container) } })
    val ready by appModel.ready.collectAsStateWithLifecycle()
    val mode by appModel.theme.collectAsStateWithLifecycle()
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    JianjiTheme(dark) {
        when (val current = ready) {
            LoadState.Loading -> Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() } }
            is LoadState.Failed -> Surface(Modifier.fillMaxSize()) { Column(Modifier.systemBarsPadding().padding(24.dp)) { ErrorCard(current.message, appModel::initialize) } }
            is LoadState.Ready -> {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                val tabs = listOf("expenses" to "明细", "statistics" to "统计", "settings" to "设置")
                Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
                    if (route in tabs.map { it.first }) NavigationBar {
                        tabs.forEachIndexed { index, (path, label) -> NavigationBarItem(selected = route == path, onClick = {
                            nav.navigate(path) { popUpTo("expenses") { saveState = true }; launchSingleTop = true; restoreState = true }
                        }, icon = { Icon(when (index) { 0 -> Icons.AutoMirrored.Filled.List; 1 -> Icons.Default.DateRange; else -> Icons.Default.Settings }, contentDescription = null) }, label = { Text(label) }) }
                    }
                }) { padding ->
                    NavHost(nav, startDestination = "expenses", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                        composable("expenses") {
                            val model: ExpenseListViewModel = viewModel(factory = viewModelFactory { initializer { ExpenseListViewModel(container, createSavedStateHandle()) } })
                            ExpenseListScreen(model, { nav.navigate("create") }, { nav.navigate("detail/$it") })
                        }
                        composable("history/{start}/{end}/{ids}", arguments = listOf(navArgument("start") { type = NavType.LongType }, navArgument("end") { type = NavType.LongType }, navArgument("ids") { type = NavType.StringType })) {
                            val model: ExpenseListViewModel = viewModel(factory = viewModelFactory { initializer { ExpenseListViewModel(container, createSavedStateHandle()) } })
                            ExpenseListScreen(model, { nav.navigate("create") }, { nav.navigate("detail/$it") }, { nav.popBackStack() })
                        }
                        composable("statistics") {
                            val model: StatisticsViewModel = viewModel(factory = viewModelFactory { initializer { StatisticsViewModel(container, createSavedStateHandle()) } })
                            StatisticsScreen(model) { range, ids -> nav.navigate("history/${range.start}/${range.endExclusive}/${ids.sorted().joinToString(",")}") }
                        }
                        composable("settings") { SettingsScreen(appModel, mode, { nav.navigate("categories") }, { nav.navigate("transfer") }) }
                        composable("transfer") {
                            val model: TransferViewModel = viewModel(factory = viewModelFactory { initializer { TransferViewModel(container.expenses, container.documents) } })
                            TransferScreen(model) { nav.popBackStack() }
                        }
                        composable("categories") {
                            val model: CategoryViewModel = viewModel(factory = viewModelFactory { initializer { CategoryViewModel(container, createSavedStateHandle()) } })
                            CategoryScreen(model) { nav.popBackStack() }
                        }
                        composable("create") {
                            val model: ExpenseEditorViewModel = viewModel(factory = viewModelFactory { initializer { ExpenseEditorViewModel(container, createSavedStateHandle()) } })
                            ExpenseEditorScreen(model, { nav.popBackStack() }, { nav.navigate("categories") })
                        }
                        composable("edit/{id}") {
                            val model: ExpenseEditorViewModel = viewModel(factory = viewModelFactory { initializer { ExpenseEditorViewModel(container, createSavedStateHandle()) } })
                            ExpenseEditorScreen(model, { nav.popBackStack() }, { nav.navigate("categories") })
                        }
                        composable("detail/{id}") {
                            val model: ExpenseDetailViewModel = viewModel(factory = viewModelFactory { initializer { ExpenseDetailViewModel(container, createSavedStateHandle()) } })
                            ExpenseDetailScreen(model, { nav.popBackStack() }, { nav.navigate("edit/$it") })
                        }
                    }
                }
            }
        }
    }
}
