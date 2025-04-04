package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeout
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.JournalKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.PdlFactory
import no.nav.syfo.service.BucketService
import no.nav.syfo.service.JournalService
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data class BehandlingsUtfallReceivedSykmelding(
    val receivedSykmelding: ByteArray,
    val behandlingsUtfall: ByteArray
)

val objectMapper: ObjectMapper =
    ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmsak")
val sikkerlogg = LoggerFactory.getLogger("securelog")

@DelicateCoroutinesApi
fun main() {
    val env = Environment()
    val applicationState = ApplicationState()
    val applicationEngine =
        createApplicationEngine(
            env,
            applicationState,
        )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    DefaultExports.initialize()

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException ->
                        throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                log.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn(
                        "Retrying for statuscode ${response.status.value}, for url ${request.url}"
                    )
                    true
                } else {
                    false
                }
            }
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 120_000
            connectTimeoutMillis = 100_000
            requestTimeoutMillis = 100_000
        }
    }

    val httpClient = HttpClient(Apache, config)

    val accessTokenClientV2 =
        AccessTokenClientV2(env.aadAccessTokenV2Url, env.clientIdV2, env.clientSecretV2, httpClient)
    val dokArkivClient =
        DokArkivClient(env.dokArkivUrl, accessTokenClientV2, env.dokArkivScope, httpClient)
    val pdfgenClient = PdfgenClient(env.pdfgen, httpClient)

    val pdlPersonService =
        PdlFactory.getPdlService(env, httpClient, accessTokenClientV2, env.pdlScope)

    val sykmeldingVedleggStorage: Storage = StorageOptions.newBuilder().build().service
    val bucketService = BucketService(env.sykmeldingVedleggBucketName, sykmeldingVedleggStorage)

    val aivenProducer =
        KafkaProducer<String, JournalKafkaMessage>(
            KafkaUtils.getAivenKafkaConfig("oppgave-journal-opprettet-producer")
                .toProducerConfig("${env.applicationName}-producer", JacksonKafkaSerializer::class)
        )
    val journalAivenService =
        JournalService(
            env.oppgaveJournalOpprettet,
            aivenProducer,
            dokArkivClient,
            pdfgenClient,
            pdlPersonService,
            bucketService
        )

    launchListeners(env, applicationState, journalAivenService)

    applicationServer.start()
}

@DelicateCoroutinesApi
fun createListener(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit
): Job =
    GlobalScope.launch(Dispatchers.Unbounded) {
        try {
            action()
        } catch (e: TrackableException) {
            log.error(
                "En uhåndtert feil oppstod, applikasjonen restarter {}",
                fields(e.loggingMeta),
                e.cause
            )
        } catch (ex: Exception) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter", ex)
        } finally {
            log.info("Setting ready and alive to false")
            applicationState.ready = false
            applicationState.alive = false
        }
    }

@DelicateCoroutinesApi
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    journalServiceAiven: JournalService,
) {
    val kafkaAivenConsumer =
        KafkaConsumer<String, String>(
            KafkaUtils.getAivenKafkaConfig("privat-sykmelding-sak-consumer")
                .toConsumerConfig(
                    "${env.applicationName}-consumer",
                    valueDeserializer = StringDeserializer::class
                )
                .also { it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none" },
        )

    createListener(applicationState) {
        kafkaAivenConsumer.subscribe(listOf(env.privatSykmeldingSak))
        blockingApplicationLogic(
            kafkaAivenConsumer,
            applicationState,
            journalServiceAiven,
            env,
        )
    }
}

suspend fun blockingApplicationLogic(
    consumer: KafkaConsumer<String, String>,
    applicationState: ApplicationState,
    journalService: JournalService,
    env: Environment,
) {
    while (applicationState.ready) {
        consumer.poll(Duration.ofSeconds(1)).forEach {
            log.info(
                "Offset for topic: privat-sykmelding-sak, offset: ${it.offset()}, partisjon: ${it.partition()}"
            )
            val behandlingsUtfallReceivedSykmelding: BehandlingsUtfallReceivedSykmelding =
                objectMapper.readValue(it.value())
            val receivedSykmelding: ReceivedSykmelding =
                objectMapper.readValue(behandlingsUtfallReceivedSykmelding.receivedSykmelding)
            val validationResult: ValidationResult =
                objectMapper.readValue(behandlingsUtfallReceivedSykmelding.behandlingsUtfall)

            val loggingMeta =
                LoggingMeta(
                    mottakId = receivedSykmelding.navLogId,
                    orgNr = receivedSykmelding.legekontorOrgNr,
                    msgId = receivedSykmelding.msgId,
                    sykmeldingId = receivedSykmelding.sykmelding.id,
                )
            withTimeout(Duration.ofSeconds(30)) {
                try {
                    journalService.onJournalRequest(
                        receivedSykmelding,
                        validationResult,
                        loggingMeta
                    )
                } catch (ex: Exception) {
                    if (env.cluster == "dev-gcp") {
                        log.error(
                            "Could not process record {} skipping in dev",
                            fields(loggingMeta)
                        )
                    } else {
                        log.error(
                            "Error during processing recieved sykmelding {} {}",
                            fields(loggingMeta),
                            ex.message
                        )
                        throw ex
                    }
                }
            }
        }
    }
}
