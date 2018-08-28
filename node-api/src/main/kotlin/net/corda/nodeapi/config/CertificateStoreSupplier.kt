package net.corda.nodeapi.config

import java.io.IOException

// TODO sollecitom see if you can make this private API wise
interface CertificateStoreSupplier {

    fun get(createNew: Boolean = false): CertificateStore

    fun getOptional(): CertificateStore? {

        return try {
            get()
        } catch (e: IOException) {
            null
        }
    }
}