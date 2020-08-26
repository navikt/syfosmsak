package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.Pasient
import no.nav.syfo.model.PdfPayload
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person

@KtorExperimentalAPI
class PdfgenClient constructor(
    private val url: String,
    private val httpClient: HttpClient
) {
    suspend fun createPdf(payload: PdfPayload): ByteArray = retry("pdfgen") {
        val httpResponse = httpClient.get<HttpStatement>(url) {
            contentType(ContentType.Application.Json)
            method = HttpMethod.Post
            body = payload
        }.execute()
        if (httpResponse.status == HttpStatusCode.OK) {
            httpResponse.call.response.receive<ByteArray>()
        } else {
            log.error("Mottok feilkode fra syfopdfgen: {}", httpResponse.status)
            throw RuntimeException("Mottok feilkode fra syfopdfgen: ${httpResponse.status}")
        }
    }
}

fun createPdfPayload(
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    person: Person
): PdfPayload = PdfPayload(
        pasient = Pasient(
                fornavn = person.personnavn.fornavn,
                mellomnavn = person.personnavn.mellomnavn,
                etternavn = person.personnavn.etternavn,
                personnummer = receivedSykmelding.personNrPasient,
                tlfNummer = receivedSykmelding.tlfPasient
        ),
        sykmelding = receivedSykmelding.sykmelding,
        validationResult = validationResult,
        mottattDato = receivedSykmelding.mottattDato,
        behandlerKontorOrgName = receivedSykmelding.legekontorOrgName
)
