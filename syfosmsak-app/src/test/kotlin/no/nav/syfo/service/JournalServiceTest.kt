package no.nav.syfo.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withTimeout
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.createListener
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SakResponse
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.service.onprem.JournalServiceOnPrem
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime

object JournalServiceTest : Spek({
    val producer = mockk<KafkaProducer<String, RegisterJournal>>(relaxed = true)
    val sakClient = mockk<SakClient>()
    val dokArkivClient = mockk<DokArkivClient>()
    val pdfgenClient = mockk<PdfgenClient>()
    val pdlPersonService = mockk<PdlPersonService>()
    val journalService = JournalServiceOnPrem("topic", producer, sakClient, dokArkivClient, pdfgenClient, pdlPersonService)

    val validationResult = ValidationResult(Status.OK, emptyList())
    val loggingMeta = LoggingMeta("", "", "", "")
    val journalpostId = "1234"
    val journalpostIdPapirsykmelding = "5555"

    beforeEachTest {
        coEvery { sakClient.findOrCreateSak(any(), any(), any()) } returns SakResponse(1L, "SYM", "aktørid", null, null, "FS22", "srv", ZonedDateTime.now())
        coEvery { pdlPersonService.getPdlPerson(any(), any()) } returns PdlPerson(Navn("fornavn", null, "etternavn"), "fnr", "aktørid", null)
        coEvery { pdfgenClient.createPdf(any()) } returns "PDF".toByteArray(Charsets.UTF_8)
        coEvery { dokArkivClient.createJournalpost(any(), any()) } returns JournalpostResponse(dokumenter = emptyList(), journalpostId = journalpostId, journalpostferdigstilt = true)
    }

    describe("JournalService - opprettEllerFinnPDFJournalpost") {

        it("test timeout") {
            val applicationState = ApplicationState(true, true)
            coEvery { pdfgenClient.createPdf(any()) } coAnswers {
                delay(Duration.ofMillis(10))
                "PDF".toByteArray(Charsets.UTF_8)
            }
            runBlocking {
                val job = createListener(applicationState) {
                    withTimeout(5) {
                        val sykmelding = generateReceivedSykmelding(
                            generateSykmelding(
                                avsenderSystem = AvsenderSystem(
                                    "EPJ-systemet",
                                    "1"
                                )
                            )
                        )
                        journalService.onJournalRequest(sykmelding, validationResult, loggingMeta)
                    }
                }
                job.join()
            }
            applicationState.alive shouldBeEqualTo false
            applicationState.ready shouldBeEqualTo false
        }

        it("Oppretter PDF hvis sykmeldingen ikke er en papirsykmelding") {
            val sykmelding = generateReceivedSykmelding(generateSykmelding(avsenderSystem = AvsenderSystem("EPJ-systemet", "1")))

            runBlocking {
                val opprettetJournalpostId =
                    journalService.opprettEllerFinnPDFJournalpost(sykmelding, validationResult, "1", loggingMeta)

                opprettetJournalpostId shouldBeEqualTo journalpostId
            }
        }
        it("Oppretter PDF hvis sykmeldingen er en papirsykmelding og journalpostid ikke er satt som versjonsnummer") {
            val sykmelding = generateReceivedSykmelding(generateSykmelding(avsenderSystem = AvsenderSystem("Papirsykmelding", "1")))

            runBlocking {
                val opprettetJournalpostId =
                    journalService.opprettEllerFinnPDFJournalpost(sykmelding, validationResult, "1", loggingMeta)

                opprettetJournalpostId shouldBeEqualTo journalpostId
            }
        }
        it("Oppretter ikke PDF hvis sykmeldingen er en papirsykmelding og versjonsnummer er journalpostid") {
            val sykmelding = generateReceivedSykmelding(generateSykmelding(avsenderSystem = AvsenderSystem("Papirsykmelding", journalpostIdPapirsykmelding)))

            runBlocking {
                val opprettetJournalpostId =
                    journalService.opprettEllerFinnPDFJournalpost(sykmelding, validationResult, "1", loggingMeta)

                opprettetJournalpostId shouldBeEqualTo journalpostIdPapirsykmelding
            }
        }
    }
})

fun generateReceivedSykmelding(sykmelding: Sykmelding): ReceivedSykmelding =
    ReceivedSykmelding(
        sykmelding = sykmelding, personNrPasient = "fnr", tlfPasient = null, personNrLege = "fnrLege", navLogId = "id", msgId = "msgid",
        legekontorOrgNr = null, legekontorHerId = null, legekontorReshId = null, legekontorOrgName = "Legekontoret", mottattDato = LocalDateTime.now(), rulesetVersion = "1",
        merknader = emptyList(), fellesformat = "", tssid = null, partnerreferanse = null, legeHelsepersonellkategori = null, legeHprNr = null
    )
