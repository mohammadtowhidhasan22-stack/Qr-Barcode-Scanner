package com.example

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ScanHistoryRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(appContext)
    }

    val historyRepository: ScanHistoryRepository by lazy {
        ScanHistoryRepository(database.scanHistoryDao())
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext)
    }
}
