package no.nav.syfo.client

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.model.VedleggMessage
import no.nav.syfo.objectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class DokArkivClientKtTest {

    @Test
    internal fun `Returnerer samme vedlegg hvis vedlegget er PDF`() {
        val vedleggMessage: VedleggMessage =
            objectMapper.readValue(
                DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_pdf.json")!!
            )
        val gosysVedlegg = toGosysVedlegg(vedleggMessage.vedlegg)

        val oppdatertVedlegg = vedleggToPDF(gosysVedlegg)

        Assertions.assertEquals(gosysVedlegg, oppdatertVedlegg)
    }

    @Test
    internal fun `Konverterer til PDF hvis vedlegget ikke er PDF`() {
        val vedleggMessage: VedleggMessage =
            objectMapper.readValue(
                DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_bilde.json")!!
            )
        val gosysVedlegg = toGosysVedlegg(vedleggMessage.vedlegg)

        val oppdatertVedlegg = vedleggToPDF(gosysVedlegg)

        Assertions.assertNotEquals(gosysVedlegg, oppdatertVedlegg)
        Assertions.assertEquals("application/pdf", oppdatertVedlegg.contentType)
        Assertions.assertEquals(vedleggMessage.vedlegg.description, oppdatertVedlegg.description)
    }

    @Test
    internal fun `Skal legge paa padding dersom hpr er under 9 siffer`() {
        val hprnummmer = hprnummerMedRiktigLengdeOgFormat("02345678 ".trim())

        Assertions.assertEquals("002345678", hprnummmer)
    }

    @Test
    internal fun `Skal fjerne - fra hprnummer`() {
        val hprnummmer = hprnummerMedRiktigLengdeOgFormat("-02345678".trim())

        Assertions.assertEquals("002345678", hprnummmer)
    }

    @Test
    internal fun `Skal fjerne bokstaver fra hprnummer`() {
        val hprnummmer = hprnummerMedRiktigLengdeOgFormat("0A234B5678".trim())

        Assertions.assertEquals("002345678", hprnummmer)
    }
}
