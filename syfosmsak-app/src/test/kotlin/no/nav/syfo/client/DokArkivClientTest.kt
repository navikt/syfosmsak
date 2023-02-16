package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.util.LoggingMeta
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DokArkivClientTest {
    val accessTokenClientV2 = mockk<AccessTokenClientV2>()
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
    }
    val loggingMetadata = LoggingMeta("mottakId", "orgnur", "msgId", "legeerklæringId")

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            jackson {}
        }
        routing {
            post("/dokarkiv") {
                when {
                    call.request.header("Nav-Callid") == "NY" -> call.respond(
                        HttpStatusCode.Created,
                        JournalpostResponse(
                            emptyList(), "nyJpId", true, null, null
                        )
                    )

                    call.request.header("Nav-Callid") == "DUPLIKAT" -> call.respond(
                        HttpStatusCode.Conflict,
                        JournalpostResponse(
                            emptyList(), "eksisterendeJpId", true, null, null
                        )
                    )

                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }.start()

    val dokArkivClient = DokArkivClient("$mockHttpServerUrl/dokarkiv", accessTokenClientV2, "scope", httpClient)

    @AfterAll
    internal fun teardown() {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    @Test
    internal fun `Happy-case`() {
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "Token"

        runBlocking {

            val jpResponse = dokArkivClient.createJournalpost(
                JournalpostRequest(
                    dokumenter = emptyList(),
                    eksternReferanseId = "NY"
                ),
                loggingMetadata
            )

            Assertions.assertEquals("nyJpId", jpResponse.journalpostId)
        }
    }

    @Test
    internal fun `Feiler ikke ved duplikat`() {
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "Token"

        runBlocking {
            val jpResponse = dokArkivClient.createJournalpost(
                JournalpostRequest(
                    dokumenter = emptyList(),
                    eksternReferanseId = "DUPLIKAT"
                ),
                loggingMetadata
            )

            Assertions.assertEquals("eksisterendeJpId", jpResponse.journalpostId)
        }
    }
}
