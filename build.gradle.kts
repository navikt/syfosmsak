group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.10.1"
val jacksonVersion = "2.18.2"
val kafkaVersion = "3.9.0"
val ktorVersion = "3.1.0"
val logstashLogbackEncoder = "8.0"
val logbackVersion = "1.5.16"
val prometheusVersion = "0.16.0"
val junitJupiterVersion = "5.11.4"
val ioMockVersion = "1.13.16"
val kotlinVersion = "2.1.10"
val pdfboxVersion = "2.0.33"
val googleCloudStorageVersion = "2.48.1"
val ktfmtVersion = "0.44"
val commonsCodecVersion = "1.18.0"
val snappyJavaVersion = "1.1.10.7"
val diagnosekoderVersion = "1.2025.0"
val opentelemetryVersion = "2.12.0"

plugins {
    id("application")
    kotlin("jvm") version "2.1.10"
    id("com.diffplug.spotless") version "7.0.2"
    id("com.gradleup.shadow") version "8.3.6"
}

application {
    mainClass.set("no.nav.syfo.BootstrapKt")
}


repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}


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
    constraints {
        implementation("commons-codec:commons-codec:$commonsCodecVersion") {
            because("override transient from io.ktor:ktor-client-apache")
        }
    }
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryVersion")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoder")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
    constraints {
        implementation("org.xerial.snappy:snappy-java:$snappyJavaVersion") {
            because("override transient from org.apache.kafka:kafka_2.12")
        }
    }
    implementation("org.apache.kafka:kafka-streams:$kafkaVersion")


    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")
    implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")

    testImplementation("no.nav.helse:diagnosekoder:$diagnosekoderVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.mockk:mockk:$ioMockVersion")
}
tasks {

    shadowJar {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.syfo.BootstrapKt",
                ),
            )
        }
    }

    test {
        useJUnitPlatform {
        }
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
        }
    }
}
