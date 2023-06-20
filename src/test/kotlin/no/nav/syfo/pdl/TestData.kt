package no.nav.syfo.pdl

import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentIdenter
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.Navn
import no.nav.syfo.pdl.client.model.PdlIdent
import no.nav.syfo.pdl.client.model.ResponseData

fun getPdlResponse(): GetPersonResponse {
    return GetPersonResponse(
        ResponseData(
            hentPerson =
                HentPerson(listOf(Navn("fornavn", null, "etternavn")), adressebeskyttelse = null),
            hentIdenter = HentIdenter(listOf(PdlIdent(ident = "987654321", gruppe = "AKTORID"))),
        ),
        errors = null,
    )
}
