package net.corda.nodeapi.internal.config

import net.corda.core.internal.div
import java.nio.file.Path

interface SSLConfiguration {
    val keyStorePassword: String
    val trustStorePassword: String
    val certificatesDirectory: Path
    val sslKeystore: Path get() = certificatesDirectory / "sslkeystore.jks"
    val nodeKeystore: Path get() = certificatesDirectory / "nodekeystore.jks"
    val trustStoreFile: Path get() = certificatesDirectory / "truststore.jks"
}

interface NodeSSLConfiguration : SSLConfiguration {
    val baseDirectory: Path
    override val certificatesDirectory: Path get() = baseDirectory / "certificates"
    // TODO This will be removed. Instead we will just check against the truststore, which will be provided out-of-band, along with its password
    val rootCertFile: Path get() = certificatesDirectory / "rootcert.pem"
}
