package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

object DokArkivClientTest : Spek({
    val stsOidcClient = mockk<StsOidcClient>()
    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
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
        install(ContentNegotiation) {
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

    val dokArkivClient = DokArkivClient("$mockHttpServerUrl/dokarkiv", stsOidcClient, httpClient)

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    describe("Test av DokarkivClient") {
        it("Happy-case") {
            var jpResponse: JournalpostResponse? = null
            runBlocking {
                coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
                jpResponse = dokArkivClient.createJournalpost(
                    JournalpostRequest(
                        dokumenter = emptyList(),
                        eksternReferanseId = "NY"
                    ),
                    loggingMetadata
                )
            }

            jpResponse?.journalpostId shouldBeEqualTo "nyJpId"
        }
        it("Feiler ikke ved duplikat") {
            var jpResponse: JournalpostResponse? = null
            runBlocking {
                coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
                jpResponse = dokArkivClient.createJournalpost(
                    JournalpostRequest(
                        dokumenter = emptyList(),
                        eksternReferanseId = "DUPLIKAT"
                    ),
                    loggingMetadata
                )
            }

            jpResponse?.journalpostId shouldBeEqualTo "eksisterendeJpId"
        }
    }
})
