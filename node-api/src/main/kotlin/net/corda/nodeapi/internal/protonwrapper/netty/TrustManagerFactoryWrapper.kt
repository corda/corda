package net.corda.nodeapi.internal.protonwrapper.netty

import java.security.KeyStore
import javax.net.ssl.ManagerFactoryParameters
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.TrustManagerFactorySpi
import javax.net.ssl.X509ExtendedTrustManager

class LoggingTrustManagerFactorySpiWrapper(private val trustManagerFactory: TrustManagerFactory) : TrustManagerFactorySpi() {
    override fun engineGetTrustManagers(): Array<TrustManager> {
        return trustManagerFactory.trustManagers
                .mapNotNull { (it as? X509ExtendedTrustManager)?.let(::LoggingTrustManagerWrapper) }
                .toTypedArray()
    }

    override fun engineInit(ks: KeyStore?) {
        trustManagerFactory.init(ks)
    }

    override fun engineInit(spec: ManagerFactoryParameters?) {
        trustManagerFactory.init(spec)
    }
}

class LoggingTrustManagerFactoryWrapper(factory: TrustManagerFactory) : TrustManagerFactory(
        LoggingTrustManagerFactorySpiWrapper(factory),
        factory.provider,
        factory.algorithm
)
