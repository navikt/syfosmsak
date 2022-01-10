package no.nav.syfo.model

import java.time.ZonedDateTime

data class OpprettSak(
    val tema: String,
    val applikasjon: String,
    val aktoerId: String,
    val orgnr: String?,
    val fagsakNr: String?
)

data class SakResponse(
    val id: Long,
    val tema: String,
    val aktoerId: String,
    val orgnr: String?,
    val fagsakNr: String?,
    val applikasjon: String,
    val opprettetAv: String,
    val opprettetTidspunkt: ZonedDateTime
)
