package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.syfo.log
import no.nav.syfo.model.Pasient
import no.nav.syfo.model.PdfPayload
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.model.PdlPerson

class PdfgenClient
constructor(
    private val url: String,
    private val httpClient: HttpClient,
) {
    suspend fun createPdf(payload: PdfPayload): ByteArray {
        val httpResponse =
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
        if (httpResponse.status == HttpStatusCode.OK) {
            return httpResponse.call.response.body()
        } else {
            log.error(
                "Mottok feilkode fra smpdfgen: {} for sykmeldingID {}",
                httpResponse.status,
                payload.sykmelding.id,
            )
            throw RuntimeException(
                "Mottok feilkode fra smpdfgen: ${httpResponse.status} for sykmeldingID ${payload.sykmelding.id}"
            )
        }
    }
}

fun createPdfPayload(
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    person: PdlPerson,
): PdfPayload =
    PdfPayload(
        pasient =
            Pasient(
                fornavn = person.navn.fornavn,
                mellomnavn = person.navn.mellomnavn,
                etternavn = person.navn.etternavn,
                personnummer = receivedSykmelding.personNrPasient,
                tlfNummer = receivedSykmelding.tlfPasient,
            ),
        sykmelding = mapToSykmeldingUtenUlovligeTegn(receivedSykmelding.sykmelding),
        validationResult = validationResult,
        mottattDato = receivedSykmelding.mottattDato,
        behandlerKontorOrgName = receivedSykmelding.legekontorOrgName,
        merknader = receivedSykmelding.merknader,
        rulesetVersion = receivedSykmelding.rulesetVersion,
        signerendBehandlerHprNr = receivedSykmelding.legeHprNr,
    )

fun mapToSykmeldingUtenUlovligeTegn(sykmelding: Sykmelding): Sykmelding {
    val sykmeldingSomString = objectMapper.writeValueAsString(sykmelding)
    val sykmeldingSomStringUtenUlovligeTegn =
        sykmeldingSomString.replace(regex = Regex("\\p{C}"), "")
    return objectMapper.readValue(sykmeldingSomStringUtenUlovligeTegn, Sykmelding::class.java)
}
