package no.nav.syfo.client

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.model.VedleggMessage
import no.nav.syfo.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class DokArkivClientKtTest : Spek({
    describe("VedleggToPdf") {
        it("Returnerer samme vedlegg hvis vedlegget er PDF") {
            val vedleggMessage: VedleggMessage = objectMapper.readValue(DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_pdf.json"))
            val gosysVedlegg = toGosysVedlegg(vedleggMessage.vedlegg)

            val oppdatertVedlegg = vedleggToPDF(gosysVedlegg)

            oppdatertVedlegg shouldBeEqualTo gosysVedlegg
        }
        it("Konverterer til PDF hvis vedlegget ikke er PDF") {
            val vedleggMessage: VedleggMessage = objectMapper.readValue(DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_bilde.json"))
            val gosysVedlegg = toGosysVedlegg(vedleggMessage.vedlegg)

            val oppdatertVedlegg = vedleggToPDF(gosysVedlegg)

            oppdatertVedlegg shouldNotBeEqualTo gosysVedlegg
            oppdatertVedlegg.contentType shouldBeEqualTo "application/pdf"
            oppdatertVedlegg.description shouldBeEqualTo vedleggMessage.vedlegg.description
        }
    }
})
