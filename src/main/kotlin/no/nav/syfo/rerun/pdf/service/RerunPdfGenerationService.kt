package no.nav.syfo.rerun.pdf.service

import io.ktor.util.KtorExperimentalAPI
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.time.delay
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.createListener
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.service.JournalService
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

data class RerunKafkaMessage(val receivedSykmelding: ReceivedSykmelding, val validationResult: ValidationResult)

@KtorExperimentalAPI
class RerunPdfGenerationService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val journalService: JournalService,
    private val applicationState: ApplicationState,
    private val topicName: String,
    private val kafkaProducer: KafkaProducer<String, ProduceTask>
) {
    private val ignorIds = listOf("7e073ab6-ecc1-49a1-a726-7dccb8b5ab23")
    private val log = LoggerFactory.getLogger(RerunPdfGenerationService::class.java)

    fun start() {
        createListener(applicationState) {
            kafkaConsumer.subscribe(listOf(topicName))
            subscribeAndCreatePDF()
        }
    }

    private suspend fun subscribeAndCreatePDF() {
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofSeconds(1)).forEach {
                handleReceivedSykmelding(objectMapper.readValue(it.value(), RerunKafkaMessage::class.java))
            }
            delay(Duration.ofSeconds(1))
        }
    }

    private suspend fun handleReceivedSykmelding(rerunKafkaMessage: RerunKafkaMessage) {
        val meta = LoggingMeta(rerunKafkaMessage.receivedSykmelding.navLogId,
                rerunKafkaMessage.receivedSykmelding.legekontorOrgNr,
                rerunKafkaMessage.receivedSykmelding.msgId,
                rerunKafkaMessage.receivedSykmelding.sykmelding.id)

        log.info("Received sykmelding from rerun-topic, {}", fields(meta))

        val validationResult = rerunKafkaMessage.validationResult

        if (!ignorIds.contains(rerunKafkaMessage.receivedSykmelding.sykmelding.id)) {
            journalService.onJournalRequest(rerunKafkaMessage.receivedSykmelding, validationResult, meta)
        }

        if (validationResult.status == Status.MANUAL_PROCESSING) {
            val produceTask = ProduceTask().apply {
                messageId = rerunKafkaMessage.receivedSykmelding.msgId
                aktoerId = rerunKafkaMessage.receivedSykmelding.sykmelding.pasientAktoerId
                tildeltEnhetsnr = ""
                opprettetAvEnhetsnr = "9999"
                behandlesAvApplikasjon = "FS22" // Gosys
                orgnr = rerunKafkaMessage.receivedSykmelding.legekontorOrgNr ?: ""
                beskrivelse = "Manuell behandling av sykmelding grunnet følgende regler: ${validationResult.ruleHits.joinToString(", ", "(", ")") { it.messageForSender }}"
                temagruppe = "ANY"
                tema = "SYM"
                behandlingstema = "ANY"
                oppgavetype = "BEH_EL_SYM"
                behandlingstype = "ANY"
                mappeId = 1
                aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
                fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
                prioritet = PrioritetType.NORM
                metadata = mapOf()
            }

            log.info("Created produceTask, sending to aapen-syfo-oppgave-produserOppgave, {}", fields(meta))
            try {
                kafkaProducer.send(ProducerRecord("aapen-syfo-oppgave-produserOppgave", rerunKafkaMessage.receivedSykmelding.sykmelding.id, produceTask)).get()
            } catch (ex: Exception) {
                log.error("Failed to send to kafka fro rerun {}", fields(meta))
                throw ex
            }
        }
    }
}
