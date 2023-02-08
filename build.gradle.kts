import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.6.4"
val jacksonVersion = "2.14.2"
val kafkaVersion = "3.3.1"
val kluentVersion = "1.68"
val ktorVersion = "2.2.3"
val logstashLogbackEncoder = "7.2"
val logbackVersion = "1.4.5"
val prometheusVersion = "0.16.0"
val smCommonVersion = "1.fbf33a9"
val kotestVersion = "5.4.1"
val ioMockVersion = "1.12.4"
val kotlinVersion = "1.8.10"
val pdfboxVersion = "2.0.24"
val googleCloudStorageVersion = "2.3.0"

plugins {
    kotlin("jvm") version "1.8.10"
    id("org.jmailen.kotlinter") version "3.10.0"
    id("com.diffplug.spotless") version "6.5.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

val githubUser: String by project
val githubPassword: String by project

allprojects {
    group = "no.nav.syfo"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
            credentials {
                username = githubUser
                password = githubPassword
            }
        }
    }
}
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
        implementation("io.prometheus:simpleclient_common:$prometheusVersion")

        implementation("io.ktor:ktor-server-core:$ktorVersion")
        implementation("io.ktor:ktor-server-netty:$ktorVersion")
        implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
        implementation("io.ktor:ktor-server-call-id:$ktorVersion")
        implementation("io.ktor:ktor-client-core:$ktorVersion")
        implementation("io.ktor:ktor-client-apache:$ktorVersion")
        implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

        implementation("ch.qos.logback:logback-classic:$logbackVersion")
        implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoder")

        implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
        implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
        implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
        implementation("no.nav.helse:syfosm-common-diagnosis-codes:$smCommonVersion")

        implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")
        implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")

        testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
        testImplementation("org.amshove.kluent:kluent:$kluentVersion")
        testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
        testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
        testImplementation("io.mockk:mockk:$ioMockVersion")
    }
    tasks {
        withType<Jar> {
            manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
        }

        create("printVersion") {
            doLast {
                println(project.version)
            }
        }

        withType<ShadowJar> {
            transform(ServiceFileTransformer::class.java) {
                setPath("META-INF/cxf")
                include("bus-extensions.txt")
            }
        }

        withType<Test> {
            useJUnitPlatform {
            }
            testLogging {
                events("skipped", "failed")
                showStackTraces = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }

        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = "17"
        }

        "check" {
            dependsOn("formatKotlin")
        }
    }

}

