package net.corda.nodeapi.internal.config

import net.corda.nodeapi.config.FileBasedCertificateStoreSupplier

interface SslConfiguration {

    val keyStore: FileBasedCertificateStoreSupplier?
    val trustStore: FileBasedCertificateStoreSupplier?
}

// TODO sollecitom move this?
interface TwoWaySslConfiguration : SslConfiguration {

    override val keyStore: FileBasedCertificateStoreSupplier
    override val trustStore: FileBasedCertificateStoreSupplier
}

// TODO sollecitom move this? maybe make it private with a factory method in SSLConfiguration?
class TwoWaySslOptions(override val keyStore: FileBasedCertificateStoreSupplier, override val trustStore: FileBasedCertificateStoreSupplier) : TwoWaySslConfiguration
