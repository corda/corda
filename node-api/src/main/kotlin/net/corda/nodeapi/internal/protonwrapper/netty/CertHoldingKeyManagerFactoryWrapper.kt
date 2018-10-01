package net.corda.nodeapi.internal.protonwrapper.netty

import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.*


class CertHoldingKeyManagerFactorySpiWrapper(private val factorySpi: KeyManagerFactorySpi) : KeyManagerFactorySpi() {
    override fun engineInit(p0: KeyStore?, p1: CharArray?) {
        val engineInitMethod = KeyManagerFactorySpi::class.java.getDeclaredMethod("engineInit", KeyStore::class.java, CharArray::class.java)
        engineInitMethod.isAccessible = true
        engineInitMethod.invoke(factorySpi, p0, p1)
    }

    override fun engineInit(p0: ManagerFactoryParameters?) {
        val engineInitMethod = KeyManagerFactorySpi::class.java.getDeclaredMethod("engineInit", ManagerFactoryParameters::class.java)
        engineInitMethod.isAccessible = true
        engineInitMethod.invoke(factorySpi, p0)
    }

    private fun getKeyManagersImpl(): Array<KeyManager> {
        val engineGetKeyManagersMethod = KeyManagerFactorySpi::class.java.getDeclaredMethod("engineGetKeyManagers")
        engineGetKeyManagersMethod.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val keyManagers = engineGetKeyManagersMethod.invoke(factorySpi) as Array<KeyManager>
        return if (factorySpi is CertHoldingKeyManagerFactorySpiWrapper) keyManagers else keyManagers.mapNotNull {
            @Suppress("USELESS_CAST") // the casts to KeyManager are not useless - without them, the typed array will be of type Any
            when (it) {
                is X509ExtendedKeyManager -> AliasProvidingExtendedKeyMangerWrapper(it) as KeyManager
                is X509KeyManager -> AliasProvidingKeyMangerWrapperImpl(it) as KeyManager
                else -> null
            }
        }.toTypedArray()
    }

    private val keyManagers = lazy { getKeyManagersImpl() }

    override fun engineGetKeyManagers(): Array<KeyManager> {
        return keyManagers.value
    }
}

/**
 * You can wrap a key manager factory in this class if you need to get the cert chain currently used to identify or
 * verify. When using for TLS channels, make sure to wrap the (singleton) factory separately on each channel, as
 * the wrapper is not thread safe as in it will return the last used alias/cert chain and has itself no notion
 * of belonging to a certain channel.
 */
class CertHoldingKeyManagerFactoryWrapper(factory: KeyManagerFactory) : KeyManagerFactory(getFactorySpi(factory), factory.provider, factory.algorithm) {
    companion object {
        private fun getFactorySpi(factory: KeyManagerFactory): KeyManagerFactorySpi {
            val spiField = KeyManagerFactory::class.java.getDeclaredField("factorySpi")
            spiField.isAccessible = true
            return CertHoldingKeyManagerFactorySpiWrapper(spiField.get(factory) as KeyManagerFactorySpi)
        }
    }

    fun getCurrentCertChain(): Array<out X509Certificate>? {
        val keyManager = keyManagers.firstOrNull()
        val alias = if (keyManager is AliasProvidingKeyMangerWrapper) keyManager.lastAlias else null
        return if (alias != null && keyManager is X509KeyManager) {
            keyManager.getCertificateChain(alias)
        } else null
    }

}