package no.nav.syfo

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.client.createPdfPayload
import no.nav.syfo.model.PdfPayload
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import java.time.LocalDateTime

fun main() {
    val sykmelding: Sykmelding = objectMapper.readValue(PdfPayload::class.java.getResourceAsStream("/sm.json"))
    val receivedSykmelding = ReceivedSykmelding(
        sykmelding,
        personNrPasient = "123456789",
        tlfPasient = "98765432",
        personNrLege = "123456789",
        legeHelsepersonellkategori = null,
        legeHprNr = null,
        navLogId = "abcdef",
        msgId = "abcdef",
        legekontorOrgNr = "98765432",
        legekontorHerId = "123456789",
        legekontorReshId = "546372819",
        legekontorOrgName = "Legekontor AS",
        mottattDato = LocalDateTime.now(),
        rulesetVersion = "1",
        merknader = null,
        partnerreferanse = "",
        fellesformat = "",
        tssid = null,
        vedlegg = null
    )
    val validationResult = ValidationResult(
        status = Status.MANUAL_PROCESSING,
        ruleHits = listOf(
            RuleInfo(
                ruleName = "BEHANDLER_KI_NOT_USING_VALID_DIAGNOSECODE_TYPE",
                messageForUser = "Den som skrev sykmeldingen mangler autorisasjon.",
                messageForSender = "Behandler er manuellterapeut/kiropraktor eller fysioterapeut med autorisasjon har angitt annen diagnose enn kapitel L (muskel og skjelettsykdommer)",
                ruleStatus = Status.MANUAL_PROCESSING
            ),
            RuleInfo(
                ruleName = "NUMBER_OF_TREATMENT_DAYS_SET",
                messageForUser = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                messageForSender = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                ruleStatus = Status.MANUAL_PROCESSING
            )
        )
    )
    val person = PdlPerson(
        navn = Navn("Fornavn", "Mellomnavnsen", "Etternavn"),
        fnr = "123456789",
        aktorId = null,
        adressebeskyttelse = null
    )
    val pdfPayload = createPdfPayload(receivedSykmelding, validationResult, person)
    val json = objectMapper.writeValueAsString(pdfPayload)
    println(json)
}
