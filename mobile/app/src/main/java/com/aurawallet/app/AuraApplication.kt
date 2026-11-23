package com.aurawallet.app

import android.app.Application
import com.aurawallet.app.data.HistoryRepository
import com.aurawallet.app.data.TransactionRepository
import com.aurawallet.app.data.local.AuraDatabase

/** Простий контейнер залежностей без DI-бібліотеки (рішення D8). */
class AuraApplication : Application() {
    val database: AuraDatabase by lazy { AuraDatabase.get(this) }
    val history: HistoryRepository by lazy { HistoryRepository(database.history()) }
    val transactions: TransactionRepository by lazy { TransactionRepository() }
}
