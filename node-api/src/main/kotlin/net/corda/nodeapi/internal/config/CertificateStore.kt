package net.corda.nodeapi.internal.config

import net.corda.core.internal.outputStream
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.addOrReplaceCertificate
import java.io.OutputStream
import java.nio.file.OpenOption
import java.nio.file.Path
import java.security.cert.X509Certificate

interface CertificateStore {

    val value: X509KeyStore
    val password: String

    fun writeTo(stream: OutputStream) = value.internal.store(stream, password.toCharArray())

    fun writeTo(path: Path, vararg options: OpenOption) = path.outputStream(*options)

    fun update(action: X509KeyStore.() -> Unit) {
        val result = action.invoke(value)
        value.save()
        return result
    }

    fun <RESULT> query(action: X509KeyStore.() -> RESULT): RESULT {
        return action.invoke(value)
    }

    operator fun set(alias: String, certificate: X509Certificate) {

        update {
            internal.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, certificate)
        }
    }

    /**
     * @throws IllegalArgumentException if no certificate for the alias is found, or if the certificate is not an [X509Certificate].
     */
    operator fun get(alias: String): X509Certificate {

        return query {
            getCertificate(alias)
        }
    }

    operator fun contains(alias: String): Boolean = value.contains(alias)
}