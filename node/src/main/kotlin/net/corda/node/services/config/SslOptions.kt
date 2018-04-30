package net.corda.node.services.config

import net.corda.nodeapi.internal.config.SSLConfiguration
import java.nio.file.Path
import java.nio.file.Paths

// TODO: we use both SSL and Ssl for names. We should pick one of them, or even better change to TLS
data class SslOptions(override val certificatesDirectory: Path,
                      override val keyStorePassword: String,
                      override val trustStorePassword: String,
                      override val crlCheckSoftFail: Boolean) : SSLConfiguration {

    fun copy(certificatesDirectory: String = this.certificatesDirectory.toString(),
             keyStorePassword: String = this.keyStorePassword,
             trustStorePassword: String = this.trustStorePassword,
             crlCheckSoftFail: Boolean = this.crlCheckSoftFail): SslOptions = copy(
            certificatesDirectory = certificatesDirectory.toAbsolutePath(),
            keyStorePassword = keyStorePassword,
            trustStorePassword = trustStorePassword,
            crlCheckSoftFail = crlCheckSoftFail)
}

private fun String.toAbsolutePath() = Paths.get(this).toAbsolutePath()