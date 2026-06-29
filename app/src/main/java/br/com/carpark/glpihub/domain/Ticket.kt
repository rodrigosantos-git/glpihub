package br.com.carpark.glpihub.domain

data class Ticket(
    val id: String,
    val titulo: String,
    val entidade: String,
    val dataAbertura: String,
    val requerente: String,
    val atribuido: String,
    val categoria: String,
    val status: String,
    val description: String = "" // Descrição completa puxada do tooltip
)

data class TicketInteraction(
    val author: String,
    val date: String,
    val content: String,
    val isTask: Boolean = false,
    val taskState: String = "todo" // "todo" ou "done"
)
