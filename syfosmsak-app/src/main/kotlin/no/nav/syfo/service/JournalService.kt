package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.createJournalpostPayload
import no.nav.syfo.client.createPdfPayload
import no.nav.syfo.log
import no.nav.syfo.metrics.MELDING_LAGER_I_JOARK
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

interface JournalService {
    suspend fun onJournalRequest(receivedSykmelding: ReceivedSykmelding, validationResult: ValidationResult, loggingMeta: LoggingMeta)
}

abstract class AbstractJournalService<T> (
    private val journalCreatedTopic: String,
    private val producer: KafkaProducer<String, T>,
    private val sakClient: SakClient,
    private val dokArkivClient: DokArkivClient,
    private val pdfgenClient: PdfgenClient,
    private val pdlPersonService: PdlPersonService
) : JournalService {
    abstract fun getKafkaMessage(
        receivedSykmelding: ReceivedSykmelding,
        sakid: String,
        journalpostid: String
    ): T
    abstract val kafkaCluster: String
    override suspend fun onJournalRequest(receivedSykmelding: ReceivedSykmelding, validationResult: ValidationResult, loggingMeta: LoggingMeta) {
        wrapExceptions(loggingMeta) {
            log.info("$kafkaCluster: Mottok en sykmelding, prover å lagre i Joark {}", fields(loggingMeta))

            val sak = sakClient.findOrCreateSak(receivedSykmelding.sykmelding.pasientAktoerId, receivedSykmelding.msgId, loggingMeta)
            val sakid = sak.id.toString()

            val journalpostid = opprettEllerFinnPDFJournalpost(receivedSykmelding, validationResult, sakid, loggingMeta)
            val registerJournal = getKafkaMessage(receivedSykmelding, sakid, journalpostid)

            try {
                producer.send(ProducerRecord(journalCreatedTopic, receivedSykmelding.sykmelding.id, registerJournal)).get()
                log.info("$kafkaCluster: message sendt to kafka", fields(loggingMeta))
            } catch (ex: Exception) {
                log.error("$kafkaCluster: Error sending to kafkatopic {} {}", journalCreatedTopic, fields(loggingMeta))
                throw ex
            }
            if (skalOpprettePdf(receivedSykmelding.sykmelding.avsenderSystem)) {
                MELDING_LAGER_I_JOARK.inc()
                log.info(
                    "$kafkaCluster: Melding lagret i Joark med journalpostId {}, {}",
                    journalpostid,
                    fields(loggingMeta)
                )
            }
        }
    }

    suspend fun opprettEllerFinnPDFJournalpost(receivedSykmelding: ReceivedSykmelding, validationResult: ValidationResult, sakid: String, loggingMeta: LoggingMeta): String {
        return if (skalOpprettePdf(receivedSykmelding.sykmelding.avsenderSystem)) {
            val patient = pdlPersonService.getPdlPerson(receivedSykmelding.personNrPasient, loggingMeta)
            val pdfPayload = createPdfPayload(receivedSykmelding, validationResult, patient)

            val pdf = pdfgenClient.createPdf(pdfPayload)
            log.info("PDF generert {}", fields(loggingMeta))

            val journalpostPayload = createJournalpostPayload(receivedSykmelding, sakid, pdf, validationResult)
            val journalpost = dokArkivClient.createJournalpost(journalpostPayload, loggingMeta)

            journalpost.journalpostId
        } else {
            log.info("Oppretter ikke ny pdf for papirsykmelding {}", fields(loggingMeta))
            receivedSykmelding.sykmelding.avsenderSystem.versjon
        }
    }

    private fun skalOpprettePdf(avsenderSystem: AvsenderSystem): Boolean {
        return !(avsenderSystem.navn == "Papirsykmelding" && avsenderSystem.versjon != "1")
    }
}
