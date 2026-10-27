package com.example.jianji.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jianji.domain.money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun TransferScreen(model: TransferViewModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), model::importFile)
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { model.exportFile(it, csv = false) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { model.exportFile(it, csv = true) }
    fun launch(action: () -> Unit) { try { action() } catch (_: android.content.ActivityNotFoundException) { model.pickerFailed() } }
    fun filename(extension: String) = "简记账单-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.$extension"
    TransferContent(state, onBack, { launch { import.launch(arrayOf("application/json", "text/*", "application/octet-stream")) } },
        { launch { json.launch(filename("json")) } }, { launch { csv.launch(filename("csv")) } }, model::confirmImport, model::discard)
}

@Composable
internal fun TransferContent(state: TransferState, onBack: () -> Unit, onImport: () -> Unit,
    onJson: () -> Unit, onCsv: () -> Unit, onConfirm: () -> Unit, onDiscard: () -> Unit) {
    BackHandler(enabled = state.busy) { /* Keep the transaction attached to this screen. */ }
    Scaffold(topBar = { PageTopBar("账单导入与导出", onBack, enabled = !state.busy) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).testTag("transferScroll").padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionIntro("为账本留一份副本", "导出全部历史账单；导入前先核对，确认后合并。")
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在处理，请稍候…")
            }
            state.message?.let { message ->
                if (state.error) ErrorCard(message) else Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(message, Modifier.padding(20.dp))
                }
            }
            val preview = state.preview
            if (preview != null) {
                SpendSummaryCard("待导入金额", "¥ ${money(preview.totalMinor)}", "新增 ${preview.added} 笔", "重复跳过 ${preview.skipped} 笔")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("导入预览", style = MaterialTheme.typography.titleLarge)
                        Text("新增 ${preview.newCategories} 个类型，匹配 ${preview.matchedCategories} 个已有类型。已有类型的名称、颜色与停用状态保持不变。")
                        Text("只按账单 ID 判断重复，不按金额和日期去重。已删除的账单若仍在文件中，会重新导入。")
                        if (preview.added > 0) {
                            HorizontalDivider()
                            Text("新增账单示例（最多 5 笔）", style = MaterialTheme.typography.labelLarge)
                            preview.expenses.take(5).forEach { e ->
                                Text("${LocalDate.ofEpochDay(e.spentOn)} · ${preview.categoryNames.getValue(e.categoryId)} · ¥ ${money(e.amountMinor)}")
                            }
                        }
                        Text("确认后一次性写入；返回或取消不会修改账本。")
                        Button(onClick = onConfirm, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("确认合并导入") }
                        OutlinedButton(onClick = onDiscard, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("取消本次导入") }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("JSON 账单文件", style = MaterialTheme.typography.titleLarge)
                        Text("包含全部账单和消费类型，可导入简记。保留记录 ID、金额、日期和备注，不包含主题设置。")
                        Button(onClick = onJson, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("导出 JSON") }
                        OutlinedButton(onClick = onImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("选择 JSON 导入") }
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("CSV 表格", style = MaterialTheme.typography.titleLarge)
                        Text("用 Excel 等工具查看全部账单，金额单位为元。CSV 仅供查看，暂不支持导入。")
                        OutlinedButton(onClick = onCsv, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("导出 CSV") }
                    }
                }
            }
            Text("文件为明文，请妥善保存。单个文件最多 10 MiB、10,000 笔账单、200 个类型。文件选择取消后不执行操作；应用被系统关闭后，请重新选择文件。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
