package no.nav.syfo.kafka

import java.io.File
import java.util.Properties
import kotlin.reflect.KClass
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.*
import org.apache.kafka.streams.StreamsConfig

interface KafkaConfig {
    val kafkaBootstrapServers: String
    val truststore: String?
    val truststorePassword: String?
    val cluster: String
}

interface KafkaCredentials {
    val kafkaUsername: String
    val kafkaPassword: String
}

fun loadBaseConfig(env: KafkaConfig, credentials: KafkaCredentials): Properties =
    Properties().also {
        it.load(KafkaConfig::class.java.getResourceAsStream("/kafka_base.properties"))
        it["sasl.jaas.config"] =
            "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"${credentials.kafkaUsername}\" password=\"${credentials.kafkaPassword}\";"
        it["bootstrap.servers"] = env.kafkaBootstrapServers
        it["specific.avro.reader"] = true
        if (env.cluster != "localhost") {
            it[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SASL_SSL"
            it[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = File(env.truststore!!).absolutePath
            it[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = env.truststorePassword!!
        }
    }

fun Properties.envOverrides() = apply {
    putAll(
        System.getenv()
            .filter { (key, _) -> key.startsWith("KAFKA_") }
            .map { (key, value) -> key.substring(6).lowercase().replace("_", ".") to value }
            .toMap()
    )
}

fun Properties.toConsumerConfig(
    groupId: String,
    valueDeserializer: KClass<out Deserializer<out Any>>,
    keyDeserializer: KClass<out Deserializer<out Any>> = StringDeserializer::class
): Properties =
    Properties().also {
        it.putAll(this)
        it[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = keyDeserializer.java
        it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = valueDeserializer.java
        it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
    }

fun Properties.toStreamsConfig(
    applicationName: String,
    valueSerde: KClass<out Serde<out Any>>,
    keySerde: KClass<out Serde<out Any>> = Serdes.String()::class
): Properties =
    Properties().also {
        it.putAll(this)
        // TODO hacky workaround for kafka streams issues
        it.setProperty("acks", "1")
        it.remove("enable.idempotence")
        it[StreamsConfig.APPLICATION_ID_CONFIG] = applicationName
        it[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = keySerde.java
        it[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = valueSerde.java
    }

fun Properties.toProducerConfig(
    groupId: String,
    valueSerializer: KClass<out Serializer<out Any>>,
    keySerializer: KClass<out Serializer<out Any>> = StringSerializer::class
): Properties =
    Properties().also {
        it.putAll(this)
        it[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = valueSerializer.java
        it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = keySerializer.java
    }
