package com.example.jianji.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jianji.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TransferState(
    val busy: Boolean = false,
    val preview: ImportPreview? = null,
    val message: String? = null,
    val error: Boolean = false,
)

class TransferViewModel(private val repository: RoomExpenseRepository, private val documents: LedgerDocuments) : ViewModel() {
    private val mutable = MutableStateFlow(TransferState())
    val state = mutable.asStateFlow()

    fun pickerFailed() { if (!mutable.value.busy) mutable.value = TransferState(message = "无法打开系统文件选择器，请确认设备提供文件管理应用", error = true) }
    fun discard() { if (!mutable.value.busy) mutable.value = TransferState(message = "已取消导入，账本未修改") }

    fun importFile(uri: Uri?) {
        if (uri == null) return
        work("读取失败，请确认文件可访问且为简记导出的 JSON。") {
            val preview = withContext(Dispatchers.IO) { repository.previewImport(LedgerTransfer.parse(documents.read(uri))) }
            TransferState(preview = preview)
        }
    }

    fun exportFile(uri: Uri?, csv: Boolean) {
        if (uri == null) return
        work("导出失败，目标可能留下不完整文件，请删除后重新导出。") {
            val count = withContext(Dispatchers.IO) {
                val snapshot = repository.exportLedger()
                val bytes = if (csv) LedgerTransfer.csv(snapshot) else LedgerTransfer.json(snapshot)
                documents.write(uri, bytes)
                snapshot.expenses.size
            }
            TransferState(message = "已导出 $count 笔账单（${if (csv) "CSV" else "JSON"}），文件已保存到所选位置")
        }
    }

    fun confirmImport() {
        val preview = mutable.value.preview ?: return
        work("导入失败，未写入任何记录。请重新选择文件。") {
            val added = withContext(Dispatchers.IO) { repository.commitImport(preview) }
            TransferState(message = "导入完成：新增 $added 笔账单，跳过 ${preview.skipped} 笔重复记录，新增 ${preview.newCategories} 个类型")
        }
    }

    private fun work(fallback: String, action: suspend () -> TransferState) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = null, error = false)
        viewModelScope.launch {
            try { mutable.value = action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val detail = when (failure) {
                    is IllegalArgumentException, is IllegalStateException -> failure.message
                    is android.database.sqlite.SQLiteFullException -> "存储空间不足，请清理后重试"
                    is java.nio.charset.CharacterCodingException -> "文件不是有效的 UTF-8 文本"
                    else -> null
                }
                mutable.value = TransferState(message = listOfNotNull(detail, fallback).joinToString("\n"), error = true)
            }
        }
    }
}
