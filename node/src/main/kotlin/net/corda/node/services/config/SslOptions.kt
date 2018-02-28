package net.corda.node.services.config

import net.corda.nodeapi.internal.config.SSLConfiguration
import java.nio.file.Path
import java.nio.file.Paths

data class SslOptions(override val certificatesDirectory: Path, override val keyStorePassword: String, override val trustStorePassword: String) : SSLConfiguration {

    fun copy(certificatesDirectory: String = this.certificatesDirectory.toString(), keyStorePassword: String = this.keyStorePassword, trustStorePassword: String = this.trustStorePassword): SslOptions = copy(certificatesDirectory = certificatesDirectory.toAbsolutePath(), keyStorePassword = keyStorePassword, trustStorePassword = trustStorePassword)
}

private fun String.toAbsolutePath() = Paths.get(this).toAbsolutePath()