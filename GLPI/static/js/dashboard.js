/**
   GLPI HUB - Modern Client Controller
   Senior architecture, clean code, modular ES6+ JavaScript.
   Prevens global scope pollution, protects against XSS, and runs dynamically.
*/

(function() {
    'use strict';

    // Estado centralizado da aplicação
    const state = {
        tickets: [],
        filteredTickets: [],
        currentTechFilter: 'all',
        currentCategoryFilter: 'all',
        searchTerm: '',
        sortOrder: 'newest',
        refreshRate: 0,
        layoutView: 'grid',
        theme: 'light',
        notificationsMode: 'all',
        autoRefreshIntervalId: null
    };

    // Cache local de detalhes dos chamados (Acompanhamentos e última interação)
    const detailsCache = {};
    let hydrationQueue = [];
    let isHydrating = false;

    // Cache de Elementos do DOM (Evita pesquisas repetidas e melhora performance)
    const el = {
        loginOverlay: document.getElementById('login-overlay'),
        loginForm: document.getElementById('login-form'),
        loginUsername: document.getElementById('login-username'),
        loginPassword: document.getElementById('login-password'),
        loginDomain: document.getElementById('login-domain'),
        loginError: document.getElementById('login-error-message'),
        btnLoginSubmit: document.getElementById('btn-login-submit'),
        
        btnRefresh: document.getElementById('btn-refresh'),
        btnLogout: document.getElementById('btn-logout'),
        btnSettings: document.getElementById('btn-settings'),
        settingsOverlay: document.getElementById('settings-overlay'),
        btnCloseSettings: document.getElementById('btn-close-settings'),
        settingsRefreshRate: document.getElementById('settings-refresh-rate'),
        settingsNotifications: document.getElementById('settings-notifications'),
        btnSettingsSave: document.getElementById('btn-settings-save'),
        
        searchInput: document.getElementById('search-input'),
        sortOrderSelect: document.getElementById('sort-order'),
        categoryFilterSelect: document.getElementById('category-filter'),
        techFilterSelect: document.getElementById('tech-filter'),
        
        systemStatusDot: document.getElementById('system-status-dot'),
        systemStatusText: document.getElementById('system-status-text'),
        
        kpiTotal: document.getElementById('kpi-total'),
        kpiPending: document.getElementById('kpi-pending'),
        kpiSolved: document.getElementById('kpi-solved'),
        
        ticketsGridContainer: document.getElementById('tickets-grid-container'),
        userBadge: document.getElementById('user-badge'),
        displayUser: document.getElementById('display-user'),
        avatarLetters: document.getElementById('avatar-letters')
    };

    // Mapeamento estático para categorias-chave (outras serão geradas dinamicamente via HSL hashing)
    const fixedCategoryColors = {
        '01 - ti > 04 - sistema epark': '#6366f1',
        '01 - ti > 01 - hardware': '#10b981',
        '01 - ti > 02 - software': '#f59e0b',
        '01 - ti > 03 - rede': '#ef4444',
        '01 - ti > 05 - telefonia': '#ec4899',
        'sem categoria': '#94a3b8'
    };

    // Inicialização da aplicação
    async function init() {
        await loadSettings();
        checkAuthStatus();
        setupEventListeners();
        
        // Solicita permissão para notificações do sistema (OS-level)
        if ('Notification' in window && Notification.permission === 'default') {
            Notification.requestPermission();
        }
    }

    // Gerador de cores HSL dinâmico e consistente baseado em Hash de string
    // Garante que cada categoria tenha uma cor única, mas estável e visualmente harmoniosa
    function getCategoryColor(category) {
        if (!category) return '#94a3b8';
        const cleanCat = category.toLowerCase().trim();
        if (fixedCategoryColors[cleanCat]) {
            return fixedCategoryColors[cleanCat];
        }
        
        // Hashing simples e rápido
        let hash = 0;
        for (let i = 0; i < cleanCat.length; i++) {
            hash = cleanCat.charCodeAt(i) + ((hash << 5) - hash);
        }
        
        // Mantém a matiz entre 0 e 360, saturação 65% e luminosidade 45% (estilo Coursue de alto contraste)
        const hue = Math.abs(hash) % 360;
        return `hsl(${hue}, 65%, 45%)`;
    }

    // Verifica se a sessão do usuário com o backend está ativa
    async function checkAuthStatus() {
        setSystemStatus('loading', 'Verificando sessão...');
        try {
            const res = await fetch('/api/auth/status');
            const data = await res.json();
            
            if (data.authenticated) {
                // Recupera nome de usuário armazenado localmente para o cabeçalho
                const storedUser = localStorage.getItem('glpi_username') || 'Usuário';
                updateUserUI(storedUser);
                hideLogin();
                loadTickets();
            } else {
                showLogin();
            }
        } catch (err) {
            console.error('Erro na validação de sessão:', err);
            showLogin();
            showError('Erro de conexão com o servidor local.');
        }
    }

    // Controla o indicador de status de sincronização
    function setSystemStatus(type, text) {
        el.systemStatusDot.className = 'sync-dot';
        if (type === 'loading') {
            el.systemStatusDot.classList.add('loading');
        } else if (type === 'error') {
            el.systemStatusDot.classList.add('error');
        }
        el.systemStatusText.textContent = text;
    }

    // Configura os escutadores de eventos
    function setupEventListeners() {
        // Envio do formulário de Login
        el.loginForm.addEventListener('submit', handleLogin);
        
        // Atualização Manual de Chamados
        el.btnRefresh.addEventListener('click', loadTickets);
        
        // Logout da Sessão
        el.btnLogout.addEventListener('click', handleLogout);
        
        // Filtro de Busca por Texto
        el.searchInput.addEventListener('input', (e) => {
            state.searchTerm = e.target.value.toLowerCase().trim();
            applyFiltersAndRender();
        });
        
        // Filtro de Ordenação
        el.sortOrderSelect.addEventListener('change', (e) => {
            state.sortOrder = e.target.value;
            applyFiltersAndRender();
        });

        // Filtro Dinâmico de Categoria
        el.categoryFilterSelect.addEventListener('change', (e) => {
            state.currentCategoryFilter = e.target.value;
            applyFiltersAndRender();
        });

        // Filtro Dinâmico de Técnico
        el.techFilterSelect.addEventListener('change', (e) => {
            state.currentTechFilter = e.target.value;
            applyFiltersAndRender();
        });

        // Clique no botão de Expansão de Acompanhamentos (Delegated Event Listener)
        el.ticketsGridContainer.addEventListener('click', (e) => {
            const btn = e.target.closest('.btn-expand-followups');
            if (btn) {
                const ticketId = btn.getAttribute('data-id');
                handleToggleFollowups(ticketId, btn);
            }
        });

        // Clique no botão de Configurações
        el.btnSettings.addEventListener('click', openSettings);
        
        // Fechar configurações
        el.btnCloseSettings.addEventListener('click', closeSettings);
        
        // Salvar configurações
        el.btnSettingsSave.addEventListener('click', saveSettings);
        
        // Fechar clicando fora do card de configurações
        el.settingsOverlay.addEventListener('click', (e) => {
            if (e.target === el.settingsOverlay) {
                closeSettings();
            }
        });

        // Configura o botão de rolagem rápida (Voltar ao Topo)
        const btnScrollTop = document.getElementById('btn-scroll-top');
        if (btnScrollTop) {
            window.addEventListener('scroll', () => {
                if (window.scrollY > 300) {
                    btnScrollTop.classList.add('visible');
                } else {
                    btnScrollTop.classList.remove('visible');
                }
            });

            btnScrollTop.addEventListener('click', () => {
                window.scrollTo({
                    top: 0,
                    behavior: 'smooth'
                });
            });
        }

        // Atalho de teste de notificações (Ctrl + Shift + N)
        window.addEventListener('keydown', (e) => {
            if (e.ctrlKey && e.shiftKey && e.key.toUpperCase() === 'N') {
                e.preventDefault();
                console.log('[Dev-Mode] Disparando simulação de novo chamado para teste.');
                
                const maxId = state.tickets.length > 0 ? Math.max(...state.tickets.map(t => parseInt(t.id, 10) || 0)) : 10000;
                const newMockId = (maxId + 1).toString();
                
                const mockTicket = {
                    id: newMockId,
                    title: "CHAMADO DE TESTE DO SISTEMA DE NOTIFICAÇÕES",
                    description: "Este é um chamado simulado para testar visualmente e auditivamente as notificações do GLPI HUB.",
                    date: new Date().toLocaleString('pt-BR'),
                    category: "01 - TI > 04 - SISTEMA EPARK",
                    entity: "GERAL",
                    requester: "ADMINISTRADOR",
                    technician: "NÃO ATRIBUÍDO",
                    status: "Novo",
                    priority: "Alta",
                    url: "http://acesso.carpark.com.br:8080/front/ticket.form.php?id=" + newMockId
                };

                checkForNewTickets([mockTicket]);
                
                state.tickets.unshift(mockTicket);
                applyFiltersAndRender();
                updateKPIs();
            }
        });
    }

    // Controle da camada de Login
    function showLogin() {
        el.loginOverlay.classList.add('active');
        el.searchInput.disabled = true;
        el.btnRefresh.disabled = true;
        el.sortOrderSelect.disabled = true;
        el.categoryFilterSelect.disabled = true;
        el.techFilterSelect.disabled = true;
        if (el.btnSettings) el.btnSettings.disabled = true;
        el.userBadge.style.display = 'none';
        setSystemStatus('error', 'Sessão Expirada');
    }

    function hideLogin() {
        el.loginOverlay.classList.remove('active');
        el.searchInput.disabled = false;
        el.btnRefresh.disabled = false;
        el.sortOrderSelect.disabled = false;
        el.categoryFilterSelect.disabled = false;
        el.techFilterSelect.disabled = false;
        if (el.btnSettings) el.btnSettings.disabled = false;
        el.userBadge.style.display = 'flex';
        hideError();
    }

    function showError(msg) {
        el.loginError.textContent = msg;
        el.loginError.style.display = 'block';
    }

    function hideError() {
        el.loginError.style.display = 'none';
    }

    function updateUserUI(username) {
        el.displayUser.textContent = username;
        el.avatarLetters.textContent = username.substring(0, 2).toUpperCase();
        localStorage.setItem('glpi_username', username);
    }

    // Processamento de Login
    async function handleLogin(e) {
        e.preventDefault();
        hideError();
        
        const username = el.loginUsername.value.trim();
        const password = el.loginPassword.value;
        const auth_method = el.loginDomain.value;
        const remember_password = document.getElementById('login-remember-password')?.checked || false;
        
        if (!username || !password) {
            showError('Por favor, preencha todos os campos.');
            return;
        }

        el.btnLoginSubmit.disabled = true;
        el.btnLoginSubmit.innerHTML = '<span>Autenticando...</span>';
        
        try {
            const res = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password, auth_method, remember_password })
            });
            const data = await res.json();
            
            if (res.ok && data.success) {
                updateUserUI(username);
                hideLogin();
                loadTickets();
            } else {
                showError(data.error || 'Falha de credenciais no GLPI.');
            }
        } catch (err) {
            console.error('Erro de requisição de login:', err);
            showError('Erro ao se conectar ao servidor backend.');
        } finally {
            el.btnLoginSubmit.disabled = false;
            el.btnLoginSubmit.innerHTML = '<span>Acessar Painel</span>';
        }
    }

    // Encerramento de Sessão
    async function handleLogout() {
        if (!confirm('Deseja realmente encerrar sua sessão no painel?')) return;
        
        try {
            await fetch('/api/auth/logout', { method: 'POST' });
        } catch (err) {
            console.error('Erro no logout do servidor:', err);
        }
        
        localStorage.removeItem('glpi_username');
        state.tickets = [];
        state.filteredTickets = [];
        buildDropdownFilters();
        renderTicketsGrid();
        showLogin();
    }

    // Carregamento de chamados a partir da API
    async function loadTickets() {
        setSystemStatus('loading', 'Obtendo chamados do GLPI...');
        el.btnRefresh.disabled = true;
        renderSkeletons();
        
        try {
            const res = await fetch('/api/tickets');
            if (res.status === 401) {
                showLogin();
                return;
            }
            
            if (!res.ok) {
                const errData = await res.json();
                throw new Error(errData.error || 'Falha na resposta do servidor');
            }
            
            const data = await res.json();
            
            // Verifica e dispara notificações caso existam novos chamados
            checkForNewTickets(data);
            
            state.tickets = data;
            
            updateKPIs();
            buildDropdownFilters();
            applyFiltersAndRender();
            setSystemStatus('success', 'Sincronizado');
        } catch (err) {
            console.error('Erro ao obter chamados:', err);
            setSystemStatus('error', 'Falha na sincronização');
            el.ticketsGridContainer.innerHTML = `
                <div class="empty-state">
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
                    </svg>
                    <h3>Falha ao carregar dados do GLPI</h3>
                    <p>${escapeHtml(err.message || 'Verifique sua conexão ou status da sessão do GLPI.')}</p>
                </div>
            `;
        } finally {
            el.btnRefresh.disabled = false;
        }
    }

    // Atualização dos indicadores (KPIs)
    function updateKPIs() {
        const total = state.tickets.length;
        
        // Pendentes = Status diferente de "solucionado" ou "fechado"
        const pendingTickets = state.tickets.filter(t => {
            const st = t.status.toLowerCase();
            return !st.includes('solucionado') && !st.includes('fechado') && !st.includes('solved') && !st.includes('closed');
        });
        
        const pendingCount = pendingTickets.length;
        const solvedCount = total - pendingCount;
        
        el.kpiTotal.textContent = total;
        el.kpiPending.textContent = pendingCount;
        el.kpiSolved.textContent = solvedCount;
    }

    // Geração dinâmica das opções nos dropdowns de filtro (Categoria e Técnico)
    function buildDropdownFilters() {
        const categories = new Set();
        const technicians = new Set();
        
        state.tickets.forEach(t => {
            if (t.category) categories.add(t.category.trim());
            if (t.technician && t.technician.trim() !== 'Não Atribuído') {
                technicians.add(t.technician.trim());
            }
        });
        
        // 1. Popula Categoria
        const sortedCats = Array.from(categories).sort();
        let catHtml = '<option value="all">TODAS AS CATEGORIAS</option>';
        sortedCats.forEach(cat => {
            const selected = state.currentCategoryFilter === cat ? 'selected' : '';
            catHtml += `<option value="${escapeHtml(cat)}" ${selected}>${escapeHtml(cat.toUpperCase())}</option>`;
        });
        el.categoryFilterSelect.innerHTML = catHtml;
        
        // 2. Popula Técnico Atribuído
        const sortedTechs = Array.from(technicians).sort();
        let techHtml = '<option value="all">TODOS OS TÉCNICOS</option>';
        
        const hasUnassigned = state.tickets.some(t => t.technician === 'Não Atribuído');
        if (hasUnassigned) {
            const selected = state.currentTechFilter === 'unassigned' ? 'selected' : '';
            techHtml += `<option value="unassigned" ${selected}>NÃO ATRIBUÍDOS</option>`;
        }
        
        sortedTechs.forEach(tech => {
            const selected = state.currentTechFilter === tech ? 'selected' : '';
            techHtml += `<option value="${escapeHtml(tech)}" ${selected}>${escapeHtml(tech.toUpperCase())}</option>`;
        });
        el.techFilterSelect.innerHTML = techHtml;
    }

    // Processamento e cruzamento dos filtros ativados
    function applyFiltersAndRender() {
        let result = [...state.tickets];
        
        // 1. Filtro por Técnico
        if (state.currentTechFilter === 'unassigned') {
            result = result.filter(t => t.technician === 'Não Atribuído');
        } else if (state.currentTechFilter !== 'all') {
            result = result.filter(t => t.technician === state.currentTechFilter);
        }
        
        // 2. Filtro por Categoria
        if (state.currentCategoryFilter !== 'all') {
            result = result.filter(t => t.category === state.currentCategoryFilter);
        }
        
        // 3. Filtro por Busca Textual
        if (state.searchTerm) {
            result = result.filter(t => 
                t.id.includes(state.searchTerm) || 
                t.title.toLowerCase().includes(state.searchTerm) || 
                t.description.toLowerCase().includes(state.searchTerm) ||
                t.requester.toLowerCase().includes(state.searchTerm) ||
                t.technician.toLowerCase().includes(state.searchTerm) ||
                t.entity.toLowerCase().includes(state.searchTerm) ||
                t.category.toLowerCase().includes(state.searchTerm)
            );
        }
        
        // 4. Ordenação Temporal
        if (state.sortOrder === 'oldest') {
            result.reverse();
        }
        
        state.filteredTickets = result;
        renderTicketsGrid();
    }

    // Determina a classe de estilo do Badge baseado no Status
    function getStatusClass(statusText) {
        const text = statusText.toLowerCase();
        if (text.includes('novo') || text.includes('new')) {
            return 'novo';
        } else if (text.includes('atribuido') || text.includes('atribuído') || text.includes('processamento') || text.includes('processing')) {
            return 'atribuido';
        } else if (text.includes('pendente') || text.includes('pending')) {
            return 'pendente';
        } else if (text.includes('solucionado') || text.includes('fechado') || text.includes('solved') || text.includes('closed')) {
            return 'solucionado';
        }
        return 'default';
    }

    // Renderiza a Grid de Cards
    function renderTicketsGrid() {
        if (state.filteredTickets.length === 0) {
            el.ticketsGridContainer.innerHTML = `
                <div class="empty-state">
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
                    </svg>
                    <h3>Nenhum chamado localizado</h3>
                    <p>Tente ajustar os seletores de filtros ou modificar o termo pesquisado.</p>
                </div>
            `;
            return;
        }
        
        let html = '';
        state.filteredTickets.forEach(t => {
            const statusClass = getStatusClass(t.status);
            const catColor = getCategoryColor(t.category);
            
            html += `
                <div class="ticket-card">
                    <div class="category-indicator-bar" style="background-color: ${catColor};"></div>
                    
                    <div class="card-top">
                        <span class="ticket-id">#${t.id}</span>
                        <span class="status-badge ${statusClass}">${escapeHtml(t.status)}</span>
                    </div>
                    
                    <div class="card-info-group">
                        <!-- TÍTULO -->
                        <div class="card-info-item">
                            <span class="card-info-label">TITULO</span>
                            <span class="card-info-value title-value" title="${escapeHtml(t.title)}">${escapeHtml(t.title)}</span>
                        </div>
                        
                        <!-- DATA DE ABERTURA -->
                        <div class="card-info-item">
                            <span class="card-info-label">DATA DE ABERTURA</span>
                            <span class="card-info-value">${escapeHtml(t.date)}</span>
                        </div>
                        
                        <!-- ENTIDADE -->
                        <div class="card-info-item">
                            <span class="card-info-label">ENTIDADE</span>
                            <span class="card-info-value" style="font-weight: 600;">${escapeHtml(t.entity.toUpperCase())}</span>
                        </div>
                        
                        <!-- CATEGORIA -->
                        <div class="card-info-item">
                            <span class="card-info-label">CATEGORIA</span>
                            <div class="category-tag" style="border-color: ${catColor}40;">
                                <span class="category-dot" style="background-color: ${catColor};"></span>
                                <span class="card-info-value" style="color: ${catColor}; font-weight: 700;">${escapeHtml(t.category.toUpperCase())}</span>
                            </div>
                        </div>

                        <!-- STATUS -->
                        <div class="card-info-item">
                            <span class="card-info-label">STATUS</span>
                            <span class="status-badge ${statusClass}" style="width: fit-content;">${escapeHtml(t.status)}</span>
                        </div>
                        
                        <!-- ATRIBUIDO -->
                        <div class="card-info-item">
                            <span class="card-info-label">ATRIBUIDO</span>
                            <span class="card-info-value" style="font-weight: 600;">${escapeHtml(t.technician.toUpperCase())}</span>
                        </div>
                        
                        <!-- ULTIMA INTERAÇÃO -->
                        <div class="card-info-item">
                            <span class="card-info-label">ULTIMA INTERAÇÃO</span>
                            <span class="card-info-value" id="last-interaction-${t.id}" style="font-weight: 700;">CARREGANDO...</span>
                        </div>
                        
                        <!-- DESCRIÇÃO -->
                        <div class="card-info-item description-section">
                            <span class="card-info-label">DESCRIÇÃO</span>
                            <div class="card-info-value description-value">${escapeHtml(t.description)}</div>
                        </div>

                        <!-- TAREFAS -->
                        <div class="card-info-item tasks-section" id="tasks-section-${t.id}" style="display: none;">
                            <span class="card-info-label">TAREFAS</span>
                            <div class="tasks-container" id="tasks-container-${t.id}"></div>
                        </div>
                    </div>
                    
                    <!-- RODAPÉ DE AÇÕES E EXPANSÃO -->
                    <div class="card-footer-row">
                        <!-- BOTÃO EXPANDIR ACOMPANHAMENTOS -->
                        <button class="btn-expand-followups" data-id="${t.id}">
                            <span>Expandir Acompanhamentos</span>
                            <svg class="icon-chevron" xmlns="http://www.w3.org/2000/svg" width="14" height="14" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
                                <polyline points="6 9 12 15 18 9"/>
                            </svg>
                        </button>
                        
                        <div class="card-actions">
                            <a href="${t.url}" target="_blank" class="btn-open-glpi">
                                <span>Visualizar no GLPI</span>
                                <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
                                    <polyline points="15 3 21 3 21 9"/>
                                    <line x1="10" y1="14" x2="21" y2="3"/>
                                </svg>
                            </a>
                        </div>
                    </div>
                    
                    <!-- CONTAINER DE COMENTÁRIOS -->
                    <div class="followups-container" id="followups-${t.id}" style="display: none;"></div>
                </div>
            `;
        });
        el.ticketsGridContainer.innerHTML = html;
        
        // Inicia a hidratação em background das datas de última interação
        startBackgroundHydration();
    }

    // Renderiza esqueletos (Skeletons) durante o carregamento de dados
    function renderSkeletons() {
        let html = '';
        for (let i = 0; i < 6; i++) {
            html += `
                <div class="skeleton-card">
                    <div class="card-top">
                        <div class="skeleton-pulse skeleton-id"></div>
                        <div class="skeleton-pulse skeleton-badge"></div>
                    </div>
                    <div class="card-info-group">
                        <div class="card-info-item">
                            <div class="skeleton-pulse skeleton-label" style="margin-bottom: 4px;"></div>
                            <div class="skeleton-pulse skeleton-title"></div>
                        </div>
                        <div class="card-info-item">
                            <div class="skeleton-pulse skeleton-label" style="margin-bottom: 4px;"></div>
                            <div class="skeleton-pulse skeleton-line"></div>
                        </div>
                        <div class="card-info-item">
                            <div class="skeleton-pulse skeleton-label" style="margin-bottom: 4px;"></div>
                            <div class="skeleton-pulse skeleton-line"></div>
                        </div>
                        <div class="card-info-item">
                            <div class="skeleton-pulse skeleton-label" style="margin-bottom: 4px;"></div>
                            <div class="skeleton-pulse skeleton-line" style="width: 60%; height: 22px;"></div>
                        </div>
                        <div class="card-info-item">
                            <div class="skeleton-pulse skeleton-label" style="margin-bottom: 4px;"></div>
                            <div class="skeleton-pulse skeleton-desc"></div>
                        </div>
                    </div>
                    <div class="skeleton-pulse skeleton-btn"></div>
                </div>
            `;
        }
        el.ticketsGridContainer.innerHTML = html;
    }

    // Função de escape de caracteres contra ataques XSS
    function escapeHtml(str) {
        if (!str) return '';
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // Controla a expansão/recolhimento e o carregamento assíncrono dos acompanhamentos
    async function handleToggleFollowups(ticketId, btn) {
        const container = document.getElementById(`followups-${ticketId}`);
        if (!container) return;

        const isActive = btn.classList.contains('active');

        if (isActive) {
            btn.classList.remove('active');
            container.style.display = 'none';
            btn.querySelector('span').textContent = 'Expandir Acompanhamentos';
        } else {
            btn.classList.add('active');
            container.style.display = 'flex';
            btn.querySelector('span').textContent = 'Recolher Acompanhamentos';

            // Se não houver cache dos detalhes, carrega da API
            if (!detailsCache[ticketId]) {
                container.innerHTML = `
                    <div class="followups-loading">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 1121.21 7.89M9 11l3-3 3 3m-3-3v12" />
                        </svg>
                        <span>Obtendo acompanhamentos...</span>
                    </div>
                `;
                try {
                    const res = await fetch(`/api/tickets/${ticketId}/details`);
                    if (!res.ok) throw new Error('Erro ao obter detalhes');
                    const data = await res.json();
                    detailsCache[ticketId] = data;

                    // Atualiza a última interação caso retornado
                    updateLastInteractionUI(ticketId, data.last_interaction);
                    renderTasksUI(ticketId, data.tasks);
                } catch (err) {
                    console.error(err);
                    container.innerHTML = `<div class="followup-item" style="color: var(--accent-red); font-weight: 600;">Erro ao sincronizar histórico com o GLPI.</div>`;
                    return;
                }
            }
            renderFollowupsList(ticketId, container);
        }
    }

    // Formata e exibe a data da última interação no card
    function updateLastInteractionUI(ticketId, dateStr) {
        const elInteraction = document.getElementById(`last-interaction-${ticketId}`);
        if (elInteraction) {
            if (dateStr) {
                elInteraction.textContent = dateStr;
            } else {
                // Fallback: pega a data de abertura do chamado limpa (DD/MM/AAAA)
                const ticket = state.tickets.find(t => t.id === ticketId);
                if (ticket && ticket.date) {
                    const match = ticket.date.match(/^(\d{2}[-/]\d{2}[-/]\d{4})|(\d{4}[-/]\d{2}[-/]\d{2})/);
                    if (match) {
                        const dateOnly = match[0].replace(/-/g, '/');
                        const parts = dateOnly.split('/');
                        if (parts[0].length === 4) {
                            elInteraction.textContent = `${parts[2]}/${parts[1]}/${parts[0]}`;
                        } else {
                            elInteraction.textContent = dateOnly;
                        }
                    } else {
                        elInteraction.textContent = ticket.date.split(' ')[0];
                    }
                } else {
                    elInteraction.textContent = 'Sem interação';
                }
            }
        }
    }

    // Renderiza a lista de balões de comentários
    // Filtra termos redundantes de 'mais detalhes' que o GLPI insere
    function renderFollowupsList(ticketId, container) {
        const data = detailsCache[ticketId];
        if (!data || !data.followups || data.followups.length === 0) {
            container.innerHTML = `<div class="followup-item" style="color: var(--text-muted); font-style: italic; text-align: center; font-size: 11.5px;">Nenhum acompanhamento registrado para este chamado.</div>`;
            return;
        }

        let html = '';
        data.followups.forEach(f => {
            html += `
                <div class="followup-item">
                    <div class="followup-header">
                        <span class="followup-author">${escapeHtml(f.author)}</span>
                        <span class="followup-date">${escapeHtml(f.date)}</span>
                    </div>
                    <div class="followup-content">${escapeHtml(f.content)}</div>
                </div>
            `;
        });
        container.innerHTML = html;
    }

    // Inicia a fila sequencial de hidratação em segundo plano das datas
    function startBackgroundHydration() {
        hydrationQueue = state.filteredTickets.map(t => t.id);
        
        // Render from cache for any items already cached
        state.filteredTickets.forEach(t => {
            if (detailsCache[t.id]) {
                updateLastInteractionUI(t.id, detailsCache[t.id].last_interaction);
                renderTasksUI(t.id, detailsCache[t.id].tasks);
            }
        });

        if (!isHydrating) {
            processNextHydration();
        }
    }

    // Processa recursivamente o próximo item da fila com delay
    async function processNextHydration() {
        if (hydrationQueue.length === 0) {
            isHydrating = false;
            return;
        }
        isHydrating = true;
        const ticketId = hydrationQueue.shift();

        // Só faz o fetch se não estiver em cache
        if (!detailsCache[ticketId]) {
            try {
                const res = await fetch(`/api/tickets/${ticketId}/details`);
                if (res.ok) {
                    const data = await res.json();
                    detailsCache[ticketId] = data;
                    updateLastInteractionUI(ticketId, data.last_interaction);
                    renderTasksUI(ticketId, data.tasks);
                }
            } catch (err) {
                console.error(`Erro ao carregar detalhes em background: ${ticketId}`, err);
            }
            // Delay de 800ms para evitar sobrecarga no servidor do GLPI
            setTimeout(processNextHydration, 800);
        } else {
            // Atualiza instantaneamente a partir do cache e vai para o próximo
            updateLastInteractionUI(ticketId, detailsCache[ticketId].last_interaction);
            renderTasksUI(ticketId, detailsCache[ticketId].tasks);
            processNextHydration();
        }
    }

    // Renderiza a checklist de tarefas no card
    function renderTasksUI(ticketId, tasks) {
        const section = document.getElementById(`tasks-section-${ticketId}`);
        const container = document.getElementById(`tasks-container-${ticketId}`);
        if (!section || !container) return;

        if (!tasks || tasks.length === 0) {
            section.style.display = 'none';
            return;
        }

        section.style.display = 'flex';

        let html = '';
        tasks.forEach(task => {
            const isDone = task.state === 'done';
            const checkedAttr = isDone ? 'checked' : '';
            const completedClass = isDone ? 'completed' : '';
            
            html += `
                <div class="task-item ${completedClass}" data-task-id="${task.id}" id="task-item-${ticketId}-${task.id}">
                    <label class="task-label">
                        <input type="checkbox" class="task-checkbox" data-ticket-id="${ticketId}" data-task-id="${task.id}" ${checkedAttr}>
                        <span class="task-checkbox-custom"></span>
                        <span class="task-text">${escapeHtml(task.content)}</span>
                    </label>
                    <span class="task-meta">${escapeHtml(task.author)} - ${escapeHtml(task.date)}</span>
                </div>
            `;
        });
        container.innerHTML = html;
        
        container.querySelectorAll('.task-checkbox').forEach(cb => {
            cb.addEventListener('change', handleTaskToggle);
        });
    }

    // Gerencia o clique no checkbox de tarefas e sincroniza com o backend
    async function handleTaskToggle(e) {
        const cb = e.target;
        const ticketId = cb.getAttribute('data-ticket-id');
        const taskId = cb.getAttribute('data-task-id');
        const taskItem = document.getElementById(`task-item-${ticketId}-${taskId}`);
        const checked = cb.checked;

        cb.disabled = true;
        if (taskItem) {
            taskItem.classList.add('loading');
        }

        try {
            const res = await fetch(`/api/tickets/${ticketId}/tasks/${taskId}/toggle`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (res.status === 401) {
                showLogin();
                return;
            }

            if (!res.ok) {
                const data = await res.json();
                throw new Error(data.error || 'Erro ao alterar estado da tarefa');
            }

            const data = await res.json();
            const newState = data.new_state;
            
            cb.checked = (newState === 'done');
            if (taskItem) {
                if (newState === 'done') {
                    taskItem.classList.add('completed');
                } else {
                    taskItem.classList.remove('completed');
                }
            }

            if (detailsCache[ticketId] && detailsCache[ticketId].tasks) {
                const task = detailsCache[ticketId].tasks.find(t => t.id === taskId);
                if (task) {
                    task.state = newState;
                }
            }

        } catch (err) {
            console.error('Erro ao alterar estado da tarefa:', err);
            cb.checked = !checked;
            alert(err.message || 'Erro ao alterar estado da tarefa no GLPI.');
        } finally {
            cb.disabled = false;
            if (taskItem) {
                taskItem.classList.remove('loading');
            }
        }
    }

    // Sintetiza um chime sonoro premium e limpo usando a Web Audio API
    function playNotificationChime() {
        try {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            if (!AudioContext) return;
            
            const ctx = new AudioContext();
            if (ctx.state === 'suspended') {
                ctx.resume();
            }
            
            const now = ctx.currentTime;
            
            // Cria dois osciladores para gerar um intervalo harmônico brilhante (Dó5 e Mi5 com transição harmônica)
            const osc1 = ctx.createOscillator();
            const osc2 = ctx.createOscillator();
            const gainNode = ctx.createGain();
            
            osc1.type = 'sine';
            osc1.frequency.setValueAtTime(523.25, now); // C5
            osc1.frequency.exponentialRampToValueAtTime(783.99, now + 0.15); // G5 (Sol5)
            
            osc2.type = 'sine';
            osc2.frequency.setValueAtTime(659.25, now); // E5
            osc2.frequency.exponentialRampToValueAtTime(987.77, now + 0.15); // B5 (Si5)
            
            gainNode.gain.setValueAtTime(0, now);
            gainNode.gain.linearRampToValueAtTime(0.12, now + 0.03); // Ataque suave rápido
            gainNode.gain.exponentialRampToValueAtTime(0.001, now + 0.38); // Fade out longo e limpo
            
            osc1.connect(gainNode);
            osc2.connect(gainNode);
            gainNode.connect(ctx.destination);
            
            osc1.start(now);
            osc2.start(now);
            osc1.stop(now + 0.4);
            osc2.stop(now + 0.4);
        } catch (e) {
            console.warn('Bloqueio ou incompatibilidade ao reproduzir som de notificação:', e);
        }
    }

    // Verifica se existem novos chamados carregados em relação ao estado atual
    function checkForNewTickets(newTickets) {
        // Se for o carregamento inicial (estado vazio), não dispara notificações
        if (state.tickets.length === 0) {
            return;
        }

        if (state.notificationsMode === 'disabled') {
            return;
        }

        const oldIds = new Set(state.tickets.map(t => t.id));
        const newTicketsFound = newTickets.filter(t => !oldIds.has(t.id));

        if (newTicketsFound.length > 0) {
            newTicketsFound.forEach(t => {
                if (state.notificationsMode === 'all') {
                    // Solicita permissão caso ainda não tenha sido definida
                    if ('Notification' in window && Notification.permission === 'default') {
                        Notification.requestPermission();
                    }
                    triggerOSNotification(t);
                }
                showInAppToast(t);
                playNotificationChime();
            });
        }
    }

    // Dispara a notificação nativa do Sistema Operacional (Action Center / Desktop Notification)
    function triggerOSNotification(ticket) {
        if ('Notification' in window && Notification.permission === 'granted') {
            const options = {
                body: `ID: #${ticket.id}\nSolicitante: ${ticket.requester}\nCategoria: ${ticket.category}`,
                icon: 'https://glpi-project.org/wp-content/uploads/2020/07/favicon.ico',
                tag: `ticket-${ticket.id}`,
                requireInteraction: false
            };

            const notification = new Notification(`GLPI HUB - Novo Chamado: ${ticket.title.toUpperCase()}`, options);
            
            notification.onclick = function() {
                window.focus();
                const card = document.querySelector(`.ticket-card [data-id="${ticket.id}"]`)?.closest('.ticket-card');
                if (card) {
                    card.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    card.style.borderColor = 'var(--accent-purple)';
                    setTimeout(() => {
                        card.style.borderColor = '';
                    }, 3000);
                }
            };
        }
    }

    // Exibe um banner de notificação premium in-app (Toast) com transições
    function showInAppToast(ticket) {
        let container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            container.className = 'toast-container';
            document.body.appendChild(container);
        }

        const toast = document.createElement('div');
        toast.className = 'toast-notification';
        toast.innerHTML = `
            <div class="toast-icon-wrapper">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                </svg>
            </div>
            <div class="toast-content">
                <span class="toast-title">Novo Chamado #${ticket.id}</span>
                <span class="toast-body"><strong>${escapeHtml(ticket.title)}</strong><br>Solicitante: ${escapeHtml(ticket.requester)}</span>
            </div>
            <button class="btn-close-toast" title="Fechar">
                <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
            </button>
        `;

        container.appendChild(toast);

        // Transição de entrada
        setTimeout(() => {
            toast.classList.add('show');
        }, 10);

        // Foca no card correspondente ao clicar no toast
        toast.addEventListener('click', (e) => {
            if (e.target.closest('.btn-close-toast')) return;
            window.focus();
            const card = document.querySelector(`.ticket-card [data-id="${ticket.id}"]`)?.closest('.ticket-card');
            if (card) {
                card.scrollIntoView({ behavior: 'smooth', block: 'center' });
                card.style.borderColor = 'var(--accent-purple)';
                setTimeout(() => {
                    card.style.borderColor = '';
                }, 3000);
            }
        });

        // Botão de fechar manual
        const closeBtn = toast.querySelector('.btn-close-toast');
        closeBtn.addEventListener('click', () => {
            dismissToast(toast);
        });

        // Fechamento automático após 6 segundos
        setTimeout(() => {
            dismissToast(toast);
        }, 6000);
    }

    // Descarta o banner in-app executando a animação de saída
    function dismissToast(toast) {
        if (!toast.parentNode) return;
        toast.classList.remove('show');
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 400);
    }

    // Carrega as configurações a partir do Registro (via API do backend) e as aplica
    async function loadSettings() {
        try {
            const res = await fetch('/api/settings');
            if (res.ok) {
                const data = await res.json();
                
                // 1. Aplica taxa de refresh
                state.refreshRate = data.refresh_rate;
                applyAutoRefresh();

                // 2. Aplica layout
                state.layoutView = data.layout_view;
                applyCardLayout();

                // 3. Aplica tema
                state.theme = data.theme;
                applyTheme();
                
                // 4. Aplica notificações
                state.notificationsMode = data.notifications_mode || 'all';

                // 5. Se lembrar credenciais estiver ativo, preenche a tela de login
                if (data.remember_password) {
                    el.loginUsername.value = data.saved_username || '';
                    el.loginPassword.value = data.saved_password || '';
                    el.loginDomain.value = data.saved_domain || 'ldap-1';
                    const cb = document.getElementById('login-remember-password');
                    if (cb) cb.checked = true;
                }
                return;
            }
        } catch (err) {
            console.error('Erro ao obter configurações do servidor, usando localStorage fallback:', err);
        }

        // Fallback local caso a API falhe ou a conexão não esteja pronta
        const savedRate = localStorage.getItem('glpi_refresh_rate');
        state.refreshRate = savedRate ? parseInt(savedRate, 10) : 0;
        applyAutoRefresh();

        const savedLayout = localStorage.getItem('glpi_layout');
        state.layoutView = savedLayout === 'list' ? 'list' : 'grid';
        applyCardLayout();

        const savedTheme = localStorage.getItem('glpi_theme');
        state.theme = savedTheme === 'dark' ? 'dark' : 'light';
        applyTheme();

        const savedNotifications = localStorage.getItem('glpi_notifications_mode');
        state.notificationsMode = savedNotifications || 'all';
    }

    // Aplica o tema configurado no body da página
    function applyTheme() {
        document.body.setAttribute('data-theme', state.theme);
    }

    // Configura e inicia o timer de auto-refresh
    function applyAutoRefresh() {
        if (state.autoRefreshIntervalId) {
            clearInterval(state.autoRefreshIntervalId);
            state.autoRefreshIntervalId = null;
        }

        if (state.refreshRate > 0) {
            state.autoRefreshIntervalId = setInterval(() => {
                if (el.userBadge.style.display !== 'none') {
                    console.log(`[Auto-Refresh] Atualizando chamados (Intervalo: ${state.refreshRate}s)`);
                    loadTickets();
                }
            }, state.refreshRate * 1000);
        }
    }

    // Aplica a classe de layout correspondente ao contêiner de cards
    function applyCardLayout() {
        if (state.layoutView === 'list') {
            el.ticketsGridContainer.classList.add('layout-list');
        } else {
            el.ticketsGridContainer.classList.remove('layout-list');
        }
    }

    // Abre o modal de configurações e preenche os campos com os valores atuais
    function openSettings() {
        el.settingsRefreshRate.value = state.refreshRate.toString();
        
        const radios = document.getElementsByName('settings-layout');
        radios.forEach(radio => {
            radio.checked = (radio.value === state.layoutView);
        });

        const themeRadios = document.getElementsByName('settings-theme');
        themeRadios.forEach(radio => {
            radio.checked = (radio.value === state.theme);
        });

        if (el.settingsNotifications) {
            el.settingsNotifications.value = state.notificationsMode;
        }

        el.settingsOverlay.classList.add('active');
    }

    // Fecha o modal de configurações
    function closeSettings() {
        el.settingsOverlay.classList.remove('active');
    }

    // Salva as configurações selecionadas e as aplica
    async function saveSettings() {
        // Salva taxa de refresh
        const rateVal = parseInt(el.settingsRefreshRate.value, 10);
        state.refreshRate = rateVal;
        localStorage.setItem('glpi_refresh_rate', rateVal.toString());
        applyAutoRefresh();

        // Salva layout
        let layoutVal = 'grid';
        const radios = document.getElementsByName('settings-layout');
        for (const radio of radios) {
            if (radio.checked) {
                layoutVal = radio.value;
                break;
            }
        }
        state.layoutView = layoutVal;
        localStorage.setItem('glpi_layout', layoutVal);
        applyCardLayout();

        // Salva tema
        let themeVal = 'light';
        const themeRadios = document.getElementsByName('settings-theme');
        for (const radio of themeRadios) {
            if (radio.checked) {
                themeVal = radio.value;
                break;
            }
        }
        state.theme = themeVal;
        localStorage.setItem('glpi_theme', themeVal);
        applyTheme();

        // Salva notificações
        const notifVal = el.settingsNotifications ? el.settingsNotifications.value : 'all';
        state.notificationsMode = notifVal;
        localStorage.setItem('glpi_notifications_mode', notifVal);

        // Solicita permissão se alterou para 'todas'
        if (state.notificationsMode === 'all' && 'Notification' in window && Notification.permission === 'default') {
            Notification.requestPermission();
        }

        closeSettings();
        
        // Salva no Registro do Windows através da API do servidor
        try {
            await fetch('/api/settings', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    refresh_rate: state.refreshRate,
                    layout_view: state.layoutView,
                    theme: state.theme,
                    notifications_mode: state.notificationsMode,
                    remember_password: document.getElementById('login-remember-password')?.checked || false,
                    saved_username: el.loginUsername.value,
                    saved_password: el.loginPassword.value,
                    saved_domain: el.loginDomain.value
                })
            });
        } catch (err) {
            console.error('Erro ao persistir configurações no servidor:', err);
        }
        
        setSystemStatus('success', 'Configurações Salvas');
        setTimeout(() => {
            if (el.btnRefresh.disabled) {
                setSystemStatus('loading', 'Sincronizando...');
            } else {
                setSystemStatus('success', 'Sincronizado');
            }
        }, 1500);
    }

    // Inicializa a execução
    init();

})();
