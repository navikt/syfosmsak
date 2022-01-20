package no.nav.syfo

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmsak"),
    val opprettSakUrl: String = getEnvVar("OPPRETT_SAK_URL"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val securityTokenServiceURL: String = getEnvVar(
        "SECURITY_TOKEN_SERVICE_URL",
        "http://security-token-service.default/rest/v1/sts/token"
    ),
    val pdfgen: String = getEnvVar("PDF_GEN_URL", "http://syfopdfgen.teamsykmelding/api/v1/genpdf/syfosm/syfosm"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val privatSykmeldingSak: String = "teamsykmelding.privat-sykmelding-sak",
    val oppgaveJournalOpprettet: String = "teamsykmelding.oppgave-journal-opprettet",
    val sykmeldingVedleggBucketName: String = getEnvVar("SYKMELDING_VEDLEGG_BUCKET_NAME")
)

data class VaultCredentials(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
