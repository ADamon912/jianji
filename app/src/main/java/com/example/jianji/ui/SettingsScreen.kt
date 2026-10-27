package com.example.jianji.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jianji.domain.ThemeMode
import com.example.jianji.BuildConfig

@Composable
fun SettingsScreen(model: AppViewModel, theme: ThemeMode, onManageCategories: () -> Unit, onTransfer: () -> Unit) {
    val error by model.settingsError.collectAsStateWithLifecycle()
    Scaffold(topBar = { PageTopBar("设置") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            error?.let { ErrorCard(it, model::retrySettings) }
            SectionIntro("自己的账本，自己的习惯", "调整分类与外观，让记录更顺手。")
            Card(onClick = onManageCategories, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("消费类型管理", style = MaterialTheme.typography.titleLarge)
                    Text("添加、编辑、删除类型，定制分类颜色。")
                    Text("管理类型 →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text("外观", style = MaterialTheme.typography.titleLarge)
            Card(Modifier.fillMaxWidth().selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = theme == mode, role = Role.RadioButton, onClick = { model.setTheme(mode) }).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = theme == mode, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(when (mode) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" })
                    }
                }
            }
            Text("账本与数据", style = MaterialTheme.typography.titleLarge)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("本地账本 · 人民币 CNY")
                    Text("消费记录保存在本机，无需登录或联网。")
                    HorizontalDivider()
                    Text("可导出 JSON 保存账单副本，或导出 CSV 查看表格；导入 JSON 时先预览，再合并到账本。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) { Text("账单导入与导出") }
                    Text("卸载应用或清除应用数据会删除本机账本，请提前导出并保存文件。", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("简记 ${BuildConfig.VERSION_NAME} · 认真记录，轻松生活", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
