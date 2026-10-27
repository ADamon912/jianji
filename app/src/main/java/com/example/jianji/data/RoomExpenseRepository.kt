package com.example.jianji.data

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteFullException
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.jianji.application.ExpenseQueryService
import com.example.jianji.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class RoomExpenseRepository(private val database: LedgerDatabase, private val clock: Clock = Clock.systemUTC()) : ExpenseRepository, ExpenseQueryService {
    private val dao = database.ledger()
    private val writeLock = Mutex()
    override suspend fun initialize() = writeLock.withLock {
        database.withTransaction {
            if (dao.meta() == null) {
                dao.insertMeta(MetaEntity(dataEpoch = UUID.randomUUID().toString()))
                dao.insertCategories(builtInCategories())
            }
        }
    }
    override suspend fun dataEpoch(): String = requireNotNull(dao.meta()).dataEpoch
    override fun observeCategories() = dao.categories().map { it.map(CategoryEntity::domain) }
    override fun observeExpense(id: String) = dao.observeExpense(id).map { it?.domain() }

    suspend fun exportLedger(): LedgerFile = database.withTransaction {
        require(dao.expenseCount() <= LedgerTransfer.MAX_RECORDS) { "当前导出最多支持 10,000 笔账单" }
        val expenses = dao.allExpenses()
        require(expenses.all { it.currencyCode == "CNY" }) { "账本包含不支持的币种" }
        LedgerFile(dao.allCategories().sortedWith(compareBy({ it.sortOrder }, { it.id })).map { it.domain() }, expenses.map { it.domain() })
    }

    suspend fun previewImport(file: LedgerFile): ImportPreview = writeLock.withLock {
        database.withTransaction { planImport(file) }
    }

    private suspend fun planImport(file: LedgerFile): ImportPreview {
        LedgerTransfer.validate(file, LocalDate.now(clock.withZone(ZoneId.systemDefault())).toEpochDay())
        val meta = requireNotNull(dao.meta())
        val existingCount = dao.expenseCount()
        require(existingCount <= LedgerTransfer.MAX_RECORDS) { "当前导入最多支持 10,000 笔账单" }
        val existingExpenses = dao.allExpenses().associateBy { it.id }
        val existingCategories = dao.allCategories()
        val byId = existingCategories.associateBy { it.id }
        val byName = existingCategories.associateBy { it.normalizedName }
        val mapped = mutableMapOf<String, String>()
        val additions = mutableListOf<CategoryEntity>()
        file.categories.forEach { c ->
            val existing = byId[c.id] ?: byName[CategoryRules.key(c.name)]
            mapped[c.id] = existing?.id ?: c.id
            if (existing == null) additions += CategoryEntity(c.id, c.name, CategoryRules.key(c.name), c.iconKey,
                c.colorKey, existingCategories.size + additions.size, isBuiltIn = false, isArchived = c.archived)
        }
        require(existingCategories.size + additions.size <= 200) { "合并后超过 200 个类型，请先整理类型" }
        val records = mutableListOf<ExpenseEntity>()
        var skipped = 0
        file.expenses.forEachIndexed { index, e ->
            val category = mapped.getValue(e.categoryId)
            val existing = existingExpenses[e.id]
            if (existing != null) {
                require(existing.amountMinor == e.amountMinor && existing.categoryId == category &&
                    existing.spentOn == e.spentOn && existing.note == e.note && existing.currencyCode == "CNY") {
                    "第 ${index + 1} 笔账单与本机同 ID 记录内容不同，未导入任何数据。请核对文件或重新导出"
                }
                skipped++
            } else records += ExpenseEntity(e.id, e.amountMinor, category, e.spentOn, e.note, e.createdAt, e.updatedAt, e.revision)
        }
        require(existingCount + records.size <= LedgerTransfer.MAX_RECORDS) { "合并后超过 10,000 笔账单，无法导入" }
        return ImportPreview(file, meta.dataEpoch, meta.changeSeq, additions, records, skipped, file.categories.size - additions.size,
            (existingCategories + additions).associate { it.id to it.name })
    }

    suspend fun commitImport(preview: ImportPreview): Int = writeLock.withLock {
        database.withTransaction {
            val meta = requireNotNull(dao.meta())
            check(meta.dataEpoch == preview.epoch && meta.changeSeq == preview.sequence) { "账本已变化，请重新选择文件并预览" }
            // Revalidate in the write transaction, including the current device date.
            val plan = planImport(preview.file)
            dao.insertCategories(plan.categories)
            plan.expenses.forEach { dao.insertExpense(it) }
            if (plan.categories.isNotEmpty() || plan.expenses.isNotEmpty()) dao.setSequence(Math.addExact(meta.changeSeq, 1))
            plan.added
        }
    }

    // The same lock and transaction as expense writes prevent deletion/creation races.
    private suspend fun mutateCategory(action: suspend () -> CategoryResult): CategoryResult = try {
        writeLock.withLock { database.withTransaction {
            val result = action()
            if (result is CategoryResult.Success) dao.setSequence(Math.addExact(requireNotNull(dao.meta()).changeSeq, 1))
            result
        } }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: SQLiteConstraintException) { CategoryResult.Failure("名称已存在或类型仍被使用，请重新加载后重试") }
    catch (_: SQLiteFullException) { CategoryResult.Failure("存储空间不足，请清理后重试") }
    catch (_: android.database.sqlite.SQLiteException) { CategoryResult.Failure("类型保存失败，请重试") }

    suspend fun saveCategory(id: String, name: String, color: Long, original: Category? = null): CategoryResult = mutateCategory {
        if (!CategoryRules.valid(name) || color !in CategoryRules.colors || runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false).not())
            return@mutateCategory CategoryResult.Failure("名称须为 1～12 个字符，请选择有效颜色")
        val previous = dao.category(id)
        val displayName = CategoryRules.name(name)
        // A restored draft may repeat a successfully committed save.
        if (previous != null && previous.name == displayName && previous.colorKey == color && (original == null || previous.isArchived == original.archived))
            return@mutateCategory CategoryResult.Success("类型已保存")
        if (previous?.domain() != original) return@mutateCategory CategoryResult.Failure("类型已被修改或删除，请关闭后重新打开")
        val categories = dao.allCategories()
        if (categories.any { it.id != id && CategoryRules.key(it.name) == CategoryRules.key(name) })
            return@mutateCategory CategoryResult.Failure("名称已存在（包含已停用类型），请换一个名称")
        if (previous == null && categories.size >= 200) return@mutateCategory CategoryResult.Failure("最多保留 200 个类型，请先删除不用的类型")
        val value = previous?.copy(name = displayName, normalizedName = CategoryRules.key(name), colorKey = color)
            ?: CategoryEntity(id, displayName, CategoryRules.key(name), "custom", color, (categories.maxOfOrNull { it.sortOrder } ?: -1) + 1, isBuiltIn = false)
        if (previous == null) dao.insertCategories(listOf(value)) else dao.updateCategory(value)
        CategoryResult.Success("类型已保存")
    }

    suspend fun deleteCategory(original: Category): CategoryResult = mutateCategory {
        val current = dao.category(original.id) ?: return@mutateCategory CategoryResult.Success("类型已删除")
        if (current.domain() != original) return@mutateCategory CategoryResult.Failure("类型已变化，请重新确认")
        if (dao.categoryUsage(original.id) == 0L) {
            dao.deleteCategory(original.id)
            CategoryResult.Success("类型已删除")
        } else {
            dao.updateCategory(current.copy(isArchived = true))
            CategoryResult.Success("类型已停用，历史消费和统计已保留")
        }
    }

    suspend fun restoreCategory(original: Category): CategoryResult = mutateCategory {
        val current = dao.category(original.id) ?: return@mutateCategory CategoryResult.Failure("类型已不存在")
        if (current.domain() != original) return@mutateCategory CategoryResult.Failure("类型已变化，请重新确认")
        dao.updateCategory(current.copy(isArchived = false))
        CategoryResult.Success("类型已恢复")
    }

    override suspend fun execute(command: ExpenseCommand): CommandResult {
        if (!command.isValidShape()) return CommandResult.Failure(ErrorCode.VALIDATION)
        return try {
            writeLock.withLock { database.withTransaction { executeInTransaction(command) } }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SQLiteFullException) { CommandResult.Failure(ErrorCode.STORAGE_FULL) }
        catch (_: SQLiteDatabaseLockedException) { CommandResult.Failure(ErrorCode.DATABASE_BUSY) }
        catch (_: SQLiteConstraintException) { CommandResult.Failure(ErrorCode.CONFLICT) }
        catch (_: ArithmeticException) { CommandResult.Failure(ErrorCode.DATA_INTEGRITY) }
        catch (_: android.database.sqlite.SQLiteException) { CommandResult.Failure(ErrorCode.STORAGE_FAILURE) }
    }

    private suspend fun executeInTransaction(command: ExpenseCommand): CommandResult {
        val meta = requireNotNull(dao.meta())
        fun failure(code: ErrorCode) = CommandResult.Failure(code)
        if (meta.dataEpoch != command.context.dataEpoch) return failure(ErrorCode.DATASET_CHANGED)
        val digest = command.digest()
        dao.receipt(command.context.operationId)?.let {
            if (it.payloadDigest != digest || it.payloadEncodingVersion != 1) return failure(ErrorCode.IDEMPOTENCY_CONFLICT)
            return CommandResult.Success(ExpenseCommit(it.operationId, it.expenseId, it.resultRevision, replayed = true))
        }
        val previous = dao.expense(command.expenseId)
        if (command.operation == Operation.CREATE && previous != null) return failure(ErrorCode.CONFLICT)
        if (command.operation != Operation.CREATE) {
            if (previous == null) return failure(ErrorCode.NOT_FOUND)
            if (previous.revision != command.expectedRevision) return failure(ErrorCode.CONFLICT)
        }
        val input = command.input
        if (input != null) {
            val today = LocalDate.now(clock.withZone(ZoneId.systemDefault())).toEpochDay()
            if (input.spentOn !in 0..today) return failure(ErrorCode.VALIDATION)
            val category = dao.category(input.categoryId) ?: return failure(ErrorCode.CATEGORY_UNAVAILABLE)
            if (category.isArchived && (command.operation == Operation.CREATE || previous?.categoryId != category.id)) return failure(ErrorCode.CATEGORY_UNAVAILABLE)
        }
        val sequence = Math.addExact(meta.changeSeq, 1)
        val now = clock.millis()
        val revision = when (command.operation) {
            Operation.CREATE -> {
                val value = requireNotNull(input)
                dao.insertExpense(ExpenseEntity(command.expenseId, value.amountMinor, value.categoryId, value.spentOn, value.note, now, now))
                1L
            }
            Operation.UPDATE -> {
                val value = requireNotNull(input)
                val next = Math.addExact(requireNotNull(previous).revision, 1)
                check(dao.updateExpense(command.expenseId, previous.revision, value.amountMinor, value.categoryId, value.spentOn, value.note, now) == 1)
                next
            }
            Operation.DELETE -> {
                check(dao.deleteExpense(command.expenseId, requireNotNull(previous).revision) == 1)
                null
            }
        }
        dao.insertReceipt(ReceiptEntity(command.context.operationId, meta.dataEpoch, command.operation.name, command.expenseId, digest, resultRevision = revision, committedAt = now))
        dao.setSequence(sequence)
        return CommandResult.Success(ExpenseCommit(command.context.operationId, command.expenseId, revision))
    }

    override fun pages(filter: ExpenseFilter) = Pager(PagingConfig(pageSize = 30, initialLoadSize = 30, prefetchDistance = 10, enablePlaceholders = false)) {
        dao.paging(query(filter, daily = false))
    }.flow.map { data -> data.map(JoinedExpense::domain) }
    override fun observeSummary(filter: ExpenseFilter) = dao.dailyTotals(query(filter, daily = true)).map { days ->
        ExpenseSummary(days.fold(0L) { sum, day -> Math.addExact(sum, day.totalMinor) }, days.fold(0L) { sum, day -> Math.addExact(sum, day.count) }, days.associate { it.day to it.totalMinor })
    }
    override fun observeCategoryTotals(range: DateRange) = dao.totals(range.start, range.endExclusive).map { it.map(AggregateRow::domain) }

    private fun query(filter: ExpenseFilter, daily: Boolean): SimpleSQLiteQuery {
        val select = if (daily) "SELECT e.spentOn AS day,SUM(e.amountMinor) AS totalMinor,COUNT(*) AS count FROM expenses e" else
            "SELECT e.*,c.name AS categoryName,c.iconKey,c.colorKey,c.sortOrder,c.isArchived FROM expenses e JOIN categories c ON e.categoryId=c.id"
        val args = mutableListOf<Any>(filter.range.start, filter.range.endExclusive, filter.keyword.trim(), filter.keyword.trim())
        val where = StringBuilder(" WHERE e.currencyCode='CNY' AND e.spentOn>=? AND e.spentOn<? AND (?='' OR instr(e.note,?)>0)")
        if (filter.categoryIds.isNotEmpty()) {
            require(filter.categoryIds.size <= 200)
            where.append(" AND e.categoryId IN (${filter.categoryIds.joinToString(",") { "?" }})")
            args.addAll(filter.categoryIds.sorted())
        }
        val order = if (daily) " GROUP BY e.spentOn ORDER BY e.spentOn DESC" else " ORDER BY e.spentOn DESC,e.createdAt DESC,e.id DESC"
        return SimpleSQLiteQuery(select + where + order, args.toTypedArray())
    }
}

