package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmsak"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sm2013ManualHandlingTopic: String = getEnvVar("KAFKA_SM2013_MANUAL_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val sm2013InvalidHandlingTopic: String = getEnvVar("KAFKA_SM2013_INVALID_TOPIC", "privat-syfo-sm2013-avvistBehandling"),
    val journalCreatedTopic: String = getEnvVar("KAFKA_JOURNAL_CREATED_TOPIC", "aapen-syfo-oppgave-journalOpprettet"),
    val sm2013SakTopic: String = getEnvVar("KAFKA_SM2013_SAK_TOPIC", "privat-syfo-sm2013-sak"),
    val sm2013BehandlingsUtfallTopic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
    val opprettSakUrl: String = getEnvVar("OPPRETT_SAK_URL"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val securityTokenServiceURL: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val pdfgen: String = getEnvVar("PDF_GEN_URL", "http://syfopdfgen.teamsykmelding/api/v1/genpdf/syfosm/syfosm"),
    val rerunTopicName: String = getEnvVar("RERUN_TOPIC_NAME", "privat-syfo-register-rerun-tmp"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
    override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD"),
    override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME")
) : KafkaConfig

data class VaultCredentials(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password")
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
