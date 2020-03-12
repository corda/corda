package net.corda.nodeapi.internal.protonwrapper.netty

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.x509
import org.slf4j.MDC
import java.net.Socket
import java.security.Principal
import javax.net.ssl.SNIMatcher
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509KeyManager

internal class SNIKeyManager(private val keyManager: X509ExtendedKeyManager, private val amqpConfig: AMQPConfiguration) : X509ExtendedKeyManager(), X509KeyManager by keyManager, AliasProvidingKeyMangerWrapper {

    companion object {
        private val log = contextLogger()
    }

    override var lastAlias: String? = null

    private fun withMDC(block: () -> Unit) {
        val oldMDC = MDC.getCopyOfContextMap()
        try {
            MDC.put("lastAlias", lastAlias)
            MDC.put("isServer", amqpConfig.sourceX500Name.isNullOrEmpty().toString())
            MDC.put("sourceX500Name", amqpConfig.sourceX500Name)
            MDC.put("useOpenSSL", amqpConfig.useOpenSsl.toString())
            block()
        } finally {
            MDC.setContextMap(oldMDC)
        }
    }

    private fun logDebugWithMDC(msg: () -> String) {
        if (log.isDebugEnabled) {
            withMDC { log.debug(msg()) }
        }
    }

    override fun chooseClientAlias(keyType: Array<out String>, issuers: Array<out Principal>, socket: Socket): String? {
        return storeIfNotNull { chooseClientAlias(amqpConfig.keyStore, amqpConfig.sourceX500Name) }
    }

    override fun chooseEngineClientAlias(keyType: Array<out String>, issuers: Array<out Principal>, engine: SSLEngine): String? {
        return storeIfNotNull { chooseClientAlias(amqpConfig.keyStore, amqpConfig.sourceX500Name) }
    }

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket): String? {
        return storeIfNotNull {
            val matcher = (socket as SSLSocket).sslParameters.sniMatchers.first()
            chooseServerAlias(keyType, issuers, matcher)
        }
    }

    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? {
        return storeIfNotNull {
            val matcher = engine?.sslParameters?.sniMatchers?.first()
            chooseServerAlias(keyType, issuers, matcher)
        }
    }

    private fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, matcher: SNIMatcher?): String? {
        val aliases = keyManager.getServerAliases(keyType, issuers)
        if (aliases == null || aliases.isEmpty()) {
            logDebugWithMDC { "Keystore doesn't contain any aliases for key type $keyType and issuers $issuers." }
            return null
        }

        log.debug("Checking aliases: $aliases.")
        matcher?.let {
            val matchedAlias = (it as ServerSNIMatcher).matchedAlias
            if (aliases.contains(matchedAlias)) {
                logDebugWithMDC { "Found match for $matchedAlias." }
                return matchedAlias
            }
        }

        logDebugWithMDC { "Unable to find a matching alias." }
        return null
    }

    private fun chooseClientAlias(keyStore: CertificateStore, clientLegalName: String?): String? {
        clientLegalName?.let {
            val aliases = keyStore.aliases()
            if (aliases.isEmpty()) {
                logDebugWithMDC { "Keystore doesn't contain any entries." }
            }
            aliases.forEach { alias ->
                val x500Name = keyStore[alias].x509.subjectX500Principal
                val aliasCordaX500Name = CordaX500Name.build(x500Name)
                val clientCordaX500Name = CordaX500Name.parse(it)
                if (clientCordaX500Name == aliasCordaX500Name) {
                    logDebugWithMDC { "Found alias $alias for $clientCordaX500Name." }
                    return alias
                }
            }
        }

        return null
    }

    private fun storeIfNotNull(func: () -> String?): String? {
        val alias = func()
        if (alias != null) {
            lastAlias = alias
        }
        return alias
    }
}
