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
import no.nav.syfo.model.GosysVedlegg
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Sak
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.imageToPDF
import no.nav.syfo.validation.validatePersonAndDNumber
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64

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
    pdf: ByteArray,
    validationResult: ValidationResult,
    vedlegg: List<Vedlegg>
) = JournalpostRequest(
    avsenderMottaker = when (validatePersonAndDNumber(receivedSykmelding.sykmelding.behandler.fnr)) {
        true -> createAvsenderMottakerValidFnr(receivedSykmelding)
        else -> createAvsenderMottakerNotValidFnr(receivedSykmelding)
    },
    bruker = Bruker(
        id = receivedSykmelding.personNrPasient,
        idType = "FNR"
    ),
    dokumenter = leggtilDokument(
        msgId = receivedSykmelding.msgId,
        receivedSykmelding = receivedSykmelding,
        pdf = pdf,
        validationResult = validationResult,
        vedleggListe = vedlegg
    ),
    eksternReferanseId = receivedSykmelding.sykmelding.id,
    journalfoerendeEnhet = "9999",
    journalpostType = "INNGAAENDE",
    kanal = "HELSENETTET",
    sak = Sak(
        sakstype = "GENERELL_SAK"
    ),
    tema = "SYM",
    tittel = createTittleJournalpost(validationResult, receivedSykmelding)
)

fun leggtilDokument(
    msgId: String,
    receivedSykmelding: ReceivedSykmelding,
    pdf: ByteArray,
    validationResult: ValidationResult,
    vedleggListe: List<Vedlegg>?
): List<Dokument> {
    val listDokument = ArrayList<Dokument>()
    listDokument.add(
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
    )
    if (!vedleggListe.isNullOrEmpty()) {
        val listVedleggDokumenter = ArrayList<Dokument>()
        vedleggListe
            .filter { vedlegg -> vedlegg.content.content.isNotEmpty() }
            .map { vedlegg -> toGosysVedlegg(vedlegg) }
            .map { gosysVedlegg -> vedleggToPDF(gosysVedlegg) }
            .mapIndexed { index, vedlegg ->
                listVedleggDokumenter.add(
                    Dokument(
                        dokumentvarianter = listOf(
                            Dokumentvarianter(
                                filtype = findFiltype(vedlegg),
                                filnavn = "Vedlegg_nr_${index}_Sykmelding_$msgId",
                                variantformat = "ARKIV",
                                fysiskDokument = vedlegg.content
                            )
                        ),
                        tittel = "Vedlegg til sykmelding ${getFomTomTekst(receivedSykmelding)}"
                    )
                )
            }
        listVedleggDokumenter.map { vedlegg ->
            listDokument.add(vedlegg)
        }
    }
    return listDokument
}
fun toGosysVedlegg(vedlegg: Vedlegg): GosysVedlegg {
    return GosysVedlegg(
        contentType = vedlegg.type,
        content = Base64.getMimeDecoder().decode(vedlegg.content.content),
        description = vedlegg.description
    )
}

fun vedleggToPDF(vedlegg: GosysVedlegg): GosysVedlegg {
    if (findFiltype(vedlegg) == "PDFA") return vedlegg
    log.info("Converting vedlegg of type ${vedlegg.contentType} to PDFA")

    val image =
        ByteArrayOutputStream().use { outputStream ->
            imageToPDF(vedlegg.content.inputStream(), outputStream)
            outputStream.toByteArray()
        }

    return GosysVedlegg(
        content = image,
        contentType = "application/pdf",
        description = vedlegg.description
    )
}

fun findFiltype(vedlegg: GosysVedlegg): String =
    when (vedlegg.contentType) {
        "application/pdf" -> "PDFA"
        "image/tiff" -> "TIFF"
        "image/png" -> "PNG"
        "image/jpeg" -> "JPEG"
        else -> throw RuntimeException("Vedlegget er av av ukjent mimeType ${vedlegg.contentType}")
    }

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
