package no.nav.syfo.pdl.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentIdenter
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.Navn
import no.nav.syfo.pdl.client.model.PdlIdent
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.getPdlResponse
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import kotlin.test.assertFailsWith

class PdlPersonServiceTest : FunSpec({
    val pdlClient = mockk<PdlClient>()
    val accessTokenClient = mockkClass(AccessTokenClientV2::class)
    val pdlService = PdlPersonService(pdlClient, accessTokenClient, "pdlscope")
    val loggingMeta = LoggingMeta("mottakId", "orgNr", "msgId", "sykmeldingId")

    beforeTest {
        clearAllMocks()
    }

    context("PdlService") {
        test("Hent person fra pdl uten fortrolig adresse") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse()
            coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

            val person = pdlService.getPdlPerson("01245678901", loggingMeta)
            person.navn.fornavn shouldBeEqualTo "fornavn"
            person.navn.mellomnavn shouldBeEqualTo null
            person.navn.etternavn shouldBeEqualTo "etternavn"
            person.aktorId shouldBeEqualTo "987654321"
        }
        test("Skal feile når person ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(null, null), errors = null)

            assertFailsWith<RuntimeException> {
                runBlocking {
                    pdlService.getPdlPerson("123", loggingMeta)
                }
            }
        }
        test("Skal feile når navn er tom liste") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = emptyList(), adressebeskyttelse = null
                    ),
                    hentIdenter = HentIdenter(emptyList())
                ),
                errors = null
            )

            assertFailsWith<RuntimeException> {
                runBlocking {
                    pdlService.getPdlPerson("123", loggingMeta)
                }
            }
        }
        test("Skal feile når navn ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = null, adressebeskyttelse = null
                    ),
                    hentIdenter = HentIdenter(listOf(PdlIdent(ident = "987654321", gruppe = "foo")))
                ),
                errors = null
            )

            assertFailsWith<RuntimeException> {
                runBlocking {
                    pdlService.getPdlPerson("123", loggingMeta)
                }
            }
        }
        test("Skal feile når identer ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = listOf(Navn("fornavn", "mellomnavn", "etternavn")),
                        adressebeskyttelse = null
                    ),
                    hentIdenter = HentIdenter(emptyList())
                ),
                errors = null
            )

            assertFailsWith<RuntimeException> {
                runBlocking {
                    pdlService.getPdlPerson("123", loggingMeta)
                }
            }
        }
    }
})
