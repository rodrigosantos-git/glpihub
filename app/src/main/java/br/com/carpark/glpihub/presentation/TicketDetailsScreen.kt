package br.com.carpark.glpihub.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.carpark.glpihub.domain.TicketInteraction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailsScreen(
    ticketId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val interactions by viewModel.ticketInteractions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(ticketId) {
        viewModel.loadTicketInteractions(ticketId)
    }

    var newMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chamado #$ticketId", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TextField(
                        value = newMessage,
                        onValueChange = { newMessage = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escreva uma mensagem...") },
                        maxLines = 4,
                        enabled = !isSending,
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = {
                            if (newMessage.isNotBlank()) {
                                isSending = true
                                viewModel.postFollowup(ticketId, newMessage) { success ->
                                    isSending = false
                                    if (success) {
                                        newMessage = ""
                                    }
                                }
                            }
                        },
                        enabled = newMessage.isNotBlank() && !isSending,
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = "Enviar", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading && interactions.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
            } else if (interactions.isEmpty() && !isLoading) {
                Text(
                    "Nenhuma interação ou acompanhamento encontrado.",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(interactions) { interaction ->
                        InteractionCard(interaction)
                    }
                }
            }
        }
    }
}

@Composable
fun InteractionCard(interaction: TicketInteraction) {
    val isTask = interaction.isTask
    val isTaskDone = interaction.taskState == "done"
    
    val cardBg = if (isTask) {
        if (isTaskDone) Color(0x1F10B981) else Color(0x1FF59E0B) // Verde claro para feita, Laranja claro para pendente
    } else {
        MaterialTheme.colorScheme.surface
    }

    val authorColor = if (interaction.author.contains("Sistema", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar Placeholder
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = interaction.author.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = interaction.author,
                        fontWeight = FontWeight.Bold,
                        color = authorColor,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = interaction.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isTask) {
                // Badge de Tarefa
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isTaskDone) Color(0xFF10B981) else Color(0xFFF59E0B),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = if (isTaskDone) "TAREFA CONCLUÍDA" else "TAREFA PENDENTE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = interaction.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
