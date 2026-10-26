package com.example.jianji.domain

import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    suspend fun initialize()
    suspend fun dataEpoch(): String
    fun observeCategories(): Flow<List<Category>>
    fun observeExpense(id: String): Flow<ExpenseRow?>
    suspend fun execute(command: ExpenseCommand): CommandResult
}
class SaveExpenseUseCase(private val repository: ExpenseRepository) {
    suspend operator fun invoke(command: ExpenseCommand): CommandResult = repository.execute(command)
}
