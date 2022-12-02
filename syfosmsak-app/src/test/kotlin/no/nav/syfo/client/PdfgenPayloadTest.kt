package no.nav.syfo.client

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.SporsmalSvar
import org.amshove.kluent.shouldBeEqualTo

class PdfgenPayloadTest : FunSpec({
    context("mapToSykmeldingUtenUlovligeTegn") {
        test("mapToSykmeldingUtenUlovligeTegn fjerner zwnbsp") {
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

            oppdatertSykmelding.utdypendeOpplysninger["6.2"]?.get("6.2.1")?.svar shouldBeEqualTo "_ •• l\\iiJr~Svar med skumle tegn"
        }
        test("mapToSykmeldingUtenUlovligeTegn endrer ikke sykmelding uten ulovlige tegn") {
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

            oppdatertSykmelding.utdypendeOpplysninger["6.2"]?.get("6.2.1")?.svar shouldBeEqualTo "Svar uten skumle tegn"
        }
    }
})
