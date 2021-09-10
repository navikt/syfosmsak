package no.nav.syfo

import com.fasterxml.jackson.module.kotlin.readValue
import java.net.ServerSocket
import java.time.Duration
import java.time.LocalDateTime
import java.util.Properties
import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.utils.cleanupDir
import no.nav.syfo.utils.deleteDir
import no.nav.syfo.utils.kafkaStreamsStateDir
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.StreamsConfig
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object StreamsTest : Spek({
    val sm2013Auto = "it-sm2013-auto"
    val sm2013Manual = "it-sm2013-manual"
    val sm2013Invalid = "it-sm2013-invalid"
    val behandlingsutfall = "it-sm2013-behandlingsutfall"
    val sm2013Sak = "it-sm2013-sak"
    fun getRandomPort() = ServerSocket(0).use {
        it.localPort
    }
    val kafkaEnvironment = KafkaEnvironment(
            withSchemaRegistry = true,
            topicNames = listOf(sm2013Auto, sm2013Manual, sm2013Invalid, behandlingsutfall, sm2013Sak)
    )

    val env = Environment(
        applicationPort = getRandomPort(),
        kafkaBootstrapServers = kafkaEnvironment.brokersURL,
        dokArkivUrl = "TODO",
        opprettSakUrl = "TODO",
        securityTokenServiceURL = "TODO",
        pdlGraphqlPath = "TODO",
        truststore = "TODO",
        truststorePassword = "TODO",
        cluster = "TODO",
        aadAccessTokenV2Url = "aadAccessTokenV2Url",
        clientIdV2 = "clientIdV2",
        clientSecretV2 = "clientSecretV2",
        pdlScope = "pdlScope",
        sm2013AutomaticHandlingTopic = sm2013Auto,
        sm2013ManualHandlingTopic = sm2013Manual,
        sm2013InvalidHandlingTopic = sm2013Invalid,
        sm2013BehandlingsUtfallTopic = behandlingsutfall,
        sm2013SakTopic = sm2013Sak
    )
    val vaultCredentials = VaultCredentials("unused", "unused")

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
        put("schema.registry.url", kafkaEnvironment.schemaRegistry!!.url)
        put(StreamsConfig.STATE_DIR_CONFIG, kafkaStreamsStateDir.toAbsolutePath().toString())
    }

    val baseProperties = loadBaseConfig(env, vaultCredentials)
    val streamsProperties = baseProperties
            .toStreamsConfig(env.applicationName, valueSerde = Serdes.String()::class)
            .overrideForTest()

    val streamsApplication = createKafkaStream(streamsProperties, env)

    beforeGroup {
        cleanupDir(kafkaStreamsStateDir, env.applicationName)
        kafkaEnvironment.start()

        streamsApplication.start()
    }

    afterGroup {
        kafkaEnvironment.tearDown()
        deleteDir(kafkaStreamsStateDir)
    }

    describe("Kafka streams") {
        val behandlingsutfallProducer = KafkaProducer<String, String>(baseProperties
                .toProducerConfig("spek-it-producer", StringSerializer::class)
                .overrideForTest())

        val smProducer = KafkaProducer<String, String>(baseProperties
                .toProducerConfig("spek-it-producer", StringSerializer::class)
                .overrideForTest())

        val outputConsumer = KafkaConsumer<String, String>(baseProperties
                .toConsumerConfig("spek-it-consumer", StringDeserializer::class)
                .overrideForTest())

        outputConsumer.subscribe(listOf(env.sm2013SakTopic))

        val sykmelding = generateSykmelding()
        val receivedSykmelding = ReceivedSykmelding(
                sykmelding = sykmelding,
                personNrPasient = "123124",
                tlfPasient = "13214",
                personNrLege = "123145",
                navLogId = "0412",
                msgId = "12314-123124-43252-2344",
                legekontorOrgNr = "",
                legekontorHerId = "",
                legekontorReshId = "",
                legekontorOrgName = "Legevakt",
                mottattDato = LocalDateTime.now(),
                rulesetVersion = "",
                fellesformat = "",
                tssid = "",
                merknader = null
        )

        it("Streams should join together the two topics") {
            smProducer.send(ProducerRecord(env.sm2013AutomaticHandlingTopic, sykmelding.id, objectMapper.writeValueAsString(receivedSykmelding)))
            behandlingsutfallProducer.send(ProducerRecord(env.sm2013BehandlingsUtfallTopic, sykmelding.id, objectMapper.writeValueAsString(ValidationResult(Status.OK, emptyList()))))

            val joined = outputConsumer.poll(Duration.ofMillis(10000)).toList()

            joined.size shouldEqual 1
            val journaledSykmelding = objectMapper.readValue<BehandlingsUtfallReceivedSykmelding>(joined.first().value())

            journaledSykmelding.behandlingsUtfall shouldEqual objectMapper.writeValueAsBytes(ValidationResult(Status.OK, emptyList()))
            journaledSykmelding.receivedSykmelding shouldEqual objectMapper.writeValueAsBytes(receivedSykmelding)
        }
        it("Sykmelding under behandling filtreres bort") {
            val sykmeldingMedMerknad = generateSykmelding()
            val receivedSykmeldingMedMerknad = receivedSykmelding.copy(sykmelding = sykmeldingMedMerknad, merknader = listOf(Merknad("UNDER_BEHANDLING", "Til manuell behandling")))
            smProducer.send(ProducerRecord(env.sm2013AutomaticHandlingTopic, sykmeldingMedMerknad.id, objectMapper.writeValueAsString(receivedSykmeldingMedMerknad)))
            behandlingsutfallProducer.send(ProducerRecord(env.sm2013BehandlingsUtfallTopic, sykmelding.id, objectMapper.writeValueAsString(ValidationResult(Status.OK, listOf(
                RuleInfo("UNDER_BEHANDLING", "manuell behandling", "manuell behandling", Status.OK)
            )))))

            val joined = outputConsumer.poll(Duration.ofMillis(1000)).toList()

            joined.size shouldEqual 0
        }
    }
})
