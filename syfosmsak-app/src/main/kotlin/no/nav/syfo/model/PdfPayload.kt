package no.nav.syfo.model

import java.time.LocalDateTime

data class PdfPayload(
    val pasient: Pasient,
    val sykmelding: Sykmelding,
    val validationResult: ValidationResult,
    val mottattDato: LocalDateTime,
    val behandlerKontorOrgName: String,
    val merknader: List<Merknad>?,
    val rulesetVersion: String?
)

data class Pasient(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val personnummer: String,
    val tlfNummer: String?
)
