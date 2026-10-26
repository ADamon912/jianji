package com.example.jianji.ui

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.jianji.AppContainer
import com.example.jianji.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

fun errorText(code: ErrorCode): String = when (code) {
    ErrorCode.VALIDATION -> "输入不符合要求，请检查金额、类型和日期"
    ErrorCode.NOT_FOUND -> "记录已不存在"
    ErrorCode.CONFLICT -> "记录已更新，请重新加载后核对"
    ErrorCode.CATEGORY_UNAVAILABLE -> "该类型已不可用，请重新选择"
    ErrorCode.IDEMPOTENCY_CONFLICT -> "提交状态异常，请重新打开页面"
    ErrorCode.DATASET_CHANGED -> "账本数据已更换，请重新打开页面"
    ErrorCode.STORAGE_FULL -> "存储空间不足，请清理空间后重试"
    ErrorCode.DATABASE_BUSY -> "数据正忙，请稍后重试"
    ErrorCode.STORAGE_FAILURE -> "数据保存失败，请重试"
    ErrorCode.DATA_INTEGRITY -> "数据无法安全处理，请检查账本"
}
private fun <T> Flow<T>.asLoadState(message: String): Flow<LoadState<T>> = map<T, LoadState<T>> { LoadState.Ready(it) }
    .onStart { emit(LoadState.Loading) }.catch { if (it is CancellationException) throw it; emit(LoadState.Failed(message)) }
private fun todayFlow() = flow { while (true) { emit(LocalDate.now()); delay(60_000) } }.distinctUntilChanged()

