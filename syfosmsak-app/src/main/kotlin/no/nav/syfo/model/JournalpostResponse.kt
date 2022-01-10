package no.nav.syfo.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JournalpostResponse(
    val dokumenter: List<DokumentInfo>,
    val journalpostId: String,
    val journalpostferdigstilt: Boolean,
    val journalstatus: String? = null,
    val melding: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DokumentInfo(
    val brevkode: String? = null,
    val dokumentInfoId: String? = null,
    val tittel: String? = null
)
