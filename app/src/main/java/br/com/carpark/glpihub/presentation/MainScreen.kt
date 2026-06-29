package br.com.carpark.glpihub.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.carpark.glpihub.domain.Ticket

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    onTicketClick: (String) -> Unit // Navegação para detalhes
) {
    val tickets by viewModel.tickets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState()
    var showSettingsSheet by remember { mutableStateOf(false) }
    
    val solutionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTicketIdForSolution by remember { mutableStateOf<String?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadTickets()
    }

    val savedUsername by viewModel.savedUsername.collectAsState()
    val filterAssignee by viewModel.filterAssignee.collectAsState()
    val filterCategory by viewModel.filterCategory.collectAsState()
    val filterRequerente by viewModel.filterRequerente.collectAsState()
    val filterEntidade by viewModel.filterEntidade.collectAsState()

    val filteredTickets = tickets.filter { ticket ->
        val matchesSearch = if (searchQuery.isEmpty()) true else {
            ticket.titulo.lowercase().contains(searchQuery.lowercase()) || 
            ticket.id.lowercase().contains(searchQuery.lowercase()) || 
            ticket.requerente.lowercase().contains(searchQuery.lowercase())
        }
        
        val assigneeName = filterAssignee.substringBefore(" (").trim()
        val matchesAssignee = if (assigneeName.isEmpty() || assigneeName == "Nenhum") true else ticket.atribuido.lowercase().contains(assigneeName.lowercase())
        
        // Categoria no ticket pode ser menor (ex: "SISTEMA EPARK") enquanto o filtro é "01 - TI > 04- SISTEMA EPARK"
        val cleanFilterCategory = filterCategory.substringAfter(">").substringAfter("-").trim()
        val matchesCategory = if (filterCategory.isEmpty() || filterCategory == "Nenhuma") true else ticket.categoria.lowercase().contains(cleanFilterCategory.lowercase()) || cleanFilterCategory.lowercase().contains(ticket.categoria.lowercase())
        
        val matchesRequerente = if (filterRequerente.isEmpty()) true else ticket.requerente.lowercase().contains(filterRequerente.lowercase())
        val matchesEntidade = if (filterEntidade.isEmpty()) true else ticket.entidade.lowercase().contains(filterEntidade.lowercase())
        
        matchesSearch && matchesAssignee && matchesCategory && matchesRequerente && matchesEntidade
    }

    // KPIs dinâmicos atualizados
    val totalTickets = tickets.size
    
    val myName = (savedUsername ?: "").replace(".", " ")
    val assignedToMeTickets = tickets.count { myName.isNotEmpty() && it.atribuido.contains(myName, ignoreCase = true) }
    
    val filteredTicketsCount = filteredTickets.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GLPI HUB", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = { viewModel.loadTickets() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.FilterAlt, contentDescription = "Filtros", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)) {
            
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 8.dp),
                placeholder = { Text("Pesquisar por ID, assunto, técnico...", fontSize = 14.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                ),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) })
            )

            // KPI Row
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard(title = "TOTAL", value = totalTickets.toString(), color = androidx.compose.ui.graphics.Color(0xFF6366F1), modifier = Modifier.weight(1f))
                KpiCard(title = "ATRIB. A MIM", value = assignedToMeTickets.toString(), color = androidx.compose.ui.graphics.Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                KpiCard(title = "PESQUISA", value = filteredTicketsCount.toString(), color = androidx.compose.ui.graphics.Color(0xFF10B981), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (filteredTickets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum chamado encontrado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTickets) { ticket ->
                        TicketCard(
                            ticket = ticket, 
                            onClickAcompanhamento = { onTicketClick(ticket.id) },
                            onClickAtribuir = { /* TODO */ },
                            onClickSolucao = { selectedTicketIdForSolution = ticket.id }
                        )
                    }
                }
            }
        }
    }

    if (showBottomSheet) {
        FilterBottomSheet(
            onDismiss = { showBottomSheet = false },
            sheetState = sheetState,
            initialAssignee = filterAssignee,
            initialCategory = filterCategory,
            initialRequerente = filterRequerente,
            initialEntidade = filterEntidade,
            onApplyFilters = { assignee, category, req, ent ->
                viewModel.saveFilters(assignee, category, req, ent)
                showBottomSheet = false
            }
        )
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            onDismiss = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            viewModel = viewModel,
            onLogout = {
                showSettingsSheet = false
                viewModel.logout()
                onLogout()
            }
        )
    }

    if (selectedTicketIdForSolution != null) {
        SolutionBottomSheet(
            ticketId = selectedTicketIdForSolution!!,
            onDismiss = { selectedTicketIdForSolution = null },
            sheetState = solutionSheetState,
            onSolucionar = { modelo, tipo, desc ->
                viewModel.addSolution(selectedTicketIdForSolution!!, modelo, tipo, desc) { success ->
                    if (success) {
                        selectedTicketIdForSolution = null
                    }
                }
            }
        )
    }
}

