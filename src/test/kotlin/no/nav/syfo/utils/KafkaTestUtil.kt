package no.nav.syfo.utils

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

val kafkaStreamsStateDir: Path = Paths.get(System.getProperty("java.io.tmpdir"))
        .resolve("kafka-stream-integration-tests")

fun deleteDir(dir: Path) {
    if (Files.exists(dir)) {
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
    }
}

fun cleanupDir(dir: Path, streamApplicationName: String) {
    deleteDir(dir)
    Files.createDirectories(dir)
    Files.createDirectories(dir.resolve(streamApplicationName))
}
