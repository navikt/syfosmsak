package no.nav.syfo.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

class AccessTokenClientV2(
    private val aadAccessTokenUrl: String,
    private val clientId: String,
    private val clientSecret: String,
    private val httpClient: HttpClient,
) {
    private val mutex = Mutex()

    @Volatile private var tokenMap = HashMap<String, AadAccessTokenMedExpiry>()

    suspend fun getAccessTokenV2(resource: String, loggingMeta: LoggingMeta): String {
        log.info(
            "Forsøker å hente nytt token fra Azure AD {}",
            StructuredArguments.fields(loggingMeta)
        )
        val omToMinutter = Instant.now().plusSeconds(120L)
        return mutex.withLock {
            (tokenMap[resource]?.takeUnless { it.expiresOn.isBefore(omToMinutter) }
                    ?: run {
                        log.info(
                            "Henter nytt token fra Azure AD {}",
                            StructuredArguments.fields(loggingMeta)
                        )
                        val response: AadAccessTokenV2 =
                            httpClient
                                .post(aadAccessTokenUrl) {
                                    accept(ContentType.Application.Json)
                                    method = HttpMethod.Post
                                    setBody(
                                        FormDataContent(
                                            Parameters.build {
                                                append("client_id", clientId)
                                                append("scope", resource)
                                                append("grant_type", "client_credentials")
                                                append("client_secret", clientSecret)
                                            },
                                        ),
                                    )
                                }
                                .body()
                        val tokenMedExpiry =
                            AadAccessTokenMedExpiry(
                                access_token = response.access_token,
                                expires_in = response.expires_in,
                                expiresOn = Instant.now().plusSeconds(response.expires_in.toLong()),
                            )
                        tokenMap[resource] = tokenMedExpiry
                        log.info(
                            "Har hentet nytt token fra Azure AD {}",
                            StructuredArguments.fields(loggingMeta)
                        )
                        return@run tokenMedExpiry
                    })
                .access_token
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AadAccessTokenV2(
    val access_token: String,
    val expires_in: Int,
)

data class AadAccessTokenMedExpiry(
    val access_token: String,
    val expires_in: Int,
    val expiresOn: Instant,
)