sealed interface LedgerListItem {
    data class Header(val day: Long) : LedgerListItem
    data class Row(val value: ExpenseRow) : LedgerListItem
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val container: AppContainer) : ViewModel() {
    val ready = MutableStateFlow<LoadState<Unit>>(LoadState.Loading)
    val settingsError = MutableStateFlow<String?>(null)
    private val preferenceRefresh = MutableStateFlow(0)
    val theme = preferenceRefresh.flatMapLatest {
        container.settings.theme.catch { error ->
            if (error is CancellationException) throw error
            settingsError.value = "主题读取失败，已使用系统主题，可点击重试"
            emit(ThemeMode.SYSTEM)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    init { initialize() }
    fun initialize() {
        ready.value = LoadState.Loading
        viewModelScope.launch {
            try { container.expenses.initialize(); ready.value = LoadState.Ready(Unit) }
            catch (error: Exception) { if (error is CancellationException) throw error; ready.value = LoadState.Failed("账本打开失败，已有数据不会被清除") }
        }
    }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch {
        try { container.settings.setTheme(mode); settingsError.value = null; preferenceRefresh.value++ }
        catch (error: Exception) { if (error is CancellationException) throw error; settingsError.value = "主题保存失败，请重试" }
    }
    fun retrySettings() { settingsError.value = null; preferenceRefresh.value++ }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ExpenseListViewModel(private val container: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    private val initialRange = if (saved.contains("start")) DateRange(requireNotNull(saved["start"]), requireNotNull(saved["end"])) else DateRange.month(LocalDate.now())
    val range = MutableStateFlow(initialRange)
    val selected: MutableStateFlow<Set<String>> = MutableStateFlow((saved.get<String>("ids") ?: "").split(',').filter { it.isNotBlank() }.toSet())
    val keyword = MutableStateFlow(saved.get<String>("keyword") ?: "")
    private var rangeMode = saved.get<String>("rangeMode") ?: if (saved.contains("start")) "custom" else "month"
    private val refresh = MutableStateFlow(0)
    val categories = refresh.flatMapLatest { container.expenses.observeCategories().asLoadState("类型加载失败，请重试") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
    val filter: StateFlow<ExpenseFilter> = combine(range, selected, keyword.debounce(300)) { dates, ids, text -> ExpenseFilter(dates, ids, text.trim()) }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, ExpenseFilter(initialRange, selected.value, keyword.value.trim()))
    val pages = filter.flatMapLatest(container.expenses::pages).map { page ->
        page.map<ExpenseRow, LedgerListItem> { LedgerListItem.Row(it) }.insertSeparators { before, after ->
            val next = (after as? LedgerListItem.Row)?.value?.expense?.spentOn
            val previous = (before as? LedgerListItem.Row)?.value?.expense?.spentOn
            if (next != null && next != previous) LedgerListItem.Header(next) else null
        }
    }.cachedIn(viewModelScope)
    val summary = combine(filter, refresh) { f, _ -> f }.flatMapLatest { container.expenses.observeSummary(it).asLoadState("汇总暂不可用，请重试") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
    init { viewModelScope.launch { todayFlow().collect { if (rangeMode != "custom") preset(rangeMode) } } }
    fun search(value: String) { keyword.value = value.take(200); saved["keyword"] = keyword.value }
    fun setCategories(ids: Set<String>) { selected.value = ids; saved["ids"] = ids.sorted().joinToString(",") }
    fun setRange(value: DateRange, mode: String = "custom") {
        range.value = value; rangeMode = mode
        saved["start"] = value.start; saved["end"] = value.endExclusive; saved["rangeMode"] = mode
    }
    fun preset(mode: String) { setRange(presetRange(mode), mode) }
    fun clear() { search(""); setCategories(emptySet()); preset("month") }
    fun retry() { refresh.value++ }
}

fun presetRange(mode: String): DateRange = when (mode) {
    "previous" -> DateRange.previousMonth(LocalDate.now())
    "year" -> DateRange.year(LocalDate.now())
    "all" -> DateRange.all(LocalDate.now())
    else -> DateRange.month(LocalDate.now())
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(private val container: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    val range = MutableStateFlow(if (saved.contains("start")) DateRange(requireNotNull(saved["start"]), requireNotNull(saved["end"])) else DateRange.month(LocalDate.now()))
    private var mode = saved.get<String>("mode") ?: "month"
    private val refresh = MutableStateFlow(0)
    val chartError = MutableStateFlow<String?>(null)
    val chartType = refresh.flatMapLatest { container.settings.chartType.catch {
        if (it is CancellationException) throw it
        chartError.value = "图表偏好读取失败，请重试"
        emit(ChartType.DONUT)
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartType.DONUT)
    val trend = combine(range, refresh) { dates, _ -> dates }.flatMapLatest { dates ->
        container.expenses.observeSummary(ExpenseFilter(dates)).map { TrendCalculator.calculate(dates, it.dailyTotals) }.asLoadState("趋势加载失败，请重试")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
    fun setChartType(value: ChartType) = viewModelScope.launch {
        try { container.settings.setChartType(value); chartError.value = null }
        catch (failure: Exception) { if (failure is CancellationException) throw failure; chartError.value = "图表偏好保存失败，请重试" }
    }
    val state = combine(range, refresh) { dates, _ -> dates }.flatMapLatest { dates ->
        container.expenses.observeCategoryTotals(dates).map { StatisticsCalculator.calculate(dates, it) }.asLoadState("统计加载失败，请重试")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
    init { viewModelScope.launch { todayFlow().collect { if (mode != "custom") setRange(presetRange(mode), mode) } } }
    fun setRange(value: DateRange, preset: String = "custom") { range.value = value; mode = preset; saved["start"] = value.start; saved["end"] = value.endExclusive; saved["mode"] = preset }
    fun retry() { chartError.value = null; refresh.value++ }
}

data class EditorState(
    val amount: String = "", val categoryId: String? = null, val date: String = LocalDate.now().toString(), val note: String = "",
    val loading: Boolean = false, val saving: Boolean = false, val dirty: Boolean = false,
    val errors: FieldErrors = FieldErrors(), val error: String? = null, val conflict: Boolean = false, val missing: Boolean = false,
    val completed: String? = null,
    val unresolved: Boolean = false,
    val loadFailed: Boolean = false,
)

class ExpenseEditorViewModel(private val container: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    val editing = saved.get<String>("id") != null
    private val expenseId = saved.get<String>("id") ?: saved.get<String>("draftId") ?: UUID.randomUUID().toString().also { saved["draftId"] = it }
    private var revision: Long? = saved["revision"]
    private var epoch: String? = saved["epoch"]
    private var initialized = saved.get<Boolean>("initialized") ?: !editing
    val state = MutableStateFlow(EditorState(
        amount = saved["amount"] ?: "", categoryId = saved["category"], date = saved["date"] ?: LocalDate.now().toString(), note = saved["note"] ?: "",
        dirty = saved["dirty"] ?: false, loading = !initialized, completed = saved["completed"],
    ))
    private val categoryRefresh = MutableStateFlow(0)
    @OptIn(ExperimentalCoroutinesApi::class)
    val categories = categoryRefresh.flatMapLatest { container.expenses.observeCategories().asLoadState("类型加载失败，请重试") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
    private var originalCategory: String? = saved["originalCategory"]
    private var pending: ExpenseCommand? = saved.get<Bundle>("pending")?.toCommand()
    private var recordJob: kotlinx.coroutines.Job? = null
    init { load() }

    fun load() {
        recordJob?.cancel()
        state.update { it.copy(loading = true, loadFailed = false) }
        recordJob = viewModelScope.launch {
            try {
                val currentEpoch = container.expenses.dataEpoch()
                if (epoch != null && epoch != currentEpoch) { state.update { it.copy(loading = false, error = errorText(ErrorCode.DATASET_CHANGED), missing = true) }; return@launch }
                epoch = currentEpoch; saved["epoch"] = currentEpoch
                state.update { it.copy(loading = editing && !initialized, loadFailed = false) }
                pending?.let { if (state.value.completed == null) submit(it) }
                if (editing) container.expenses.observeExpense(expenseId).collect { row ->
                    if (row == null) state.update { it.copy(loading = false, missing = true, error = "记录已不存在") }
                    else if (!initialized) {
                        val expense = row.expense
                        revision = expense.revision; saved["revision"] = revision
                        originalCategory = expense.categoryId; saved["originalCategory"] = originalCategory
                        state.value = EditorState(money(expense.amountMinor), expense.categoryId, LocalDate.ofEpochDay(expense.spentOn).toString(), expense.note)
                        initialized = true; saved["initialized"] = true; persistDraft()
                    } else if (row.expense.revision != revision && state.value.completed == null && !state.value.saving) {
                        state.update { it.copy(conflict = true, error = "该记录已有更新，请重新加载后核对") }
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                state.update { it.copy(loading = false, saving = false, loadFailed = true, error = "加载失败，请重试") }
            }
        }
    }
    fun canSelect(category: Category) = !category.archived || (editing && category.id == originalCategory)
    fun change(amount: String = state.value.amount, category: String? = state.value.categoryId, date: String = state.value.date, note: String = state.value.note) {
        if (state.value.saving || state.value.unresolved || state.value.completed != null) return
        pending = null; saved.remove<Bundle>("pending")
        state.update { it.copy(amount = amount.take(32), categoryId = category, date = date, note = note, dirty = true, errors = FieldErrors(), error = if (it.conflict) it.error else null) }
        persistDraft()
    }
    private fun persistDraft() {
        val value = state.value
        saved["amount"] = value.amount; saved["category"] = value.categoryId; saved["date"] = value.date; saved["note"] = value.note; saved["dirty"] = value.dirty
    }
    fun save() {
        if (state.value.saving || state.value.loading || state.value.loadFailed || state.value.missing || state.value.completed != null) return
        pending?.let { command ->
            state.update { it.copy(saving = true, error = null) }
            viewModelScope.launch { submit(command) }
            return
        }
        val value = state.value
        val date = runCatching { LocalDate.parse(value.date) }.getOrNull()
        if (date == null) { state.update { it.copy(errors = FieldErrors(date = "请选择有效日期")) }; return }
        val (input, errors) = ValidateExpenseUseCase()(value.amount, value.categoryId, date, value.note, LocalDate.now())
        if (input == null) { state.update { it.copy(errors = errors) }; return }
        val dataEpoch = epoch ?: return
        val command = pending ?: ExpenseCommand(WriteContext(UUID.randomUUID().toString(), dataEpoch), if (editing) Operation.UPDATE else Operation.CREATE, expenseId, revision, input)
        pending = command; saved["pending"] = command.toBundle()
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch { submit(command) }
    }
    private suspend fun submit(command: ExpenseCommand) {
        state.update { it.copy(saving = true) }
        try {
            when (val result = container.saveExpense(command)) {
                is CommandResult.Success -> {
                    saved["completed"] = result.commit.operationId
                    saved.remove<Bundle>("pending"); pending = null
                    state.update { it.copy(saving = false, dirty = false, completed = result.commit.operationId, unresolved = false) }
                }
                is CommandResult.Failure -> state.update { it.copy(saving = false, unresolved = false, error = errorText(result.code), conflict = result.code == ErrorCode.CONFLICT, missing = result.code == ErrorCode.NOT_FOUND || result.code == ErrorCode.DATASET_CHANGED) }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            state.update { it.copy(saving = false, unresolved = true, error = "提交状态尚未确认，请点击保存重试原请求") }
        }
    }
    fun reload() { if (state.value.saving || state.value.unresolved) return; initialized = false; saved["initialized"] = false; saved.remove<Bundle>("pending"); pending = null; state.value = EditorState(loading = true); load() }
    fun retryCategories() { categoryRefresh.value++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseDetailViewModel(private val container: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    private val id = requireNotNull(saved.get<String>("id"))
    private val refresh = MutableStateFlow(0)
    val expense = refresh.flatMapLatest { container.expenses.observeExpense(id).asLoadState("记录加载失败，请重试") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
    val deleting = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val completed = MutableStateFlow(saved.get<Boolean>("deleted") ?: false)
    private var pending = saved.get<Bundle>("pendingDelete")?.toCommand()
    init { if (pending != null && !completed.value) delete(requireNotNull(pending).expectedRevision!!) }
    fun retry() { refresh.value++ }
    fun delete(expectedRevision: Long) {
        if (deleting.value || completed.value) return
        deleting.value = true; error.value = null
        viewModelScope.launch {
            try {
                val command = pending ?: ExpenseCommand(WriteContext(UUID.randomUUID().toString(), container.expenses.dataEpoch()), Operation.DELETE, id, expectedRevision).also { pending = it; saved["pendingDelete"] = it.toBundle() }
                when (val result = container.saveExpense(command)) {
                    is CommandResult.Success -> { completed.value = true; saved["deleted"] = true; pending = null; saved.remove<Bundle>("pendingDelete") }
                    is CommandResult.Failure -> {
                        if (result.code == ErrorCode.NOT_FOUND) { completed.value = true; saved["deleted"] = true }
                        else error.value = errorText(result.code)
                        if (result.code == ErrorCode.CONFLICT) { pending = null; saved.remove<Bundle>("pendingDelete"); retry() }
                    }
                }
            } catch (failure: Exception) { if (failure is CancellationException) throw failure; error.value = "删除状态尚未确认，请重试" }
            finally { deleting.value = false }
        }
    }
}

private fun ExpenseCommand.toBundle() = Bundle().apply {
    putString("operationId", context.operationId); putString("epoch", context.dataEpoch); putString("operation", operation.name); putString("id", expenseId)
    expectedRevision?.let { putLong("revision", it) }
    input?.let { putLong("amount", it.amountMinor); putString("category", it.categoryId); putLong("day", it.spentOn); putString("note", it.note) }
}
private fun Bundle.toCommand() = ExpenseCommand(
    WriteContext(requireNotNull(getString("operationId")), requireNotNull(getString("epoch"))), Operation.valueOf(requireNotNull(getString("operation"))), requireNotNull(getString("id")),
    if (containsKey("revision")) getLong("revision") else null,
    if (containsKey("amount")) ExpenseInput(getLong("amount"), requireNotNull(getString("category")), getLong("day"), getString("note").orEmpty()) else null,
)
