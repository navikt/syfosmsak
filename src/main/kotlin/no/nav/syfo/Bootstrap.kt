package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.network.sockets.SocketTimeoutException
import io.prometheus.client.hotspot.DefaultExports
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
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.model.JournalKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.PdlFactory
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.service.JournalService
import no.nav.syfo.service.aiven.JournalServiceAiven
import no.nav.syfo.service.onprem.JournalServiceOnPrem
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.Unbounded
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.kstream.Joined
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ProxySelector
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

data class BehandlingsUtfallReceivedSykmelding(val receivedSykmelding: ByteArray, val behandlingsUtfall: ByteArray)

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmsak")

@DelicateCoroutinesApi
fun main() {
    val env = Environment()
    val credentials = VaultCredentials()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    DefaultExports.initialize()

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
        HttpResponseValidator {
            handleResponseException { exception ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
    }

    val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    val httpClient = HttpClient(Apache, config)
    val httpClientWithProxy = HttpClient(Apache, proxyConfig)

    val stsClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceURL)
    val sakClient = SakClient(env.opprettSakUrl, stsClient, httpClient)
    val dokArkivClient = DokArkivClient(env.dokArkivUrl, stsClient, httpClient)
    val pdfgenClient = PdfgenClient(env.pdfgen, httpClient)

    val accessTokenClientV2 = AccessTokenClientV2(env.aadAccessTokenV2Url, env.clientIdV2, env.clientSecretV2, httpClientWithProxy)
    val pdlPersonService = PdlFactory.getPdlService(env, httpClient, accessTokenClientV2, env.pdlScope)

    val kafkaBaseConfig = loadBaseConfig(env, credentials).envOverrides()
    kafkaBaseConfig["auto.offset.reset"] = "none"
    val consumerConfig = kafkaBaseConfig.toConsumerConfig(
        "${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class
    )
    val producerConfig = kafkaBaseConfig.toProducerConfig(env.applicationName, KafkaAvroSerializer::class)
        .apply { this.setProperty(ProducerConfig.RETRIES_CONFIG, "100") }
    val producer = KafkaProducer<String, RegisterJournal>(producerConfig)
    val streamProperties = kafkaBaseConfig.toStreamsConfig(env.applicationName, valueSerde = Serdes.String()::class)
        .apply { this.setProperty(StreamsConfig.NUM_STREAM_THREADS_CONFIG, "1") }

    val journalService = JournalServiceOnPrem(env.journalCreatedTopic, producer, sakClient, dokArkivClient, pdfgenClient, pdlPersonService)
    val aivenProducer = KafkaProducer<String, JournalKafkaMessage>(KafkaUtils.getAivenKafkaConfig().toProducerConfig("${env.applicationName}-producer", JacksonKafkaSerializer::class))
    val journalAivenService = JournalServiceAiven(env.oppgaveJournalOpprettet, aivenProducer, sakClient, dokArkivClient, pdfgenClient, pdlPersonService)
    applicationState.ready = true

    startKafkaAivenStream(env, applicationState)
    launchListeners(env, applicationState, consumerConfig, journalService, journalAivenService, streamProperties)
}

fun startKafkaAivenStream(env: Environment, applicationState: ApplicationState) {
    val streamsBuilder = StreamsBuilder()
    val streamProperties = KafkaUtils.getAivenKafkaConfig().toStreamsConfig(env.applicationName, Serdes.String()::class, Serdes.String()::class)
    val inputStream = streamsBuilder.stream(
        listOf(
            env.okSykmeldingTopic,
            env.avvistSykmeldingTopic,
            env.manuellSykmeldingTopic
        ),
        Consumed.with(Serdes.String(), Serdes.String())
    ).filter { _, value ->
        value?.let { objectMapper.readValue<ReceivedSykmelding>(value).merknader?.any { it.type == "UNDER_BEHANDLING" } != true } ?: true
    }

    val behandlingsutfallStream = streamsBuilder.stream(
        listOf(
            env.behandlingsUtfallTopic
        ),
        Consumed.with(Serdes.String(), Serdes.String())
    ).filter { _, value ->
        !(value?.let { objectMapper.readValue<ValidationResult>(value).ruleHits.any { it.ruleName == "UNDER_BEHANDLING" } } ?: false)
    }

    val joinWindow = JoinWindows.of(Duration.ofDays(14))

    inputStream.join(
        behandlingsutfallStream,
        { sm2013, behandling ->
            log.info("streamed to aiven")
            objectMapper.writeValueAsString(
                BehandlingsUtfallReceivedSykmelding(
                    receivedSykmelding = sm2013.toByteArray(Charsets.UTF_8),
                    behandlingsUtfall = behandling.toByteArray(Charsets.UTF_8)
                )
            )
        },
        joinWindow
    ).to(env.privatSykmeldingSak)

    val stream = KafkaStreams(streamsBuilder.build(), streamProperties)
    stream.setUncaughtExceptionHandler { err ->
        log.error("Aiven: Caught exception in stream: ${err.message}", err)
        stream.close(Duration.ofSeconds(30))
        applicationState.ready = false
        applicationState.alive = false
        throw err
    }

    stream.setStateListener { newState, oldState ->
        log.info("Aiven: From state={} to state={}", oldState, newState)
        if (newState == KafkaStreams.State.ERROR) {
            // if the stream has died there is no reason to keep spinning
            log.error("Aiven: Closing stream because it went into error state")
            stream.close(Duration.ofSeconds(30))
            log.error("Aiven: Restarter applikasjon")
            applicationState.ready = false
            applicationState.alive = false
        }
    }
    stream.start()
}

