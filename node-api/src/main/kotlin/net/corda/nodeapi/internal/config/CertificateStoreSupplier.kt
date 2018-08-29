package net.corda.nodeapi.internal.config

import net.corda.nodeapi.internal.crypto.X509KeyStore
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

// TODO sollecitom see if you can make this private API wise
class FileBasedCertificateStoreSupplier(val path: Path, val password: String) : CertificateStoreSupplier {

    // TODO sollecitom check if we need caching
    //    private var cached: CertificateStore? = null
    //
    //    override fun get(createNew: Boolean): CertificateStore {
    //
    //        synchronized(this) {
    //            if (cached == null) {
    //                cached = DelegatingCertificateStore(X509KeyStore.fromFile(path, password, createNew), password)
    //            }
    //            return cached!!
    //        }
    //    }

    override fun get(createNew: Boolean): CertificateStore = DelegatingCertificateStore(X509KeyStore.fromFile(path, password, createNew), password)
}

private class DelegatingCertificateStore(override val value: X509KeyStore, override val password: String) : CertificateStore