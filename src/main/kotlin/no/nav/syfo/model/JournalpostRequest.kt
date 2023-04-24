package no.nav.syfo.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JournalpostRequest(
    val avsenderMottaker: AvsenderMottaker? = null,
    val behandlingstema: String? = null,
    var bruker: Bruker? = null,
    val dokumenter: List<Dokument>,
    val eksternReferanseId: String? = null,
    val journalfoerendeEnhet: String? = null,
    val journalpostType: String? = null,
    val kanal: String? = null,
    val sak: Sak? = null,
    val tema: String? = null,
    val tittel: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AvsenderMottaker(
    val id: String? = null,
    val idType: String? = null,
    val land: String? = null,
    val navn: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Bruker(
    val id: String,
    val idType: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Dokument(
    val brevkode: String? = null,
    val dokumentKategori: String? = null,
    val dokumentvarianter: List<Dokumentvarianter>,
    val tittel: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Dokumentvarianter(
    val filnavn: String,
    val filtype: String,
    val fysiskDokument: ByteArray,
    val variantformat: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Sak(
    val sakstype: String? = null,
)
