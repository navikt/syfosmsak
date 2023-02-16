package no.nav.syfo.client

import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.SporsmalSvar
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class PdfgenPayloadTest {

    @Test
    internal fun `mapToSykmeldingUtenUlovligeTegn fjerner zwnbsp`() {
        val utdypendeOpplysninger = mapOf(
            "6.2" to mapOf(
                "6.2.1" to SporsmalSvar(
                    sporsmal = "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon.",
                    svar = "\uFEFF_ •• l\\iiJr~Svar med skumle tegn",
                    restriksjoner = emptyList()
                )
            )
        )
        val sykmelding = generateSykmelding().copy(utdypendeOpplysninger = utdypendeOpplysninger)

        val oppdatertSykmelding = mapToSykmeldingUtenUlovligeTegn(sykmelding)

        Assertions.assertEquals(
            "_ •• l\\iiJr~Svar med skumle tegn",
            oppdatertSykmelding.utdypendeOpplysninger["6.2"]?.get("6.2.1")?.svar
        )
    }

    @Test
    internal fun `mapToSykmeldingUtenUlovligeTegn endrer ikke sykmelding uten ulovlige tegn`() {
        val utdypendeOpplysninger = mapOf(
            "6.2" to mapOf(
                "6.2.1" to SporsmalSvar(
                    sporsmal = "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon.",
                    svar = "Svar uten skumle tegn",
                    restriksjoner = emptyList()
                )
            )
        )
        val sykmelding = generateSykmelding().copy(utdypendeOpplysninger = utdypendeOpplysninger)

        val oppdatertSykmelding = mapToSykmeldingUtenUlovligeTegn(sykmelding)

        Assertions.assertEquals(
            "Svar uten skumle tegn",
            oppdatertSykmelding.utdypendeOpplysninger["6.2"]?.get("6.2.1")?.svar
        )
    }
}
