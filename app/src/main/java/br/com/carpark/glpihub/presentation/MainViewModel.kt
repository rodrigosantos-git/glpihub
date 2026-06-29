package br.com.carpark.glpihub.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carpark.glpihub.data.DataStoreManager
import br.com.carpark.glpihub.data.GlpiScraper
import br.com.carpark.glpihub.domain.Ticket
import br.com.carpark.glpihub.domain.TicketInteraction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class MainViewModel(
    private val scraper: GlpiScraper,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _tickets = MutableStateFlow<List<Ticket>>(emptyList())
    val tickets: StateFlow<List<Ticket>> = _tickets.asStateFlow()

    private val _ticketInteractions = MutableStateFlow<List<TicketInteraction>>(emptyList())
    val ticketInteractions: StateFlow<List<TicketInteraction>> = _ticketInteractions.asStateFlow()
    
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()
    
    val filterAssignee = dataStoreManager.filterAssigneeFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val filterCategory = dataStoreManager.filterCategoryFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val filterRequerente = dataStoreManager.filterRequerenteFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val filterEntidade = dataStoreManager.filterEntidadeFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")

    val savedUsername = dataStoreManager.usernameFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val savedPassword = dataStoreManager.passwordFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val savedSaveLogin = dataStoreManager.saveLoginFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val themeType = dataStoreManager.themeTypeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "light")
    val aiApiKey = dataStoreManager.aiApiKeyFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun saveThemeType(theme: String) {
        viewModelScope.launch {
            dataStoreManager.saveThemeType(theme)
        }
    }

    fun saveAiApiKey(key: String) {
        viewModelScope.launch {
            dataStoreManager.saveAiApiKey(key)
        }
    }

    fun saveFilters(assignee: String, category: String, requerente: String, entidade: String) {
        viewModelScope.launch {
            dataStoreManager.saveFilters(assignee, category, requerente, entidade)
        }
    }

    fun checkLoginStatus() {
        viewModelScope.launch {
            val username = dataStoreManager.usernameFlow.first()
            val password = dataStoreManager.passwordFlow.first()
            val saveLogin = dataStoreManager.saveLoginFlow.first()
            
            if (saveLogin && !username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                login(username, password, true)
            } else {
                _isLoggedIn.value = false
            }
        }
    }

    fun login(username: String, pass: String, save: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            val (success, errorMsg) = scraper.login(username, pass)
            if (success) {
                if (save) {
                    dataStoreManager.saveCredentials(username, pass, true)
                }
                _isLoggedIn.value = true
            } else {
                _isLoggedIn.value = false
                _loginError.value = errorMsg
            }
            _isLoading.value = false
        }
    }


    fun loadTickets() {
        viewModelScope.launch {
            _isLoading.value = true
            val tks = scraper.getTickets() // Agora usará os sessionCookies por padrão
            _tickets.value = tks
            _isLoading.value = false
        }
    }

    fun loadTicketInteractions(ticketId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _ticketInteractions.value = emptyList()
            val interactions = scraper.getTicketDetails(ticketId)
            _ticketInteractions.value = interactions
            _isLoading.value = false
        }
    }

    fun postFollowup(ticketId: String, content: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = scraper.addFollowup(ticketId, content)
            if (success) {
                // Recarrega as interações do chamado
                val interactions = scraper.getTicketDetails(ticketId)
                _ticketInteractions.value = interactions
            }
            _isLoading.value = false
            onResult(success)
        }
    }

    fun addSolution(
        ticketId: String,
        templateName: String,
        typeName: String,
        content: String,
        onResult: (Boolean) -> Unit
    ) {
        val templateId = when (templateName) {
            "01 - ACESSO REMOTO (3)" -> "3"
            "02 - VISITA TECNICA CARPARK (2)" -> "2"
            "03 - RESOLVIDO (1)" -> "1"
            "04 - CUSTOMIZACAO (5)" -> "5"
            "05 - TREINAMENTO (4)" -> "4"
            "06 - CANCELADO (6)" -> "6"
            else -> "0"
        }

        val typeId = when (typeName) {
            "01 - OK REMOTAMENTE (4)" -> "4"
            "02 - OK INLOCO (1)" -> "1"
            "03 - OK SEM INTERVENCAO TECNICA (5)" -> "5"
            "03 - OK VIA PRESTADOR DE SERVICO (11)" -> "11"
            "04 - NAO RESOLVIDO (3)" -> "3"
            "05 - CANCELADO (2)" -> "2"
            "06 - PLANTAO TECNICO (12)" -> "12"
            "CUSTOMIZACAO (7)" -> "7"
            "DESENVOLVIMENTO (8)" -> "8"
            "RESTART APLICACAO (10)" -> "10"
            "TREINAMENTO (6)" -> "6"
            "TROCA DE PEÇAS (9)" -> "9"
            else -> "0"
        }

        viewModelScope.launch {
            _isLoading.value = true
            val success = scraper.addSolution(ticketId, templateId, typeId, content)
            if (success) {
                val tks = scraper.getTickets()
                _tickets.value = tks
            }
            _isLoading.value = false
            onResult(success)
        }
    }

    fun logout() {
        viewModelScope.launch {
            scraper.clearSession()
            dataStoreManager.clearSession()
            _isLoggedIn.value = false
        }
    }
}
