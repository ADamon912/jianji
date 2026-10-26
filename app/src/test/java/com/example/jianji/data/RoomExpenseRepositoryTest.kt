package com.example.jianji.data

import android.app.Application
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.jianji.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RoomExpenseRepositoryTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repo: RoomExpenseRepository
    private lateinit var epoch: String
    private val day = LocalDate.of(2026, 10, 10).toEpochDay()
    private val range = DateRange(LocalDate.of(2026, 10, 1).toEpochDay(), LocalDate.of(2026, 11, 1).toEpochDay())
    private val categories = builtInCategories()
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomExpenseRepository(db, Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC))
        repo.initialize(); epoch = repo.dataEpoch()
    }
    @After fun close() { db.close() }
    private fun newCommand(amount: Long = 1230, category: String = categories[0].id, note: String = "午餐", spentOn: Long = day) = ExpenseCommand(WriteContext(UUID.randomUUID().toString(), epoch), Operation.CREATE, UUID.randomUUID().toString(), input = ExpenseInput(amount, category, spentOn, note))
    private fun operation(original: ExpenseCommand, type: Operation, revision: Long, input: ExpenseInput? = original.input) = original.copy(context = WriteContext(UUID.randomUUID().toString(), epoch), operation = type, expectedRevision = revision, input = if (type == Operation.DELETE) null else input)
    private suspend fun summary(filter: ExpenseFilter = ExpenseFilter(range)) = withTimeout(10_000) { repo.observeSummary(filter).first() }
    private fun success(result: CommandResult) = (result as CommandResult.Success).commit

    @Test fun categoryLifecyclePreservesHistoricalAmountsAndStableIds() = runBlocking {
        val id = UUID.randomUUID().toString()
        val color = CategoryRules.colors.first()
        assertTrue(repo.saveCategory(id, "宠物", color) is CategoryResult.Success)
        assertTrue(repo.saveCategory(id, "宠物", color) is CategoryResult.Success)
        assertEquals(9, repo.observeCategories().first().size)
        val command = newCommand(category = id)
        success(repo.execute(command))
        val original = db.ledger().category(id)!!.domain()
        assertTrue(repo.saveCategory(id, "宠物护理", CategoryRules.colors[2], original) is CategoryResult.Success)
        assertEquals("宠物护理", repo.observeExpense(command.expenseId).first()!!.category.name)
        assertEquals(id, repo.observeCategoryTotals(range).first().single().category.id)
        val renamed = db.ledger().category(id)!!.domain()
        assertTrue(repo.deleteCategory(renamed) is CategoryResult.Success)
        assertTrue(db.ledger().category(id)!!.isArchived)
        assertEquals(1230L, summary().totalMinor)
        assertEquals(CommandResult.Failure(ErrorCode.CATEGORY_UNAVAILABLE), repo.execute(newCommand(category = id)))
        assertTrue(repo.restoreCategory(db.ledger().category(id)!!.domain()) is CategoryResult.Success)
        success(repo.execute(operation(command, Operation.DELETE, 1)))
        assertTrue(repo.deleteCategory(db.ledger().category(id)!!.domain()) is CategoryResult.Success)
        assertNull(db.ledger().category(id))
        assertEquals(epoch, repo.dataEpoch())
    }

    @Test fun categoryNamesAreUniqueAfterUnicodeNormalizationIncludingArchivedNames() = runBlocking {
        val id = UUID.randomUUID().toString()
        val color = CategoryRules.colors.first()
        assertTrue(repo.saveCategory(id, "  Ｃａｆｅ  ", color) is CategoryResult.Success)
        assertEquals("Cafe", db.ledger().category(id)!!.name)
        assertTrue(repo.saveCategory(UUID.randomUUID().toString(), "cafe", color) is CategoryResult.Failure)
        assertTrue(repo.saveCategory(UUID.randomUUID().toString(), "餐饮", color) is CategoryResult.Failure)
        assertTrue(repo.saveCategory(UUID.randomUUID().toString(), "\n", color) is CategoryResult.Failure)
        assertTrue(repo.saveCategory(UUID.randomUUID().toString(), "测试", 0) is CategoryResult.Failure)
        success(repo.execute(newCommand(category = id)))
        repo.deleteCategory(db.ledger().category(id)!!.domain())
        assertTrue(repo.saveCategory(UUID.randomUUID().toString(), "CAFE", color) is CategoryResult.Failure)
    }

    @Test fun staleCategoryEditorCannotOverwriteOrDeleteNewerChanges() = runBlocking {
        val original = db.ledger().category(categories[0].id)!!.domain()
        assertTrue(repo.saveCategory(original.id, "三餐", original.colorKey, original) is CategoryResult.Success)
        assertTrue(repo.saveCategory(original.id, "夜宵", original.colorKey, original) is CategoryResult.Failure)
        assertTrue(repo.deleteCategory(original) is CategoryResult.Failure)
        assertEquals("三餐", db.ledger().category(original.id)!!.name)
    }

    @Test fun categoryWriteRollsBackWhenSequenceUpdateFails() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_meta BEFORE UPDATE ON app_meta BEGIN SELECT RAISE(ABORT, 'forced failure'); END")
        val id = UUID.randomUUID().toString()
        assertTrue(repo.saveCategory(id, "测试", CategoryRules.colors.first()) is CategoryResult.Failure)
        assertNull(db.ledger().category(id))
        assertEquals(0L, db.ledger().meta()!!.changeSeq)
    }

    @Test fun deletingUnusedBuiltInCategoryDoesNotReseedItOnReopen() = runBlocking {
        val original = db.ledger().category(categories[0].id)!!.domain()
        assertTrue(repo.deleteCategory(original) is CategoryResult.Success)
        repo.initialize()
        assertNull(db.ledger().category(original.id))
        assertEquals(7, repo.observeCategories().first().size)
    }

    @Test fun bootstrapDoesNotResetExistingCategoriesOrEpoch() = runBlocking {
        db.openHelper.writableDatabase.execSQL("UPDATE categories SET isArchived=1 WHERE id=?", arrayOf(categories[0].id))
        repo.initialize()
        assertEquals(epoch, repo.dataEpoch()); assertEquals(8, repo.observeCategories().first().size)
        assertTrue(db.ledger().category(categories[0].id)!!.isArchived)
    }
    @Test fun createUpdateMoveMonthAndDeleteRecomputeTheSameLedger() = runBlocking {
        val command = newCommand()
        success(repo.execute(command)); assertEquals(1230L, summary().totalMinor)
        val previousMonth = LocalDate.of(2026, 9, 30).toEpochDay()
        val changed = command.input!!.copy(amountMinor = 2500, categoryId = categories[1].id, spentOn = previousMonth)
        success(repo.execute(operation(command, Operation.UPDATE, 1, changed)))
        assertEquals(0L, summary().totalMinor)
        val record = db.ledger().expense(command.expenseId)!!
        assertEquals(2L, record.revision); assertEquals(2500L, record.amountMinor)
        assertEquals(record.createdAt, record.updatedAt)
        val otherRange = DateRange(previousMonth, previousMonth + 1)
        assertEquals(categories[1].id, repo.observeCategoryTotals(otherRange).first().single().category.id)
        success(repo.execute(operation(command, Operation.DELETE, 2)))
        assertNull(db.ledger().expense(command.expenseId)); assertTrue(repo.observeCategoryTotals(otherRange).first().isEmpty())
    }
    @Test fun idempotentReplaysCannotDuplicateOrResurrectDeletedRecords() = runBlocking {
        val command = newCommand()
        assertFalse(success(repo.execute(command)).replayed)
        assertTrue(success(repo.execute(command)).replayed)
        assertEquals(1L, summary().count); assertEquals(1L, db.ledger().meta()!!.changeSeq)
        val delete = operation(command, Operation.DELETE, 1)
        success(repo.execute(delete))
        assertTrue(success(repo.execute(command)).replayed)
        assertTrue(success(repo.execute(delete)).replayed)
        assertNull(db.ledger().expense(command.expenseId)); assertEquals(2L, db.ledger().meta()!!.changeSeq)
    }
    @Test fun sameRequestIdWithDifferentPayloadIsRejected() = runBlocking {
        val command = newCommand(); success(repo.execute(command))
        assertEquals(CommandResult.Failure(ErrorCode.IDEMPOTENCY_CONFLICT), repo.execute(command.copy(input = command.input!!.copy(amountMinor = 3000))))
        assertEquals(1230L, summary().totalMinor)
    }
    @Test fun onlyOneConcurrentEditorCanCommitTheExpectedRevision() = runBlocking {
        val command = newCommand(); success(repo.execute(command))
        val results = coroutineScope {
            val a = async { repo.execute(operation(command, Operation.UPDATE, 1, command.input!!.copy(amountMinor = 2000))) }
            val b = async { repo.execute(operation(command, Operation.UPDATE, 1, command.input!!.copy(amountMinor = 3000))) }
            listOf(a.await(), b.await())
        }
        assertEquals(1, results.count { it is CommandResult.Success })
        assertEquals(1, results.count { it == CommandResult.Failure(ErrorCode.CONFLICT) })
        assertEquals(CommandResult.Failure(ErrorCode.CONFLICT), repo.execute(operation(command, Operation.DELETE, 1)))
    }
    @Test fun filtersMatchLiteralWildcardsAndApplyToEntireDailySummary() = runBlocking {
        repeat(50) { success(repo.execute(newCommand(100, note = if (it % 2 == 0) "优惠%_" else "普通"))) }
        success(repo.execute(newCommand(3000, categories[1].id, "优惠%_")))
        val filter = ExpenseFilter(range, setOf(categories[0].id), "%_")
        assertEquals(2500L, summary(filter).totalMinor); assertEquals(25L, summary(filter).count)
        assertEquals(8000L, summary().dailyTotals[day])
        assertEquals(0L, summary(ExpenseFilter(range, keyword = "' OR 1=1 --")).count)
        assertEquals(51L, summary().count)
    }
    @Test fun failedReceiptInsertRollsBackExpenseAndSequence() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_receipt BEFORE INSERT ON mutation_receipts BEGIN SELECT RAISE(ABORT, 'forced failure'); END")
        val command = newCommand()
        assertTrue(repo.execute(command) is CommandResult.Failure)
        assertNull(db.ledger().expense(command.expenseId)); assertNull(db.ledger().receipt(command.context.operationId))
        assertEquals(0L, db.ledger().meta()!!.changeSeq)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_receipt")
        success(repo.execute(command)); assertEquals(1230L, summary().totalMinor)
    }
    @Test fun foreignKeysPreventOrphanedExpenses() = runBlocking {
        val command = newCommand(); success(repo.execute(command))
        val exception = runCatching { db.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE id=?", arrayOf(command.input!!.categoryId)) }.exceptionOrNull()
        assertNotNull(exception); assertNotNull(db.ledger().expense(command.expenseId))
    }
    @Test fun archivedOriginalCategoryCanBeRetainedButNotUsedForNewRecords() = runBlocking {
        val command = newCommand(); success(repo.execute(command))
        db.openHelper.writableDatabase.execSQL("UPDATE categories SET isArchived=1 WHERE id=?", arrayOf(command.input!!.categoryId))
        assertEquals(CommandResult.Failure(ErrorCode.CATEGORY_UNAVAILABLE), repo.execute(newCommand()))
        success(repo.execute(operation(command, Operation.UPDATE, 1, command.input.copy(amountMinor = 500))))
        assertEquals(500L, summary().totalMinor)
    }
    @Test fun oldDatasetAndInvalidAmountCannotWrite() = runBlocking {
        val command = newCommand()
        assertEquals(CommandResult.Failure(ErrorCode.DATASET_CHANGED), repo.execute(command.copy(context = command.context.copy(dataEpoch = UUID.randomUUID().toString()))))
        assertEquals(CommandResult.Failure(ErrorCode.VALIDATION), repo.execute(newCommand(0)))
        assertEquals(CommandResult.Failure(ErrorCode.VALIDATION), repo.execute(newCommand(spentOn = LocalDate.of(2026, 10, 25).toEpochDay())))
        assertEquals(0L, summary().count)
    }
    @Test fun dataSurvivesDatabaseCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "persistence-${UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, LedgerDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            var storage = RoomExpenseRepository(disk)
            storage.initialize()
            val command = newCommand().copy(context = WriteContext(UUID.randomUUID().toString(), storage.dataEpoch()))
            success(storage.execute(command)); disk.close()
            disk = Room.databaseBuilder(context, LedgerDatabase::class.java, name).allowMainThreadQueries().build()
            storage = RoomExpenseRepository(disk); storage.initialize()
            assertEquals(1230L, storage.observeExpense(command.expenseId).first()!!.expense.amountMinor)
            assertTrue(success(storage.execute(command)).replayed)
        } finally { disk.close(); context.deleteDatabase(name) }
    }
}
