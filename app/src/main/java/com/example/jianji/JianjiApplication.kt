package com.example.jianji

import android.app.Application
import androidx.room.Room
import com.example.jianji.data.LedgerDatabase
import com.example.jianji.data.RoomExpenseRepository
import com.example.jianji.data.SettingsRepository
import com.example.jianji.domain.SaveExpenseUseCase

class JianjiApplication : Application() {
    val container by lazy { AppContainer(this) }
}
class AppContainer(application: Application) {
    private val database = Room.databaseBuilder(application, LedgerDatabase::class.java, "jianji.db").build()
    val expenses = RoomExpenseRepository(database)
    val saveExpense = SaveExpenseUseCase(expenses)
    val settings = SettingsRepository(application)
    val documents = com.example.jianji.data.AndroidLedgerDocuments(application.contentResolver)
}
