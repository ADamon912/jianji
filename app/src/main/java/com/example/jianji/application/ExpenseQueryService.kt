package com.example.jianji.application

import androidx.paging.PagingData
import com.example.jianji.domain.*
import kotlinx.coroutines.flow.Flow

interface ExpenseQueryService {
    fun pages(filter: ExpenseFilter): Flow<PagingData<ExpenseRow>>
    fun observeSummary(filter: ExpenseFilter): Flow<ExpenseSummary>
    fun observeCategoryTotals(range: DateRange): Flow<List<CategoryTotal>>
}
