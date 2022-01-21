package no.nav.syfo.client

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.model.VedleggMessage
import no.nav.syfo.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class DokArkivClientKtTest : Spek ({
    describe("VedleggToPdf") {
        it("Returnerer samme vedlegg hvis vedlegget er PDF") {
            val vedleggMessage: VedleggMessage = objectMapper.readValue(DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_pdf.json"))

            val oppdatertVedlegg = vedleggToPDF(vedleggMessage.vedlegg)

            oppdatertVedlegg shouldBeEqualTo vedleggMessage.vedlegg
        }
        it("Konverterer til PDF hvis vedlegget ikke er PDF") {
            val vedleggMessage: VedleggMessage = objectMapper.readValue(DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_bilde.json"))

            val oppdatertVedlegg = vedleggToPDF(vedleggMessage.vedlegg)

            oppdatertVedlegg shouldNotBeEqualTo vedleggMessage.vedlegg
        }
    }
})
