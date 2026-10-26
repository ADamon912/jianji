package com.example.jianji.data

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.jianji.domain.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories", indices = [Index(value = ["normalizedName"], unique = true)])
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val normalizedName: String, val iconKey: String, val colorKey: Long, val sortOrder: Int, val isBuiltIn: Boolean = true, val isArchived: Boolean = false) {
    fun domain() = Category(id, name, iconKey, colorKey, sortOrder, isArchived)
}
@Entity(tableName = "expenses", foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["spentOn", "createdAt", "id"]), Index(value = ["categoryId", "spentOn"])])
data class ExpenseEntity(@PrimaryKey val id: String, val amountMinor: Long, val categoryId: String, val spentOn: Long, val note: String, val createdAt: Long, val updatedAt: Long, val revision: Long = 1, val currencyCode: String = "CNY") {
    fun domain() = Expense(id, amountMinor, categoryId, spentOn, note, createdAt, updatedAt, revision)
}
@Entity(tableName = "app_meta")
data class MetaEntity(@PrimaryKey val singletonId: Int = 1, val dataEpoch: String, val changeSeq: Long = 0)
@Entity(tableName = "mutation_receipts")
data class ReceiptEntity(@PrimaryKey val operationId: String, val dataEpoch: String, val operationType: String, val expenseId: String, val payloadDigest: String, val payloadEncodingVersion: Int = 1, val resultRevision: Long?, val committedAt: Long)

data class JoinedExpense(
    @Embedded val expense: ExpenseEntity,
    val categoryName: String, val iconKey: String, val colorKey: Long, val sortOrder: Int, val isArchived: Boolean,
) {
    fun domain() = ExpenseRow(expense.domain(), Category(expense.categoryId, categoryName, iconKey, colorKey, sortOrder, isArchived))
}
data class AggregateRow(val categoryId: String, val name: String, val iconKey: String, val colorKey: Long, val sortOrder: Int, val isArchived: Boolean, val totalMinor: Long, val count: Long) {
    fun domain() = CategoryTotal(Category(categoryId, name, iconKey, colorKey, sortOrder, isArchived), totalMinor, count)
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM app_meta WHERE singletonId=1") suspend fun meta(): MetaEntity?
    @Insert suspend fun insertMeta(meta: MetaEntity)
    @Query("UPDATE app_meta SET changeSeq=:value WHERE singletonId=1") suspend fun setSequence(value: Long)
    @Insert suspend fun insertCategories(categories: List<CategoryEntity>)
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories WHERE id=:id") suspend fun category(id: String): CategoryEntity?
    @Query("SELECT * FROM categories") suspend fun allCategories(): List<CategoryEntity>
    @Update suspend fun updateCategory(category: CategoryEntity): Int
    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId=:id") suspend fun categoryUsage(id: String): Long
    @Query("DELETE FROM categories WHERE id=:id") suspend fun deleteCategory(id: String): Int
    @Query("SELECT * FROM expenses WHERE id=:id") suspend fun expense(id: String): ExpenseEntity?
    @Query("SELECT e.*, c.name AS categoryName, c.iconKey, c.colorKey, c.sortOrder, c.isArchived FROM expenses e JOIN categories c ON e.categoryId=c.id WHERE e.id=:id")
    fun observeExpense(id: String): Flow<JoinedExpense?>
    @Insert suspend fun insertExpense(expense: ExpenseEntity)
    @Query("UPDATE expenses SET amountMinor=:amount, categoryId=:category, spentOn=:day, note=:note, updatedAt=:now, revision=revision+1 WHERE id=:id AND revision=:expected")
    suspend fun updateExpense(id: String, expected: Long, amount: Long, category: String, day: Long, note: String, now: Long): Int
    @Query("DELETE FROM expenses WHERE id=:id AND revision=:expected") suspend fun deleteExpense(id: String, expected: Long): Int
    @Query("SELECT * FROM mutation_receipts WHERE operationId=:id") suspend fun receipt(id: String): ReceiptEntity?
    @Insert suspend fun insertReceipt(receipt: ReceiptEntity)
    @RawQuery(observedEntities = [ExpenseEntity::class, CategoryEntity::class]) fun paging(query: SupportSQLiteQuery): PagingSource<Int, JoinedExpense>
    @RawQuery(observedEntities = [ExpenseEntity::class]) fun dailyTotals(query: SupportSQLiteQuery): Flow<List<DailyTotal>>
    @Query("SELECT c.id AS categoryId, c.name, c.iconKey, c.colorKey, c.sortOrder, c.isArchived, SUM(e.amountMinor) AS totalMinor, COUNT(*) AS count FROM expenses e JOIN categories c ON e.categoryId=c.id WHERE e.currencyCode='CNY' AND e.spentOn>=:start AND e.spentOn<:end GROUP BY c.id,c.name,c.iconKey,c.colorKey,c.sortOrder,c.isArchived ORDER BY totalMinor DESC,c.sortOrder,c.id")
    fun totals(start: Long, end: Long): Flow<List<AggregateRow>>
}

@Database(entities = [CategoryEntity::class, ExpenseEntity::class, MetaEntity::class, ReceiptEntity::class], version = 1, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledger(): LedgerDao
}
