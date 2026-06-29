package br.com.carpark.glpihub.data

import br.com.carpark.glpihub.domain.Ticket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class GlpiScraper {

    private val baseUrl = "http://acesso.carpark.com.br:8080"
    private var sessionCookies: MutableMap<String, String> = mutableMapOf()

    // Realiza o login e salva os cookies da sessão
    suspend fun login(username: String, password: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Limpa qualquer sessão residual antes de tentar um novo login
            sessionCookies.clear()

            // 1. Acessa a página inicial e segue os redirecionamentos manualmente para NÃO PERDER os cookies (Bug conhecido do JSoup)
            var resInit = Jsoup.connect("$baseUrl/front/central.php")
                .method(Connection.Method.GET)
                .followRedirects(false)
                .execute()
            sessionCookies.putAll(resInit.cookies())

            while (resInit.hasHeader("Location")) {
                var loc = resInit.header("Location") ?: break
                if (!loc.startsWith("http")) {
                    loc = if (loc.startsWith("/")) "$baseUrl$loc" else "$baseUrl/$loc"
                }
                resInit = Jsoup.connect(loc)
                    .cookies(sessionCookies)
                    .method(Connection.Method.GET)
                    .followRedirects(false)
                    .execute()
                sessionCookies.putAll(resInit.cookies())
            }

            val doc = resInit.parse()

            // Nomes dos campos dinâmicos do GLPI
            val loginNameAttr = doc.select("input#login_name").first()?.attr("name") ?: "login_name"
            val loginPassAttr = doc.select("input#login_password").first()?.attr("name") ?: "login_password"
            val rememberNameAttr = doc.select("input#login_remember").first()?.attr("name") ?: "remember"
            val csrfToken = doc.select("input[name=_glpi_csrf_token]").first()?.attr("value") ?: ""

            // 2. Faz o POST para autenticar igual ao desktop
            var resLogin = Jsoup.connect("$baseUrl/front/login.php")
                .data("_glpi_csrf_token", csrfToken)
                .data("redirect", "/front/central.php")
                .data("auth", "ldap-1")
                .data("submit", "Enviar")
                .data(loginNameAttr, username)
                .data(loginPassAttr, password)
                .data(rememberNameAttr, "on")
                .cookies(sessionCookies)
                .method(Connection.Method.POST)
                .followRedirects(false)
                .execute()

            sessionCookies.putAll(resLogin.cookies())

            while (resLogin.hasHeader("Location")) {
                var loc = resLogin.header("Location") ?: break
                if (!loc.startsWith("http")) {
                    loc = if (loc.startsWith("/")) "$baseUrl$loc" else "$baseUrl/$loc"
                }
                resLogin = Jsoup.connect(loc)
                    .cookies(sessionCookies)
                    .method(Connection.Method.GET)
                    .followRedirects(false)
                    .execute()
                sessionCookies.putAll(resLogin.cookies())
            }

            // Verifica se a URL final do redirecionamento indica falha
            val loggedDoc = resLogin.parse()
            val finalUrl = resLogin.url().toString().lowercase()
            val isFailedUrl = finalUrl.contains("login") || finalUrl.contains("error")
            val hasErrorText = loggedDoc.body().text().contains("inválidos", ignoreCase = true)
            
            if (isFailedUrl || hasErrorText) {
                // Busca o erro na div.center.b (onde o GLPI exibe o erro) ou div.error
                val errorDiv = loggedDoc.select("div.center.b, div.error, div.message_error").first()
                var errorMsg = errorDiv?.text()?.trim() ?: "Nome de usuário ou senha inválidos"
                if (errorMsg.contains("Faça login novamente", ignoreCase = true)) {
                    errorMsg = errorMsg.split(Regex("(?i)Faça login novamente"))[0].trim()
                }
                return@withContext Pair(false, errorMsg)
            }
            
            return@withContext Pair(true, "")
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Pair(false, "Erro de rede: ${e.localizedMessage}")
        }
    }

    suspend fun getTickets(cookies: Map<String, String> = sessionCookies): List<Ticket> = withContext(Dispatchers.IO) {
        val tickets = mutableListOf<Ticket>()
        try {
            val res = Jsoup.connect("$baseUrl/front/ticket.php?reset=reset")
                .cookies(cookies)
                .method(Connection.Method.GET)
                .execute()

            val doc = res.parse()
            // A classe da tabela no GLPI 9.5 é 'tab_cadrehov' e em outras é 'tab_cadre_fixe'.
            // Usar 'table[class*=tab_cadre]' abrange todos os casos e não falha.
            val rows = doc.select("table[class*=tab_cadre] tr")

            for (row in rows) {
                val cols = row.select("td")
                if (cols.size >= 10) { // O GLPI do usuário tem 13 colunas na listagem
                    // O GLPI pode retornar o ID formatado com espaço não-separável (ex: "21 125").
                    val rawId = cols.getOrNull(1)?.text() ?: ""
                    val id = rawId.replace(Regex("[^0-9]"), "")

                    if (id.isNotEmpty()) {
                        // O Título tem um tooltip invisível dentro da td, então pegamos apenas o link <a> principal
                        val titulo = cols.getOrNull(2)?.selectFirst("a")?.text() ?: cols.getOrNull(2)?.text() ?: "Sem Título"
                        
                        val entidade = cols.getOrNull(3)?.text()?.trim() ?: ""
                        val dataAbertura = cols.getOrNull(4)?.text()?.trim() ?: ""
                        
                        // ownText() extrai apenas o texto da própria célula (ex: "Nome (ID)"), ignorando o <div> escondido que tem email/ramal
                        val requerente = cols.getOrNull(6)?.ownText()?.trim() ?: ""
                        val atribuido = cols.getOrNull(7)?.ownText()?.trim() ?: ""
                        
                        val categoria = cols.getOrNull(8)?.text()?.trim() ?: ""
                        val status = cols.getOrNull(9)?.text()?.trim() ?: ""

                        // Extração da descrição removendo o Título do texto completo da célula (que contém o tooltip)
                        val tituloCellText = cols.getOrNull(2)?.text() ?: ""
                        var description = tituloCellText.replace(titulo, "").trim()
                        // Remove pontuações/símbolos residuais no início da descrição
                        description = description.replace(Regex("^[\\s\\-\\:\\,\\.\\/]+"), "")
                        if (description.isEmpty()) description = "Sem descrição detalhada."

                        tickets.add(
                            Ticket(
                                id = id,
                                titulo = titulo,
                                entidade = entidade,
                                dataAbertura = dataAbertura,
                                requerente = requerente,
                                atribuido = atribuido,
                                categoria = categoria,
                                status = status,
                                description = description
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext tickets
    }

    // Busca as interações (Follow-ups e Tasks) de um chamado específico usando as abas AJAX do GLPI 9.5
    suspend fun getTicketDetails(ticketId: String, cookies: Map<String, String> = sessionCookies): List<br.com.carpark.glpihub.domain.TicketInteraction> = withContext(Dispatchers.IO) {
        val interactions = mutableListOf<br.com.carpark.glpihub.domain.TicketInteraction>()
        try {
            // URL baseada no padrão verificado para a timeline do GLPI 9.5 (do server.py)
            val urlTabs = "$baseUrl/ajax/common.tabs.php?_target=/front/ticket.form.php&_itemtype=Ticket&_glpi_tab=Ticket$1&id=$ticketId"
            
            val res = Jsoup.connect(urlTabs)
                .cookies(cookies)
                .header("X-Requested-With", "XMLHttpRequest")
                .method(Connection.Method.GET)
                .execute()

            val doc = res.parse()
            // Procura itens da timeline
            val items = doc.select("div[class~=(?i)(h[-_]item|timeline[-_]item|tracking)]")
            
            for (item in items) {
                // Pega autor e data do info_div
                val infoDiv = item.selectFirst("div[class~=(?i)(h[-_]info|timeline[-_]info|info|header)]")
                
                var author = "Sistema"
                var dateStr = "Sem data"
                
                if (infoDiv != null) {
                    val authorEl = infoDiv.selectFirst("a[href*=user.form.php]") ?: infoDiv.selectFirst("a")
                    if (authorEl != null) {
                        author = authorEl.text().trim()
                    }
                    val dateEl = infoDiv.selectFirst("span[class~=(?i)(date|time|timestamp)], div[class~=(?i)(date|time|timestamp)]")
                    if (dateEl != null) {
                        dateStr = dateEl.text().trim()
                    }
                }
                
                // Pega conteúdo
                val bodyDiv = item.selectFirst("div[class~=(?i)(rich[-_]text[-_]container|item[-_]content|h[-_]content|timeline[-_]content|content|body)]")
                val content = bodyDiv?.text()?.trim() ?: item.text().replace(infoDiv?.text() ?: "", "").trim()
                if (content.isEmpty() || content.lowercase().contains("mais detalhes")) continue
                
                // Identifica se é tarefa (TicketTask)
                val isTask = item.html().contains("change_task_state") || item.selectFirst("[class*=state_]") != null
                var taskState = "todo"
                if (isTask) {
                    if (item.html().contains("state_2")) taskState = "done"
                }

                // Limpeza do nome do autor parecida com o desktop
                val cleanAuthor = author.replace(Regex("(?i)^(técnico\\(s\\)?|tech|responsável)\\s*:\\s*"), "")
                    .replace(Regex("[\\(\\[\\{].*?[\\)\\]\\}]"), "")
                    .trim()

                interactions.add(
                    br.com.carpark.glpihub.domain.TicketInteraction(
                        author = cleanAuthor.ifEmpty { "Não Atribuído" },
                        date = dateStr,
                        content = content,
                        isTask = isTask,
                        taskState = taskState
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext interactions
    }

    suspend fun addFollowup(ticketId: String, content: String, cookies: Map<String, String> = sessionCookies): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Acessa o chamado para pegar o token CSRF
            val resTicket = Jsoup.connect("$baseUrl/front/ticket.form.php?id=$ticketId")
                .cookies(cookies)
                .method(Connection.Method.GET)
                .execute()

            val docTicket = resTicket.parse()
            val csrfToken = docTicket.select("input[name=_glpi_csrf_token]").first()?.attr("value") ?: ""

            if (csrfToken.isEmpty()) return@withContext false

            // 2. Faz o POST para o itilfollowup.form.php
            val resPost = Jsoup.connect("$baseUrl/front/itilfollowup.form.php")
                .data("itemtype", "Ticket")
                .data("items_id", ticketId)
                .data("content", content)
                .data("add", "Adicionar")
                .data("_glpi_csrf_token", csrfToken)
                .cookies(cookies)
                .method(Connection.Method.POST)
                .followRedirects(true)
                .execute()

            return@withContext resPost.statusCode() == 200
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    fun clearSession() {
        sessionCookies.clear()
    }
}
