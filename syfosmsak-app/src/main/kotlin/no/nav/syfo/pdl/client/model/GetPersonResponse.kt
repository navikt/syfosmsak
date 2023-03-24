package no.nav.syfo.pdl.client.model

data class GetPersonResponse(
    val data: ResponseData,
    val errors: List<ResponseError>?,
)

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?,
)

data class ResponseData(
    val hentPerson: HentPerson?,
    val hentIdenter: HentIdenter?,
)

data class HentIdenter(
    val identer: List<PdlIdent>,
)

data class PdlIdent(val gruppe: String, val ident: String)

data class ErrorLocation(
    val line: String?,
    val column: String?,
)

data class ErrorExtension(
    val code: String?,
    val classification: String?,
)

data class HentPerson(
    val navn: List<Navn>?,
    val adressebeskyttelse: List<Adressebeskyttelse>?,
)

data class Adressebeskyttelse(
    val gradering: String,
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)
