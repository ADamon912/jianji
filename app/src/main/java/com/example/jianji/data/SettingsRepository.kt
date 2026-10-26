package com.example.jianji.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.jianji.domain.ThemeMode
import com.example.jianji.domain.ChartType
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore
    private val themeKey = stringPreferencesKey("theme")
    private val chartKey = stringPreferencesKey("chart_type")
    val chartType = store.data.map { value -> ChartType.entries.firstOrNull { it.name == value[chartKey] } ?: ChartType.DONUT }
    suspend fun setChartType(type: ChartType) { store.edit { it[chartKey] = type.name } }
    val theme = store.data.map { value -> ThemeMode.entries.firstOrNull { it.name == value[themeKey] } ?: ThemeMode.SYSTEM }
    suspend fun setTheme(mode: ThemeMode) { store.edit { it[themeKey] = mode.name } }
}
