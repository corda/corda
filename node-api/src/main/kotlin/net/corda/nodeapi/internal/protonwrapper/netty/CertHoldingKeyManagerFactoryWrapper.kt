package net.corda.nodeapi.internal.protonwrapper.netty

import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.KeyManagerFactorySpi
import javax.net.ssl.ManagerFactoryParameters
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509KeyManager

private class CertHoldingKeyManagerFactorySpiWrapper(private val keyManagerFactory: KeyManagerFactory,
                                                     private val amqpConfig: AMQPConfiguration) : KeyManagerFactorySpi() {
    override fun engineInit(keyStore: KeyStore?, password: CharArray?) {
        keyManagerFactory.init(keyStore, password)
    }

    override fun engineInit(spec: ManagerFactoryParameters?) {
        keyManagerFactory.init(spec)
    }

    private fun getKeyManagersImpl(): Array<KeyManager> {
        return keyManagerFactory.keyManagers.map {
            val aliasProvidingKeyManager = getDefaultKeyManager(it)
            // Use the SNIKeyManager if keystore has several entries and only for clients and non-openSSL servers.
            // Condition of using SNIKeyManager: if its client, or JDKSsl server.
            val isClient = amqpConfig.sourceX500Name != null
            val enableSNI = amqpConfig.enableSNI && amqpConfig.keyStore.aliases().size > 1
            if (enableSNI && (isClient || !amqpConfig.useOpenSsl)) {
                SNIKeyManager(aliasProvidingKeyManager as X509ExtendedKeyManager, amqpConfig)
            } else {
                aliasProvidingKeyManager
            }
        }.toTypedArray()
    }

    private fun getDefaultKeyManager(keyManager: KeyManager): KeyManager {
        return when (keyManager) {
            is X509ExtendedKeyManager -> AliasProvidingExtendedKeyMangerWrapper(keyManager)
            is X509KeyManager -> AliasProvidingKeyMangerWrapperImpl(keyManager)
            else -> throw UnsupportedOperationException("Supported key manager types are: X509ExtendedKeyManager, X509KeyManager. Provided ${keyManager::class.java.name}")
        }
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
class CertHoldingKeyManagerFactoryWrapper(factory: KeyManagerFactory, amqpConfig: AMQPConfiguration) : KeyManagerFactory(
        CertHoldingKeyManagerFactorySpiWrapper(factory, amqpConfig),
        factory.provider,
        factory.algorithm
) {
    fun getCurrentCertChain(): Array<out X509Certificate>? {
        val keyManager = keyManagers.firstOrNull()
        val alias = if (keyManager is AliasProvidingKeyMangerWrapper) keyManager.lastAlias else null
        return if (alias != null && keyManager is X509KeyManager) {
            keyManager.getCertificateChain(alias)
        } else null
    }
}
