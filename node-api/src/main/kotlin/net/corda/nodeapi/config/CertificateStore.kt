package net.corda.nodeapi.config

import net.corda.core.internal.outputStream
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.X509KeyStore
import java.io.OutputStream
import java.nio.file.OpenOption
import java.nio.file.Path
import java.security.cert.X509Certificate

// TODO sollecitom see if you can make this private API wise
// TODO sollecitom see if you can make the password private here
interface CertificateStore {

    val value: X509KeyStore
    val password: String

    fun writeTo(stream: OutputStream) = value.internal.store(stream, password.toCharArray())

    fun writeTo(path: Path, vararg options: OpenOption) = path.outputStream(*options)

    fun <RESULT> update(action: X509KeyStore.() -> RESULT): RESULT {
        val result = action.invoke(value)
        value.save()
        return result
    }

    // TODO sollecitom introduce a `query` equivalent of `update` that doesn't save - remove query functions apart from `contains`

    fun getCertificate(alias: String): X509Certificate = value.getCertificate(alias)

    operator fun contains(alias: String): Boolean = value.contains(alias)

    fun getCertificateChain(alias: String): List<X509Certificate> = value.getCertificateChain(alias)

    fun getCertificateAndKeyPair(alias: String, keyPassword: String = password): CertificateAndKeyPair = value.getCertificateAndKeyPair(alias, keyPassword)
}