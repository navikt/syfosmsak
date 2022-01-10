package no.nav.syfo.service.aiven

import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.model.JournalKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.service.AbstractJournalService
import org.apache.kafka.clients.producer.KafkaProducer

class JournalServiceAiven(
    journalCreatedTopic: String,
    producer: KafkaProducer<String, JournalKafkaMessage>,
    sakClient: SakClient,
    dokArkivClient: DokArkivClient,
    pdfgenClient: PdfgenClient,
    pdlPersonService: PdlPersonService
) : AbstractJournalService<JournalKafkaMessage>(
    journalCreatedTopic = journalCreatedTopic,
    producer = producer,
    sakClient = sakClient,
    dokArkivClient = dokArkivClient,
    pdfgenClient = pdfgenClient,
    pdlPersonService = pdlPersonService
) {
    override val kafkaCluster: String = "aiven"
    override fun getKafkaMessage(
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