fun createKafkaStream(streamProperties: Properties, env: Environment): KafkaStreams {
    val streamsBuilder = StreamsBuilder()

    val sm2013InputStream = streamsBuilder.stream<String, String>(
        listOf(
            env.sm2013AutomaticHandlingTopic,
            env.sm2013ManualHandlingTopic,
            env.sm2013InvalidHandlingTopic
        ),
        Consumed.with(Serdes.String(), Serdes.String())
    )

    val behandlingsUtfallStream = streamsBuilder.stream<String, String>(
        listOf(
            env.sm2013BehandlingsUtfallTopic
        ),
        Consumed.with(Serdes.String(), Serdes.String())
    )

    val joinWindow = JoinWindows.of(TimeUnit.DAYS.toMillis(14))
        .until(TimeUnit.DAYS.toMillis(31))

    val joined = Joined.with(
        Serdes.String(), Serdes.String(), Serdes.String()
    )

    sm2013InputStream.filter { _, value ->
        value?.let { objectMapper.readValue<ReceivedSykmelding>(value).merknader?.any { it.type == "UNDER_BEHANDLING" } != true } ?: true
    }.join(
        behandlingsUtfallStream.filter { _, value ->
            !(value?.let { objectMapper.readValue<ValidationResult>(value).ruleHits.any { it.ruleName == "UNDER_BEHANDLING" } } ?: false)
        },
        { sm2013, behandling ->
            objectMapper.writeValueAsString(
                BehandlingsUtfallReceivedSykmelding(
                    receivedSykmelding = sm2013.toByteArray(Charsets.UTF_8),
                    behandlingsUtfall = behandling.toByteArray(Charsets.UTF_8)
                )
            )
        }, joinWindow, joined
    )
        .to(env.sm2013SakTopic, Produced.with(Serdes.String(), Serdes.String()))

    return KafkaStreams(streamsBuilder.build(), streamProperties)
}

@DelicateCoroutinesApi
fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch(Dispatchers.Unbounded) {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
        } catch (ex: Exception) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter", ex.cause)
        } finally {
            log.error("Setting ready and alive to false")
            applicationState.ready = false
            applicationState.alive = false
        }
    }

@DelicateCoroutinesApi
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    consumerProperties: Properties,
    journalService: JournalService,
    journalServiceAiven: JournalService,
    streamProperties: Properties
) {
    val kafkaStream = createKafkaStream(streamProperties, env)

    kafkaStream.setUncaughtExceptionHandler { err ->
        log.error("Caught exception in stream: ${err.message}", err)
        kafkaStream.close(Duration.ofSeconds(30))
        applicationState.ready = false
        applicationState.alive = false
        throw err
    }

    kafkaStream.setStateListener { newState, oldState ->
        log.info("From state={} to state={}", oldState, newState)
        if (newState == KafkaStreams.State.ERROR) {
            // if the stream has died there is no reason to keep spinning
            log.error("Closing stream because it went into error state")
            kafkaStream.close(Duration.ofSeconds(30))
            log.error("Restarter applikasjon")
            applicationState.ready = false
            applicationState.alive = false
        }
    }
    kafkaStream.start()
    val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
    val kafkaAivenConsumer = KafkaConsumer<String, String>(KafkaUtils.getAivenKafkaConfig().toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class))
    createListener(applicationState) {
        kafkaAivenConsumer.subscribe(listOf(env.privatSykmeldingSak))
        blockingApplicationLogic(
            kafkaAivenConsumer,
            applicationState,
            journalServiceAiven
        )
    }
    createListener(applicationState) {
        kafkaconsumer.subscribe(listOf(env.sm2013SakTopic))
        blockingApplicationLogic(
            kafkaconsumer,
            applicationState,
            journalService
        )
    }.invokeOnCompletion {
        log.info("Unsubscribing and stopping kafka stream")
        kafkaconsumer.unsubscribe()
        kafkaStream.close()
    }
}

suspend fun blockingApplicationLogic(
    consumer: KafkaConsumer<String, String>,
    applicationState: ApplicationState,
    journalService: JournalService
) {
    while (applicationState.ready) {
        consumer.poll(Duration.ofSeconds(1)).forEach {
            log.info("Offset for topic: privat-syfo-sm2013-sak, offset: ${it.offset()}, partisjon: ${it.partition()}")
            val behandlingsUtfallReceivedSykmelding: BehandlingsUtfallReceivedSykmelding =
                objectMapper.readValue(it.value())
            val receivedSykmelding: ReceivedSykmelding =
                objectMapper.readValue(behandlingsUtfallReceivedSykmelding.receivedSykmelding)
            val validationResult: ValidationResult =
                objectMapper.readValue(behandlingsUtfallReceivedSykmelding.behandlingsUtfall)

            val loggingMeta = LoggingMeta(
                mottakId = receivedSykmelding.navLogId,
                orgNr = receivedSykmelding.legekontorOrgNr,
                msgId = receivedSykmelding.msgId,
                sykmeldingId = receivedSykmelding.sykmelding.id
            )
            withTimeout(Duration.ofSeconds(30)) {
                journalService.onJournalRequest(receivedSykmelding, validationResult, loggingMeta)
            }
        }
    }
}
