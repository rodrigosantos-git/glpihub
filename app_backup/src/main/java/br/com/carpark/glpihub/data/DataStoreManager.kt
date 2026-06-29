package br.com.carpark.glpihub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {
    
    companion object {
        val COOKIE_SESSION = stringPreferencesKey("cookie_session")
        val GLPI_CSRF_TOKEN = stringPreferencesKey("glpi_csrf_token")
        val SAVE_LOGIN = booleanPreferencesKey("save_login")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        
        val FILTER_ASSIGNEE = stringPreferencesKey("filter_assignee")
        val FILTER_CATEGORY = stringPreferencesKey("filter_category")
        val FILTER_REQUERENTE = stringPreferencesKey("filter_requerente")
        val FILTER_ENTIDADE = stringPreferencesKey("filter_entidade")
    }

    val sessionCookieFlow: Flow<String?> = context.dataStore.data.map { it[COOKIE_SESSION] }
    val csrfTokenFlow: Flow<String?> = context.dataStore.data.map { it[GLPI_CSRF_TOKEN] }
    val saveLoginFlow: Flow<Boolean> = context.dataStore.data.map { it[SAVE_LOGIN] ?: false }
    val usernameFlow: Flow<String?> = context.dataStore.data.map { it[USERNAME] }
    val passwordFlow: Flow<String?> = context.dataStore.data.map { it[PASSWORD] }
    
    val filterAssigneeFlow: Flow<String> = context.dataStore.data.map { it[FILTER_ASSIGNEE] ?: "" }
    val filterCategoryFlow: Flow<String> = context.dataStore.data.map { it[FILTER_CATEGORY] ?: "" }
    val filterRequerenteFlow: Flow<String> = context.dataStore.data.map { it[FILTER_REQUERENTE] ?: "" }
    val filterEntidadeFlow: Flow<String> = context.dataStore.data.map { it[FILTER_ENTIDADE] ?: "" }

    suspend fun saveFilters(assignee: String, category: String, requerente: String, entidade: String) {
        context.dataStore.edit { preferences ->
            preferences[FILTER_ASSIGNEE] = assignee
            preferences[FILTER_CATEGORY] = category
            preferences[FILTER_REQUERENTE] = requerente
            preferences[FILTER_ENTIDADE] = entidade
        }
    }

    suspend fun saveSession(cookie: String, csrfToken: String) {
        context.dataStore.edit { preferences ->
            preferences[COOKIE_SESSION] = cookie
            preferences[GLPI_CSRF_TOKEN] = csrfToken
        }
    }

    suspend fun saveCredentials(username: String, password: String, save: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USERNAME] = username
            preferences[PASSWORD] = password
            preferences[SAVE_LOGIN] = save
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(COOKIE_SESSION)
            preferences.remove(GLPI_CSRF_TOKEN)
        }
    }
}
