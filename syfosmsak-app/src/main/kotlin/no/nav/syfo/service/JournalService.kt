package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.createJournalpostPayload
import no.nav.syfo.client.createPdfPayload
import no.nav.syfo.log
import no.nav.syfo.metrics.MELDING_LAGER_I_JOARK
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.JournalKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class JournalService(
    private val journalCreatedTopic: String,
    private val producer: KafkaProducer<String, JournalKafkaMessage>,
    private val sakClient: SakClient,
    private val dokArkivClient: DokArkivClient,
    private val pdfgenClient: PdfgenClient,
    private val pdlPersonService: PdlPersonService,
    private val bucketService: BucketService
) {
    suspend fun onJournalRequest(receivedSykmelding: ReceivedSykmelding, validationResult: ValidationResult, loggingMeta: LoggingMeta) {
        wrapExceptions(loggingMeta) {
            log.info("Mottok en sykmelding, prover å lagre i Joark {}", StructuredArguments.fields(loggingMeta))
            val sak = sakClient.findOrCreateSak(receivedSykmelding.sykmelding.pasientAktoerId, receivedSykmelding.msgId, loggingMeta)
            val sakid = sak.id.toString()

            val journalpostid = opprettEllerFinnPDFJournalpost(receivedSykmelding, validationResult, sakid, loggingMeta)
            val registerJournal = getKafkaMessage(receivedSykmelding, sakid, journalpostid)

            try {
                producer.send(ProducerRecord(journalCreatedTopic, receivedSykmelding.sykmelding.id, registerJournal)).get()
                log.info("message sendt to kafka", StructuredArguments.fields(loggingMeta))
            } catch (ex: Exception) {
                log.error("Error sending to kafkatopic {} {}", journalCreatedTopic, StructuredArguments.fields(loggingMeta))
                throw ex
            }
            if (skalOpprettePdf(receivedSykmelding.sykmelding.avsenderSystem)) {
                MELDING_LAGER_I_JOARK.inc()
                log.info(
                    "Melding lagret i Joark med journalpostId {}, {}",
                    journalpostid,
                    StructuredArguments.fields(loggingMeta)
                )
            }
        }
    }

    suspend fun opprettEllerFinnPDFJournalpost(receivedSykmelding: ReceivedSykmelding, validationResult: ValidationResult, sakid: String, loggingMeta: LoggingMeta): String {
        return if (skalOpprettePdf(receivedSykmelding.sykmelding.avsenderSystem)) {
            val vedleggListe: List<Vedlegg> = if (receivedSykmelding.vedlegg.isNullOrEmpty()) {
                emptyList()
            } else {
                receivedSykmelding.vedlegg!!.map {
                    bucketService.getVedleggFromBucket(it)
                }
            }
            val patient = pdlPersonService.getPdlPerson(receivedSykmelding.personNrPasient, loggingMeta)
            val pdfPayload = createPdfPayload(receivedSykmelding, validationResult, patient)

            val pdf = pdfgenClient.createPdf(pdfPayload)
            log.info("PDF generert {}", StructuredArguments.fields(loggingMeta))

            val journalpostPayload = createJournalpostPayload(receivedSykmelding, sakid, pdf, validationResult, vedleggListe)
            val journalpost = dokArkivClient.createJournalpost(journalpostPayload, loggingMeta)

            journalpost.journalpostId
        } else {
            log.info("Oppretter ikke ny pdf for papirsykmelding {}", StructuredArguments.fields(loggingMeta))
            receivedSykmelding.sykmelding.avsenderSystem.versjon
        }
    }

    private fun skalOpprettePdf(avsenderSystem: AvsenderSystem): Boolean {
        return !(avsenderSystem.navn == "Papirsykmelding" && avsenderSystem.versjon != "1")
    }

    fun getKafkaMessage(
        receivedSykmelding: ReceivedSykmelding,
        sakid: String,
        journalpostid: String
    ): JournalKafkaMessage = JournalKafkaMessage(
        journalpostKilde = "AS36",
        messageId = receivedSykmelding.msgId,
        sakId = sakid,
        journalpostId = journalpostid
    )
}
