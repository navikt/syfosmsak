package no.nav.syfo.model

data class JournalKafkaMessage(
    val messageId: String,
    val journalpostId: String,
    val journalpostKilde: String
)
