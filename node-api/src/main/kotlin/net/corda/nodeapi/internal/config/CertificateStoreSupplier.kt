package net.corda.nodeapi.internal.config

import java.io.IOException
import java.nio.file.Path

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

// TODO replace reference to FileBasedCertificateStoreSupplier with CertificateStoreSupplier, after coming up with a way of passing certificate stores to Artemis.
class FileBasedCertificateStoreSupplier(val path: Path, val password: String) : CertificateStoreSupplier {

    override fun get(createNew: Boolean) = CertificateStore.fromFile(path, password, createNew)
}