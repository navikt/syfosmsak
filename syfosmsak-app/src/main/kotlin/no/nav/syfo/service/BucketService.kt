package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.storage.Storage
import no.nav.syfo.log
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.model.VedleggMessage
import no.nav.syfo.objectMapper

class BucketService(
    private val bucketName: String,
    private val storage: Storage
) {
    fun getVedleggFromBucket(key: String): Vedlegg {
        val vedleggBlob = storage.get(bucketName, key)

        if (vedleggBlob == null) {
            log.error("Fant ikke vedlegg med key $key")
            throw RuntimeException("Fant ikke vedlegg med key $key")
        } else {
            log.info("Fant vedlegg med key $key")
            return objectMapper.readValue<VedleggMessage>(vedleggBlob.getContent()).vedlegg
        }
    }
}
