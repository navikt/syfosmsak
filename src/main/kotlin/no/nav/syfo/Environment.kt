package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmsak"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sm2013ManualHandlingTopic: String = getEnvVar("KAFKA_SM2013_MANUAL_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val sm2013InvalidHandlingTopic: String = getEnvVar("KAFKA_SM2013_INVALID_TOPIC", "privat-syfo-sm2013-avvistBehandling"),
    val smpapirAutomaticHandlingTopic: String = getEnvVar("KAFKA_SMPAPIR_AUTOMATIC_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
    val smpapirManualHandlingTopic: String = getEnvVar("KAFKA_SMPAPIR_MANUAL_TOPIC", "privat-syfo-smpapir-manuellBehandling"),
    val journalCreatedTopic: String = getEnvVar("KAFKA_JOURNAL_CREATED_TOPIC", "aapen-syfo-oppgave-journalOpprettet"),
    val opprettSakUrl: String = getEnvVar("OPPRETT_SAK_URL"),
    val dokmotMottaInngaaendeUrl: String = getEnvVar("DOKMOT_MOTTA_INNGAAENDE_URL"),
    val personV3EndpointURL: String = getEnvVar("PERSON_V3_ENDPOINT_URL"),
    val securityTokenServiceURL: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val pdfgen: String = getEnvVar("PDF_GEN_URL", "http://syfopdfgen/api/v1/genpdf/syfosm/syfosm")
) : KafkaConfig

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
