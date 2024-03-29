package no.nav.syfo.service

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withTimeout
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.createListener
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Content
import no.nav.syfo.model.JournalKafkaMessage
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@DelicateCoroutinesApi
class JournalServiceTest {
    val producer = mockk<KafkaProducer<String, JournalKafkaMessage>>(relaxed = true)
    val dokArkivClient = mockk<DokArkivClient>()
    val pdfgenClient = mockk<PdfgenClient>()
    val pdlPersonService = mockk<PdlPersonService>()
    val bucketService = mockk<BucketService>()
    val journalService =
        JournalService(
            "topic",
            producer,
            dokArkivClient,
            pdfgenClient,
            pdlPersonService,
            bucketService
        )

    val validationResult = ValidationResult(Status.OK, emptyList())
    val loggingMeta = LoggingMeta("", "", "", "")
    val journalpostId = "1234"
    val journalpostIdPapirsykmelding = "5555"
    val journalpostIdUtenlandskSykmelding = "7894"

    @BeforeEach
    internal fun `Set up`() {
        clearMocks(dokArkivClient)
        coEvery { pdlPersonService.getPdlPerson(any(), any()) } returns
            PdlPerson(
                Navn("fornavn", null, "etternavn"),
                "fnr",
                "aktørid",
                null,
            )
        coEvery { pdfgenClient.createPdf(any()) } returns "PDF".toByteArray(Charsets.UTF_8)
        coEvery { dokArkivClient.createJournalpost(any(), any()) } returns
            JournalpostResponse(
                dokumenter = emptyList(),
                journalpostId = journalpostId,
                journalpostferdigstilt = true,
            )
    }

    @Test
    internal fun `opprettEllerFinnPDFJournalpost test timeout`() {
        val applicationState = ApplicationState(true, true)
        coEvery { pdfgenClient.createPdf(any()) } coAnswers
            {
                delay(Duration.ofMillis(10))
                "PDF".toByteArray(Charsets.UTF_8)
            }
        val job =
            createListener(applicationState) {
                withTimeout(5) {
                    val sykmelding =
                        generateReceivedSykmelding(
                            generateSykmelding(
                                avsenderSystem =
                                    AvsenderSystem(
                                        "EPJ-systemet",
                                        "1",
                                    ),
                            ),
                        )
                    journalService.onJournalRequest(sykmelding, validationResult, loggingMeta)
                }
            }
        runBlocking {
            job.join()

            Assertions.assertEquals(false, applicationState.alive)
            Assertions.assertEquals(false, applicationState.ready)
        }
    }

    @Test
    internal fun `Oppretter PDF hvis sykmeldingen ikke er en papirsykmelding`() {
        val sykmelding =
            generateReceivedSykmelding(
                generateSykmelding(avsenderSystem = AvsenderSystem("EPJ-systemet", "1"))
            )

        runBlocking {
            val opprettetJournalpostId =
                journalService.opprettEllerFinnPDFJournalpost(
                    sykmelding,
                    validationResult,
                    loggingMeta
                )

            Assertions.assertEquals(journalpostId, opprettetJournalpostId)
        }
    }

    @Test
    internal fun `Oppretter PDF hvis sykmeldingen er en papirsykmelding og journalpostid ikke er satt som versjonsnummer`() {
        val sykmelding =
            generateReceivedSykmelding(
                generateSykmelding(avsenderSystem = AvsenderSystem("Papirsykmelding", "1"))
            )

        runBlocking {
            val opprettetJournalpostId =
                journalService.opprettEllerFinnPDFJournalpost(
                    sykmelding,
                    validationResult,
                    loggingMeta
                )

            Assertions.assertEquals(journalpostId, opprettetJournalpostId)
        }
    }

    @Test
    internal fun `Oppretter ikke PDF hvis sykmeldingen er en papirsykmelding og versjonsnummer er journalpostid`() {
        val sykmelding =
            generateReceivedSykmelding(
                generateSykmelding(
                    avsenderSystem =
                        AvsenderSystem(
                            "Papirsykmelding",
                            journalpostIdPapirsykmelding,
                        ),
                ),
            )
        runBlocking {
            val opprettetJournalpostId =
                journalService.opprettEllerFinnPDFJournalpost(
                    sykmelding,
                    validationResult,
                    loggingMeta
                )

            Assertions.assertEquals(journalpostIdPapirsykmelding, opprettetJournalpostId)
        }
    }

    @Test
    internal fun `Oppretter ikke PDF hvis sykmeldingen er utenlandsk og versjonsnummer er journalpostid`() {
        val sykmelding =
            generateReceivedSykmelding(
                    generateSykmelding(
                        avsenderSystem =
                            AvsenderSystem(
                                "syk-dig",
                                journalpostIdUtenlandskSykmelding,
                            ),
                    ),
                )
                .copy(utenlandskSykmelding = UtenlandskSykmelding("GER", false))
        runBlocking {
            val opprettetJournalpostId =
                journalService.opprettEllerFinnPDFJournalpost(
                    sykmelding,
                    validationResult,
                    loggingMeta
                )

            Assertions.assertEquals(journalpostIdUtenlandskSykmelding, opprettetJournalpostId)
        }
    }

    @Test
    internal fun `Journalforer vedlegg hvis sykmelding inneholder vedlegg`() {
        coEvery { bucketService.getVedleggFromBucket(any(), any()) } returns
            Vedlegg(
                Content(
                    "Base64Container",
                    "base64",
                ),
                "application/pdf",
                "vedlegg2.pdf",
            )
        val sykmelding =
            generateReceivedSykmelding(generateSykmelding()).copy(vedlegg = listOf("vedleggsid1"))

        runBlocking {
            val opprettetJournalpostId =
                journalService.opprettEllerFinnPDFJournalpost(
                    sykmelding,
                    validationResult,
                    loggingMeta
                )

            Assertions.assertEquals(journalpostId, opprettetJournalpostId)
            coVerify { dokArkivClient.createJournalpost(match { it.dokumenter.size == 2 }, any()) }
        }
    }
}

fun generateReceivedSykmelding(sykmelding: Sykmelding): ReceivedSykmelding =
    ReceivedSykmelding(
        sykmelding = sykmelding,
        personNrPasient = "fnr",
        tlfPasient = null,
        personNrLege = "fnrLege",
        navLogId = "id",
        msgId = "msgid",
        legekontorOrgNr = null,
        legekontorHerId = null,
        legekontorReshId = null,
        legekontorOrgName = "Legekontoret",
        mottattDato = LocalDateTime.now(),
        rulesetVersion = "1",
        merknader = emptyList(),
        fellesformat = "",
        tssid = null,
        partnerreferanse = null,
        legeHelsepersonellkategori = null,
        legeHprNr = null,
        vedlegg = null,
        utenlandskSykmelding = null,
    )
