package com.example.jianji.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.jianji.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LedgerImportTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repo: RoomExpenseRepository
    private val category = Category(UUID.randomUUID().toString(), "旅行", "custom", CategoryRules.colors.first(), 8, archived = true)
    private fun file() = LedgerFile(listOf(category), listOf(Expense(UUID.randomUUID().toString(), 1234, category.id, 20_000, "住宿", 100, 200, 2)))
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomExpenseRepository(db)
        repo.initialize()
    }
    @After fun close() { db.close() }

    @Test fun previewDoesNotWriteAndImportRoundTripSkipsRepeatedIds() = runBlocking {
        val file = file()
        val preview = repo.previewImport(file)
        assertEquals(1, preview.added)
        assertEquals(1, preview.newCategories)
        assertEquals(1234L, preview.totalMinor)
        assertEquals(0L, db.ledger().expenseCount())
        assertEquals(8, db.ledger().allCategories().size)
        assertEquals(1, repo.commitImport(preview))
        assertEquals(file.expenses, repo.exportLedger().expenses)
        assertTrue(db.ledger().category(category.id)!!.isArchived)
        val again = repo.previewImport(LedgerTransfer.parse(LedgerTransfer.json(repo.exportLedger())))
        assertEquals(1, again.skipped)
        assertEquals(0, repo.commitImport(again))
        assertEquals(1L, db.ledger().expenseCount())
    }

    @Test fun categoryNamesMapToExistingIdsWithoutChangingStyleOrArchiveState() = runBlocking {
        val original = builtInCategories().first()
        db.ledger().updateCategory(original.copy(isArchived = true))
        val file = file().let { it.copy(categories = listOf(category.copy(name = original.name))) }
        val preview = repo.previewImport(file)
        assertEquals(0, preview.newCategories)
        assertEquals(1, preview.matchedCategories)
        repo.commitImport(preview)
        assertEquals(original.id, db.ledger().allExpenses().single().categoryId)
        assertEquals(original.copy(isArchived = true), db.ledger().category(original.id))
    }

    @Test fun sameIdDifferentContentsBlocksWholeFile() = runBlocking {
        val file = file()
        repo.commitImport(repo.previewImport(file))
        val conflict = file.copy(expenses = listOf(file.expenses[0].copy(id = UUID.randomUUID().toString()), file.expenses[0].copy(amountMinor = 1)))
        try { repo.previewImport(conflict); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(file.expenses, repo.exportLedger().expenses)
    }

    @Test fun changesAfterPreviewRequireAnotherPreview() = runBlocking {
        val preview = repo.previewImport(file())
        repo.saveCategory(UUID.randomUUID().toString(), "新类型", CategoryRules.colors.first())
        try { repo.commitImport(preview); fail() } catch (_: IllegalStateException) { }
        assertEquals(0L, db.ledger().expenseCount())
        assertNull(db.ledger().category(category.id))
    }

    @Test fun writeFailureRollsBackCategoriesRecordsAndSequence() = runBlocking {
        val file = file().let { it.copy(expenses = it.expenses + it.expenses[0].copy(id = UUID.randomUUID().toString(), note = "fail")) }
        val preview = repo.previewImport(file)
        val meta = db.ledger().meta()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_import BEFORE INSERT ON expenses WHEN NEW.note='fail' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repo.commitImport(preview); fail() } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(0L, db.ledger().expenseCount())
        assertEquals(8, db.ledger().allCategories().size)
        assertEquals(meta, db.ledger().meta())
    }

    @Test fun equalContentWithDifferentIdsIsNotSilentlyDropped() = runBlocking {
        val file = file().let { it.copy(expenses = it.expenses + it.expenses[0].copy(id = UUID.randomUUID().toString())) }
        assertEquals(2, repo.commitImport(repo.previewImport(file)))
        assertEquals(2468L, repo.exportLedger().expenses.sumOf { it.amountMinor })
    }
}
