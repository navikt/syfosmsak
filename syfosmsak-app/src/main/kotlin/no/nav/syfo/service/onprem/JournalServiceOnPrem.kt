package no.nav.syfo.service.onprem

import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.service.AbstractJournalService
import org.apache.kafka.clients.producer.KafkaProducer

class JournalServiceOnPrem(
    journalCreatedTopic: String,
    producer: KafkaProducer<String, RegisterJournal>,
    sakClient: SakClient,
    dokArkivClient: DokArkivClient,
    pdfgenClient: PdfgenClient,
    pdlPersonService: PdlPersonService
) : AbstractJournalService<RegisterJournal>(
    journalCreatedTopic = journalCreatedTopic,
    producer = producer,
    sakClient = sakClient,
    dokArkivClient = dokArkivClient,
    pdfgenClient = pdfgenClient,
    pdlPersonService = pdlPersonService
) {
    override val kafkaCluster: String = "on-prem"
    override fun getKafkaMessage(
        receivedSykmelding: ReceivedSykmelding,
        sakid: String,
        journalpostid: String
    ): RegisterJournal = RegisterJournal().apply {
        journalpostKilde = "AS36"
        messageId = receivedSykmelding.msgId
        sakId = sakid
        journalpostId = journalpostid
    }
}
