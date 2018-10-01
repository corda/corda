package net.corda.nodeapi.internal.protonwrapper.netty

import java.security.KeyStore
import javax.net.ssl.*

class LoggingTrustManagerFactorySpiWrapper(private val factorySpi: TrustManagerFactorySpi) : TrustManagerFactorySpi() {
    override fun engineGetTrustManagers(): Array<TrustManager> {
        val engineGetTrustManagersMethod = TrustManagerFactorySpi::class.java.getDeclaredMethod("engineGetTrustManagers")
        engineGetTrustManagersMethod.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val trustManagers = engineGetTrustManagersMethod.invoke(factorySpi) as Array<TrustManager>
        return if (factorySpi is LoggingTrustManagerFactorySpiWrapper) trustManagers else trustManagers.filterIsInstance(X509ExtendedTrustManager::class.java).map { LoggingTrustManagerWrapper(it) }.toTypedArray()
    }

    override fun engineInit(p0: KeyStore?) {
        val engineInitMethod = TrustManagerFactorySpi::class.java.getDeclaredMethod("engineInit", KeyStore::class.java)
        engineInitMethod.isAccessible = true
        engineInitMethod.invoke(factorySpi, p0)
    }

    override fun engineInit(p0: ManagerFactoryParameters?) {
        val engineInitMethod = TrustManagerFactorySpi::class.java.getDeclaredMethod("engineInit", ManagerFactoryParameters::class.java)
        engineInitMethod.isAccessible = true
        engineInitMethod.invoke(factorySpi, p0)
    }
}

class LoggingTrustManagerFactoryWrapper(factory: TrustManagerFactory) : TrustManagerFactory(getFactorySpi(factory), factory.provider, factory.algorithm) {
    companion object {
        private fun getFactorySpi(factory: TrustManagerFactory): TrustManagerFactorySpi {
            val spiField = TrustManagerFactory::class.java.getDeclaredField("factorySpi")
            spiField.isAccessible = true
            return LoggingTrustManagerFactorySpiWrapper(spiField.get(factory) as TrustManagerFactorySpi)
        }
    }
}