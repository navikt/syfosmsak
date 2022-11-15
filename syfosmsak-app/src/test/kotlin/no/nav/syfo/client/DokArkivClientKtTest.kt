package no.nav.syfo.client

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.model.VedleggMessage
import no.nav.syfo.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo

class DokArkivClientKtTest : FunSpec({
    context("VedleggToPdf") {
        test("Returnerer samme vedlegg hvis vedlegget er PDF") {
            val vedleggMessage: VedleggMessage = objectMapper.readValue(DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_pdf.json"))
            val gosysVedlegg = toGosysVedlegg(vedleggMessage.vedlegg)

            val oppdatertVedlegg = vedleggToPDF(gosysVedlegg)

            oppdatertVedlegg shouldBeEqualTo gosysVedlegg
        }
        test("Konverterer til PDF hvis vedlegget ikke er PDF") {
            val vedleggMessage: VedleggMessage = objectMapper.readValue(DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_bilde.json"))
            val gosysVedlegg = toGosysVedlegg(vedleggMessage.vedlegg)

            val oppdatertVedlegg = vedleggToPDF(gosysVedlegg)

            oppdatertVedlegg shouldNotBeEqualTo gosysVedlegg
            oppdatertVedlegg.contentType shouldBeEqualTo "application/pdf"
            oppdatertVedlegg.description shouldBeEqualTo vedleggMessage.vedlegg.description
        }

        test("Skal legge på padding dersom hpr er under 9 siffer") {
            val hprnummmer = hprnummerMedRiktigLengdeOgFormat("02345678 ".trim())

            hprnummmer shouldBeEqualTo "002345678"
        }
        test("Skal fjerne - fra hprnummer") {
            val hprnummmer = hprnummerMedRiktigLengdeOgFormat("-02345678".trim())

            hprnummmer shouldBeEqualTo "002345678"
        }
        test("Skal fjerne bokstaver fra hprnummer") {
            val hprnummmer = hprnummerMedRiktigLengdeOgFormat("0A234B5678".trim())

            hprnummmer shouldBeEqualTo "002345678"
        }
    }
})
