package no.nav.syfo

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import no.nav.syfo.validation.validatePersonAndDNumber
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

val personNumberDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyy")

internal class ValidatePersonNumberTest {
    @Test
    internal fun `Should check validate as fnr`() {
        val generateFnr = generatePersonNumber(LocalDate.of(1991, 1, 1), false)
        val validFnr = validatePersonAndDNumber(generateFnr)
        Assertions.assertEquals(true, validFnr)
    }

    @Test
    internal fun `Should check validate as d-number`() {
        val generateDnumber = generatePersonNumber(LocalDate.of(1991, 1, 1), true)
        val validdnumber = validatePersonAndDNumber(generateDnumber)
        Assertions.assertEquals(true, validdnumber)
    }
}

fun generatePersonNumber(bornDate: LocalDate, useDNumber: Boolean = false): String {
    val personDate =
        bornDate.format(personNumberDateFormat).let {
            if (useDNumber) "${it[0] + 4}${it.substring(1)}" else it
        }
    return (if (bornDate.year >= 2000) (75011..99999) else (11111..50099))
        .map { "$personDate$it" }
        .first { validatePersonAndDNumber(it) }
}
