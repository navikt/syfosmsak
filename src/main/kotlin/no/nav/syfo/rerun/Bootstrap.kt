package no.nav.syfo.rerun

import io.ktor.util.KtorExperimentalAPI
import java.util.Properties
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.rerun.pdf.service.RerunPdfGenerationService
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.service.JournalService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer

@KtorExperimentalAPI
fun setupRerunDependencies(journalService: JournalService, env: Environment, consumerConfig: Properties, applicationState: ApplicationState, producerConfig: Properties) {
    val kafkaConsumer = KafkaConsumer<String, String>(consumerConfig)
    val kafkaProducer = KafkaProducer<String, ProduceTask>(producerConfig)

    val rerunPdfGenerationService = RerunPdfGenerationService(kafkaConsumer, journalService, applicationState, env.rerunTopicName, kafkaProducer)

    rerunPdfGenerationService.start()
}
