package com.example.jianji.ui

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jianji.AppContainer
import com.example.jianji.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class CategoryDraft(val id: String, val name: String, val color: Long, val original: Category?)

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryViewModel(private val container: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    val categories = refresh.flatMapLatest {
        container.expenses.observeCategories().map<List<Category>, LoadState<List<Category>>> { LoadState.Ready(it) }
            .onStart { emit(LoadState.Loading) }.catch { if (it is CancellationException) throw it; emit(LoadState.Failed("类型读取失败，请重试")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
    val saving = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)
    val draft = MutableStateFlow(saved.get<Bundle>("categoryDraft")?.let { bundle ->
        val original = bundle.getBundle("original")?.let { Category(requireNotNull(it.getString("id")), requireNotNull(it.getString("name")), requireNotNull(it.getString("icon")), it.getLong("color"), it.getInt("sort"), it.getBoolean("archived")) }
        CategoryDraft(requireNotNull(bundle.getString("id")), bundle.getString("name").orEmpty(), bundle.getLong("color"), original)
    })
    private fun persist() {
        saved["categoryDraft"] = draft.value?.let { value -> Bundle().apply {
            putString("id", value.id); putString("name", value.name); putLong("color", value.color)
            value.original?.let { original -> putBundle("original", Bundle().apply {
                putString("id", original.id); putString("name", original.name); putString("icon", original.iconKey)
                putLong("color", original.colorKey); putInt("sort", original.sortOrder); putBoolean("archived", original.archived)
            }) }
        } }
    }
    fun open(category: Category? = null) {
        if (saving.value) return
        error.value = null
        draft.value = CategoryDraft(category?.id ?: UUID.randomUUID().toString(), category?.name.orEmpty(), category?.colorKey ?: CategoryRules.colors.first(), category)
        persist()
    }
    fun edit(name: String = requireNotNull(draft.value).name, color: Long = requireNotNull(draft.value).color) {
        if (saving.value) return
        draft.value = draft.value?.copy(name = name, color = color); error.value = null; persist()
    }
    fun close() { if (!saving.value) { draft.value = null; error.value = null; persist() } }
    fun retry() { refresh.value++ }
    fun clearNotice() { notice.value = null }
    private fun write(closeDraft: Boolean = false, action: suspend () -> CategoryResult) {
        if (saving.value) return
        saving.value = true; error.value = null; notice.value = null
        viewModelScope.launch {
            try {
                when (val result = action()) {
                    is CategoryResult.Success -> { notice.value = result.message; if (closeDraft) { draft.value = null; persist() } }
                    is CategoryResult.Failure -> error.value = result.message
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error.value = "操作未确认，请重试并检查类型列表"
            } finally { saving.value = false }
        }
    }
    fun save() {
        val value = draft.value ?: return
        write(closeDraft = true) { container.expenses.saveCategory(value.id, value.name, value.color, value.original) }
    }
    fun delete(category: Category) = write { container.expenses.deleteCategory(category) }
    fun restore(category: Category) = write { container.expenses.restoreCategory(category) }
}
