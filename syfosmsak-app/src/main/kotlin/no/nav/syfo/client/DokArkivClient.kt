package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.log
import no.nav.syfo.model.AvsenderMottaker
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Bruker
import no.nav.syfo.model.Dokument
import no.nav.syfo.model.Dokumentvarianter
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Sak
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.validation.validatePersonAndDNumber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DokArkivClient(
    private val url: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun createJournalpost(
        journalpostRequest: JournalpostRequest,
        loggingMeta: LoggingMeta
    ): JournalpostResponse =
        try {
            log.info(
                "Kall til dokarkiv Nav-Callid {}, {}", journalpostRequest.eksternReferanseId,
                fields(loggingMeta)
            )
            val httpResponse = httpClient.post<HttpStatement>(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
                header("Nav-Callid", journalpostRequest.eksternReferanseId)
                body = journalpostRequest
                parameter("forsoekFerdigstill", true)
            }.execute()
            if (httpResponse.status == HttpStatusCode.Created || httpResponse.status == HttpStatusCode.Conflict) {
                httpResponse.call.response.receive<JournalpostResponse>()
            } else {
                log.error("Mottok uventet statuskode fra dokarkiv: {}, {}", httpResponse.status, fields(loggingMeta))
                throw RuntimeException("Mottok uventet statuskode fra dokarkiv: ${httpResponse.status}")
            }
        } catch (e: Exception) {
            log.warn("Oppretting av journalpost feilet: ${e.message}, {}", fields(loggingMeta))
            throw e
        }
}

fun createJournalpostPayload(
    receivedSykmelding: ReceivedSykmelding,
    caseId: String,
    pdf: ByteArray,
    validationResult: ValidationResult
) = JournalpostRequest(
    avsenderMottaker = when (validatePersonAndDNumber(receivedSykmelding.sykmelding.behandler.fnr)) {
        true -> createAvsenderMottakerValidFnr(receivedSykmelding)
        else -> createAvsenderMottakerNotValidFnr(receivedSykmelding)
    },
    bruker = Bruker(
        id = receivedSykmelding.personNrPasient,
        idType = "FNR"
    ),
    dokumenter = listOf(
        Dokument(
            dokumentvarianter = listOf(
                Dokumentvarianter(
                    filnavn = "Sykmelding",
                    filtype = "PDFA",
                    variantformat = "ARKIV",
                    fysiskDokument = pdf
                ),
                Dokumentvarianter(
                    filnavn = "Sykmelding json",
                    filtype = "JSON",
                    variantformat = "ORIGINAL",
                    fysiskDokument = objectMapper.writeValueAsBytes(receivedSykmelding.sykmelding)
                )
            ),
            tittel = createTittleJournalpost(validationResult, receivedSykmelding),
            brevkode = "NAV 08-07.04 A"
        )
    ),
    eksternReferanseId = receivedSykmelding.sykmelding.id,
    journalfoerendeEnhet = "9999",
    journalpostType = "INNGAAENDE",
    kanal = "HELSENETTET",
    sak = Sak(
        arkivsaksnummer = caseId,
        arkivsaksystem = "GSAK"
    ),
    tema = "SYM",
    tittel = createTittleJournalpost(validationResult, receivedSykmelding)
)

fun createAvsenderMottakerValidFnr(receivedSykmelding: ReceivedSykmelding): AvsenderMottaker = AvsenderMottaker(
    id = receivedSykmelding.sykmelding.behandler.fnr,
    idType = "FNR",
    land = "Norge",
    navn = receivedSykmelding.sykmelding.behandler.formatName()
)

fun createAvsenderMottakerNotValidFnr(receivedSykmelding: ReceivedSykmelding): AvsenderMottaker = AvsenderMottaker(
    land = "Norge",
    navn = receivedSykmelding.sykmelding.behandler.formatName()
)

fun createTittleJournalpost(validationResult: ValidationResult, receivedSykmelding: ReceivedSykmelding): String {
    return if (validationResult.status == Status.INVALID) {
        "Avvist Sykmelding ${getFomTomTekst(receivedSykmelding)}"
    } else if (receivedSykmelding.sykmelding.avsenderSystem.navn == "Papirsykmelding") {
        "Sykmelding mottatt på papir ${getFomTomTekst(receivedSykmelding)}"
    } else {
        "Sykmelding ${getFomTomTekst(receivedSykmelding)}"
    }
}

private fun getFomTomTekst(receivedSykmelding: ReceivedSykmelding) =
    "${formaterDato(receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeFOMDate().first().fom)} -" +
        " ${formaterDato(receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeTOMDate().last().tom)}"

fun List<Periode>.sortedSykmeldingPeriodeFOMDate(): List<Periode> =
    sortedBy { it.fom }

fun List<Periode>.sortedSykmeldingPeriodeTOMDate(): List<Periode> =
    sortedBy { it.tom }

fun Behandler.formatName(): String =
    if (mellomnavn == null) {
        "$etternavn $fornavn"
    } else {
        "$etternavn $fornavn $mellomnavn"
    }

fun formaterDato(dato: LocalDate): String {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return dato.format(formatter)
}
