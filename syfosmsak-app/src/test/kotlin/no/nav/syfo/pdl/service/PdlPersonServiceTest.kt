package no.nav.syfo.pdl.service

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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PdlPersonServiceTest {
    val pdlClient = mockk<PdlClient>()
    val accessTokenClient = mockkClass(AccessTokenClientV2::class)
    val pdlService = PdlPersonService(pdlClient, accessTokenClient, "pdlscope")
    val loggingMeta = LoggingMeta("mottakId", "orgNr", "msgId", "sykmeldingId")

    @BeforeEach
    internal fun `Set up`() {
        clearAllMocks()
    }

    @Test
    internal fun `Hent person fra pdl uten fortrolig adresse`() {
        coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse()
        coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

        runBlocking {

            val person = pdlService.getPdlPerson("01245678901", loggingMeta)
            Assertions.assertEquals("fornavn", person.navn.fornavn)
            Assertions.assertEquals(null, person.navn.mellomnavn)
            Assertions.assertEquals("etternavn", person.navn.etternavn)
            Assertions.assertEquals("987654321", person.aktorId)
        }
    }

    @Test
    internal fun `Skal feile naar person ikke finnes`() {

        coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(null, null), errors = null)

        assertThrows<RuntimeException> {
            runBlocking {
                pdlService.getPdlPerson("123", loggingMeta)
            }
        }
    }

    @Test
    internal fun `Skal feile naar navn er tom liste`() {
        coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
            ResponseData(
                hentPerson = HentPerson(
                    navn = emptyList(), adressebeskyttelse = null
                ),
                hentIdenter = HentIdenter(emptyList())
            ),
            errors = null
        )

        assertThrows<RuntimeException> {
            runBlocking {
                pdlService.getPdlPerson("123", loggingMeta)
            }
        }
    }

    @Test
    internal fun `Skal feile naar navn ikke finnes`() {
        coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
            ResponseData(
                hentPerson = HentPerson(
                    navn = null, adressebeskyttelse = null
                ),
                hentIdenter = HentIdenter(listOf(PdlIdent(ident = "987654321", gruppe = "foo")))
            ),
            errors = null
        )

        assertThrows<RuntimeException> {
            runBlocking {
                pdlService.getPdlPerson("123", loggingMeta)
            }
        }
    }

    @Test
    internal fun `Skal feile naar identer ikke finnes`() {
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
        assertThrows<RuntimeException> {
            runBlocking {
                pdlService.getPdlPerson("123", loggingMeta)
            }
        }
    }
}
