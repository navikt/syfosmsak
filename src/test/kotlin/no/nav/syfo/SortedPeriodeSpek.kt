package no.nav.syfo

import no.nav.syfo.client.createTittleJournalpost
import no.nav.syfo.client.sortedSykmeldingPeriodeFOMDate
import no.nav.syfo.client.sortedSykmeldingPeriodeTOMDate
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime

object SortedPeriodeSpek : Spek({

    fun getReceivedSykemelding(perioder: List<Periode> = listOf(generatePeriode())): ReceivedSykmelding {
        return ReceivedSykmelding(
            sykmelding = generateSykmelding(perioder = perioder),
            personNrPasient = "1231231",
            tlfPasient = "1323423424",
            personNrLege = "123134",
            navLogId = "4d3fad98-6c40-47ec-99b6-6ca7c98aa5ad",
            msgId = "06b2b55f-c2c5-4ee0-8e0a-6e252ec2a550",
            legekontorOrgNr = "444333",
            legekontorOrgName = "Helese sentar",
            legekontorHerId = "33",
            legekontorReshId = "1313",
            mottattDato = LocalDateTime.now(),
            rulesetVersion = "2",
            fellesformat = "",
            tssid = "13415",
            merknader = null,
            partnerreferanse = null,
            legeHelsepersonellkategori = null,
            legeHprNr = null
        )
    }

    describe("Testing sorting the fom and tom of a periode") {

        it("Should choose the correct fom and tom with one Periode") {

            val periode = generatePeriode(fom = LocalDate.of(2019, 1, 1), tom = LocalDate.of(2019, 1, 2))
            val receivedSykmelding = getReceivedSykemelding(listOf(periode))

            receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeFOMDate()
                .first().fom shouldBeEqualTo LocalDate.of(2019, 1, 1)
            receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeTOMDate()
                .last().tom shouldBeEqualTo LocalDate.of(
                2019,
                1,
                2
            )
        }

        it("Should choose the correct fom and tom with one Periode") {

            val periode = generatePeriode(fom = LocalDate.of(2019, 1, 1), tom = LocalDate.of(2019, 1, 2))
            val periode2 = generatePeriode(fom = LocalDate.of(2019, 1, 3), tom = LocalDate.of(2019, 1, 9))

            val receivedSykmelding = getReceivedSykemelding(listOf(periode2, periode))

            receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeFOMDate()
                .first().fom shouldBeEqualTo LocalDate.of(2019, 1, 1)
            receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeTOMDate()
                .last().tom shouldBeEqualTo LocalDate.of(
                2019,
                1,
                9
            )
        }

        it("Should get correct title for sykemelding with one Periode") {
            val periode = generatePeriode(fom = LocalDate.of(2019, 1, 1), tom = LocalDate.of(2019, 1, 2))
            val receivedSykmelding = getReceivedSykemelding(listOf(periode))

            val title = createTittleJournalpost(ValidationResult(Status.OK, emptyList()), receivedSykmelding)
            title shouldBeEqualTo "Sykmelding 01.01.2019 - 02.01.2019"
        }

        it("Should get correct title for sykemelding with two Periode") {
            val periode = generatePeriode(fom = LocalDate.of(2019, 1, 1), tom = LocalDate.of(2019, 1, 2))
            val periode2 = generatePeriode(fom = LocalDate.of(2019, 1, 3), tom = LocalDate.of(2019, 1, 4))
            val receivedSykmelding = getReceivedSykemelding(listOf(periode2, periode))

            val title = createTittleJournalpost(ValidationResult(Status.OK, emptyList()), receivedSykmelding)
            title shouldBeEqualTo "Sykmelding 01.01.2019 - 04.01.2019"
        }

        it("Should get correct title for sykemelding with one gradert") {
            val periode = generatePeriode(fom = LocalDate.of(2019, 1, 1), tom = LocalDate.of(2019, 1, 2))
            val periode2 = generatePeriode(
                fom = LocalDate.of(2019, 1, 3),
                tom = LocalDate.of(2019, 1, 4),
                gradert = generateGradert(reisetilskudd = true, grad = 50)
            )

            val receivedSykmelding = getReceivedSykemelding(listOf(periode2, periode))
            val title = createTittleJournalpost(ValidationResult(Status.OK, emptyList()), receivedSykmelding)
            title shouldBeEqualTo "Sykmelding 01.01.2019 - 04.01.2019"
        }

        it("Should get correct title for Sykemelding with several Perioder") {
            val periode = generatePeriode(fom = LocalDate.of(2019, 1, 1), tom = LocalDate.of(2019, 1, 2))
            val periode2 = generatePeriode(
                fom = LocalDate.of(2019, 1, 3),
                tom = LocalDate.of(2019, 1, 4),
                gradert = generateGradert(reisetilskudd = true, grad = 50)
            )
            val periode3 = generatePeriode(fom = LocalDate.of(2019, 1, 5), tom = LocalDate.of(2019, 2, 1))

            val receivedSykmelding = getReceivedSykemelding(listOf(periode2, periode3, periode))
            val title = createTittleJournalpost(ValidationResult(Status.OK, emptyList()), receivedSykmelding)
            title shouldBeEqualTo "Sykmelding 01.01.2019 - 01.02.2019"
        }

        it("Should get Avvist Sykemelding with correct fom and tom") {
            val periode = generatePeriode(fom = LocalDate.of(2019, 1, 1), tom = LocalDate.of(2019, 1, 2))
            val periode2 = generatePeriode(
                fom = LocalDate.of(2019, 1, 3),
                tom = LocalDate.of(2019, 1, 4),
                gradert = generateGradert(reisetilskudd = true, grad = 50)
            )
            val periode3 = generatePeriode(fom = LocalDate.of(2019, 1, 5), tom = LocalDate.of(2019, 2, 1))

            val receivedSykmelding = getReceivedSykemelding(listOf(periode2, periode3, periode))
            val title = createTittleJournalpost(ValidationResult(Status.INVALID, emptyList()), receivedSykmelding)
            title shouldBeEqualTo "Avvist Sykmelding 01.01.2019 - 01.02.2019"
        }
    }
})
