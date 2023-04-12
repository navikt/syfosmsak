package no.nav.syfo.validation

val lookup1: IntArray = intArrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2, 0)
val lookup2: IntArray = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)

fun validatePersonDNumberMod11(personNumber: String): Boolean {
    if (personNumber.length != 11) {
        return false
    }

    var checksum1 = 0
    var checksum2 = 0

    for (i in 0..9) {
        val currNum = (personNumber[i] - '0')
        checksum1 += currNum * lookup1[i]
        checksum2 += currNum * lookup2[i]
    }

    checksum1 %= 11
    checksum2 %= 11

    val checksum1Final = if (checksum1 == 0) 0 else 11 - checksum1
    val checksum2Final = if (checksum2 == 0) 0 else 11 - checksum2

    return checksum1Final != 10 &&
        personNumber[9] - '0' == checksum1Final &&
        personNumber[10] - '0' == checksum2Final
}

fun validatePersonAndPersonDNumberRange(personNumber: String): Boolean {
    val personNumberBornDay = personNumber.substring(0, 2)
    return validatePersonNumberRange(personNumberBornDay) || validatePersonDNumberRange(personNumberBornDay)
}

fun validatePersonAndDNumber(personNumber: String): Boolean =
    validatePersonDNumberMod11(personNumber) && validatePersonAndPersonDNumberRange(personNumber)

fun validatePersonNumberRange(personNumberFirstAndSecoundChar: String): Boolean {
    return personNumberFirstAndSecoundChar.toInt() in 1..31
}

fun validatePersonDNumberRange(personNumberFirstAndSecoundChar: String): Boolean {
    return personNumberFirstAndSecoundChar.toInt() in 41..71
}