@Composable
fun KpiCard(title: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, fontSize = 10.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 24.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TicketCard(
    ticket: Ticket, 
    onClickAcompanhamento: () -> Unit,
    onClickAtribuir: () -> Unit,
    onClickSolucao: () -> Unit
) {
    val isSolved = ticket.status.contains("solucionado", true) || ticket.status.contains("fechado", true)
    val isNew = ticket.status.contains("novo", true)
    val isPending = ticket.status.contains("pendente", true)
    
    val (statusBg, statusTxt) = when {
        isNew -> androidx.compose.ui.graphics.Color(0x228B5CF6) to androidx.compose.ui.graphics.Color(0xFF6D28D9)
        isSolved -> androidx.compose.ui.graphics.Color(0x2210B981) to androidx.compose.ui.graphics.Color(0xFF047857)
        isPending -> androidx.compose.ui.graphics.Color(0x22F59E0B) to androidx.compose.ui.graphics.Color(0xFFB45309)
        else -> androidx.compose.ui.graphics.Color(0x223B82F6) to androidx.compose.ui.graphics.Color(0xFF1D4ED8)
    }

    val catColor = when {
        ticket.categoria.contains("SUPORTE GERAL", true) -> androidx.compose.ui.graphics.Color(0xFF3B82F6)
        ticket.categoria.contains("EPARK", true) -> androidx.compose.ui.graphics.Color(0xFF8B5CF6)
        ticket.categoria.contains("CANCELA", true) -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
        ticket.categoria.contains("PLANTÃO", true) -> androidx.compose.ui.graphics.Color(0xFFEF4444)
        ticket.categoria.contains("COMPRAS", true) -> androidx.compose.ui.graphics.Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.primary
    }

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Linha lateral indicadora de cor baseada na categoria
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(catColor))
            
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "ID: ${ticket.id}", fontWeight = FontWeight.ExtraBold, fontSize = 16.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }, color = MaterialTheme.colorScheme.onSurface)
                    
                    // Status Badge (Pill)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusBg,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = ticket.status.uppercase(),
                            color = statusTxt,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                val tituloLimpo = ticket.titulo.replace(Regex("\\s*\\(\\d+\\)$"), "").trim()
                Text(
                    text = tituloLimpo.uppercase(), 
                    fontWeight = FontWeight.Bold, 
                    style = MaterialTheme.typography.titleMedium, 
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                
                if (ticket.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = ticket.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("REQUERENTE", fontSize = 10.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        Text(ticket.requerente, fontSize = 13.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ATRIBUÍDO", fontSize = 10.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        Text(ticket.atribuido, fontSize = 13.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("CATEGORIA", fontSize = 10.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = catColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        ticket.categoria,
                        fontSize = 11.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) },
                        fontWeight = FontWeight.Bold,
                        color = catColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))
                
                // Botões de Ação
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onClickAtribuir, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Assignment, contentDescription = "Atribuir a mim", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Atribuir", fontSize = 12.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) })
                    }
                    TextButton(onClick = onClickSolucao, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Adicionar Solução", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Solução", fontSize = 12.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) })
                    }
                    TextButton(onClick = onClickAcompanhamento, modifier = Modifier.weight(1.2f)) {
                        Icon(Icons.Default.Message, contentDescription = "Acompanhamentos", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Acompanhar", fontSize = 12.dp.value.toInt().dp.value.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }, maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    initialAssignee: String,
    initialCategory: String,
    initialRequerente: String,
    initialEntidade: String,
    onApplyFilters: (String, String, String, String) -> Unit
) {
    val categories = listOf(
        "01 - TI > 01 - SUPORTE GERAL",
        "01 - TI > 04- SISTEMA EPARK",
        "01 - TI > 05- SUPORTE CANCELA",
        "01 - TI > 09- PLANTÃO FDS",
        "06 - TI - COMPRAS"
    )

    val assignees = listOf(
        "ALEX FRANCISCO (36)",
        "RODRIGO SANTOS (164)",
        "CAIO VIEIRA (197)",
        "JOABSON BARRETO DE OLIVEIRA (274)",
        "EDSON DA FONSECA LIVRAMENTO (219)"
    )

    var selectedAssignee by remember { mutableStateOf(initialAssignee) }
    var assigneeExpanded by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var categoryExpanded by remember { mutableStateOf(false) }

    var requerente by remember { mutableStateOf(initialRequerente) }
    var entidade by remember { mutableStateOf(initialEntidade) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets.ime
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Filtros", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Dropdown de Técnicos
            ExposedDropdownMenuBox(
                expanded = assigneeExpanded,
                onExpandedChange = { assigneeExpanded = !assigneeExpanded }
            ) {
                OutlinedTextField(
                    value = selectedAssignee,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Atribuído ao Técnico") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = assigneeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = assigneeExpanded,
                    onDismissRequest = { assigneeExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("Nenhum") }, onClick = { selectedAssignee = ""; assigneeExpanded = false })
                    assignees.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item) },
                            onClick = {
                                selectedAssignee = item
                                assigneeExpanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Dropdown de Categorias
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoria") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("Nenhuma") }, onClick = { selectedCategory = ""; categoryExpanded = false })
                    categories.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item) },
                            onClick = {
                                selectedCategory = item
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = requerente,
                onValueChange = { requerente = it },
                label = { Text("Requerente") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = entidade,
                onValueChange = { entidade = it },
                label = { Text("Entidade") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onApplyFilters(selectedAssignee, selectedCategory, requerente, entidade) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Aplicar Filtros")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    viewModel: MainViewModel,
    onLogout: () -> Unit
) {
    val aiApiKey by viewModel.aiApiKey.collectAsState()
    val themeType by viewModel.themeType.collectAsState()

    var apiKeyInput by remember { mutableStateOf(aiApiKey) }

    // Sincroniza o input local se o estado mudar externamente
    LaunchedEffect(aiApiKey) {
        apiKeyInput = aiApiKey
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets.ime
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Ajustes do Aplicativo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // Campo de API Key
            Text("Inteligência Artificial (IA)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Chave API ChatGPT/OpenAI") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.saveAiApiKey(apiKeyInput) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Salvar")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // Configuração de Temas
            Text("Tema do Aplicativo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            val themes = listOf(
                "light" to "Claro",
                "dark" to "Escuro",
                "monokai" to "Monokai",
                "cyberpunk" to "Cyberpunk"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themes.forEach { (type, name) ->
                    val isSelected = themeType == type
                    val border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clickable { viewModel.saveThemeType(type) },
                        border = border,
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (type) {
                                "light" -> Color(0xFFF1F5F9)
                                "dark" -> Color(0xFF1E293B)
                                "monokai" -> Color(0xFF272822)
                                "cyberpunk" -> Color(0xFF0C0817)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (type) {
                                    "light" -> Color.Black
                                    "dark" -> Color.White
                                    "monokai" -> Color(0xFFF8F8F2)
                                    "cyberpunk" -> Color(0xFFF2E307)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(20.dp))

            // Botão Sair da Conta
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("SAIR DA CONTA", fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SolutionBottomSheet(
    ticketId: String,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState,
    onSolucionar: (String, String, String) -> Unit
) {
    var modeloExpanded by remember { mutableStateOf(false) }
    var selectedModelo by remember { mutableStateOf("") }
    val modelos = listOf(
        "CAR PARK",
        "01 - ACESSO REMOTO (3)",
        "02 - VISITA TECNICA CARPARK (2)",
        "03 - RESOLVIDO (1)",
        "04 - CUSTOMIZACAO (5)",
        "05 - TREINAMENTO (4)",
        "06 - CANCELADO (6)"
    )

    var tipoExpanded by remember { mutableStateOf(false) }
    var selectedTipo by remember { mutableStateOf("") }
    val tipos = listOf(
        "01 - OK REMOTAMENTE (4)",
        "02 - OK INLOCO (1)",
        "03 - OK SEM INTERVENCAO TECNICA (5)",
        "03 - OK VIA PRESTADOR DE SERVICO (11)",
        "04 - NAO RESOLVIDO (3)",
        "05 - CANCELADO (2)",
        "06 - PLANTAO TECNICO (12)",
        "CUSTOMIZACAO (7)",
        "DESENVOLVIMENTO (8)",
        "RESTART APLICACAO (10)",
        "TREINAMENTO (6)"
    )

    var descricao by remember { mutableStateOf("") }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = androidx.compose.foundation.layout.WindowInsets.ime
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Novo item - Solução", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Chamado ID: $ticketId", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))

            // Dropdown: Modelo de Solução
            androidx.compose.material3.ExposedDropdownMenuBox(
                expanded = modeloExpanded,
                onExpandedChange = { modeloExpanded = !modeloExpanded }
            ) {
                OutlinedTextField(
                    value = selectedModelo,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Modelo de solução") },
                    trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeloExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = modeloExpanded,
                    onDismissRequest = { modeloExpanded = false }
                ) {
                    modelos.forEach { item ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(item) },
                            onClick = {
                                selectedModelo = item
                                modeloExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Dropdown: Tipo da Solução
            androidx.compose.material3.ExposedDropdownMenuBox(
                expanded = tipoExpanded,
                onExpandedChange = { tipoExpanded = !tipoExpanded }
            ) {
                OutlinedTextField(
                    value = selectedTipo,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tipo da solução") },
                    trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = tipoExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = tipoExpanded,
                    onDismissRequest = { tipoExpanded = false }
                ) {
                    tipos.forEach { item ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(item) },
                            onClick = {
                                selectedTipo = item
                                tipoExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Text Area: Descrição
            OutlinedTextField(
                value = descricao,
                onValueChange = { descricao = it },
                label = { Text("Descrição") },
                minLines = 5,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Botão Adicionar
            Button(
                onClick = { onSolucionar(selectedModelo, selectedTipo, descricao) },
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFF59E0B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Adicionar Solução", fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