private fun ExpenseCommand.isValidShape(): Boolean {
    fun uuid(value: String) = runCatching { UUID.fromString(value).toString().equals(value, ignoreCase = true) }.getOrDefault(false)
    if (!uuid(expenseId) || !uuid(context.operationId) || !uuid(context.dataEpoch)) return false
    if (operation == Operation.CREATE && expectedRevision != null) return false
    if (operation != Operation.CREATE && (expectedRevision == null || expectedRevision < 1)) return false
    if (operation == Operation.DELETE) return input == null
    return input?.let { it.amountMinor in 1..MoneyParser.MAX_MINOR && uuid(it.categoryId) && it.note == it.note.trim() && validNote(it.note) } ?: false
}

fun builtInCategories(): List<CategoryEntity> {
    val names = listOf("餐饮", "交通", "购物", "居住", "娱乐", "医疗", "教育", "其他")
    val keys = listOf("food", "transport", "shopping", "home", "fun", "health", "education", "other")
    val colors = listOf(0xFFE08B36, 0xFF4285B4, 0xFFAE6BA9, 0xFF3B8F77, 0xFFDA6D76, 0xFF478D92, 0xFF7973B2, 0xFF788489)
    return names.mapIndexed { index, name -> CategoryEntity(UUID.nameUUIDFromBytes("jianji:${keys[index]}".toByteArray(Charsets.UTF_8)).toString(), name, name, keys[index], colors[index], index) }
}
