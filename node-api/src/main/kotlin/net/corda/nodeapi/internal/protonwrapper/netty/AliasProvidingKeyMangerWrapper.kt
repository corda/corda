package net.corda.nodeapi.internal.protonwrapper.netty

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509KeyManager

interface AliasProvidingKeyMangerWrapper : X509KeyManager {
    var lastAlias: String?
}


class AliasProvidingKeyMangerWrapperImpl(private val keyManager: X509KeyManager) : AliasProvidingKeyMangerWrapper {
    override var lastAlias: String? = null

    override fun getClientAliases(p0: String?, p1: Array<out Principal>?): Array<String> {
        return keyManager.getClientAliases(p0, p1)
    }

    override fun getServerAliases(p0: String?, p1: Array<out Principal>?): Array<String> {
        return getServerAliases(p0, p1)
    }

    override fun chooseServerAlias(p0: String?, p1: Array<out Principal>?, p2: Socket?): String? {
        return storeIfNotNull { keyManager.chooseServerAlias(p0, p1, p2) }
    }

    override fun getCertificateChain(p0: String?): Array<X509Certificate> {
        return keyManager.getCertificateChain(p0)
    }

    override fun getPrivateKey(p0: String?): PrivateKey {
        return keyManager.getPrivateKey(p0)
    }

    override fun chooseClientAlias(p0: Array<out String>?, p1: Array<out Principal>?, p2: Socket?): String? {
        return storeIfNotNull { keyManager.chooseClientAlias(p0, p1, p2) }
    }

    private fun storeIfNotNull(func: () -> String?): String? {
        val alias = func()
        if (alias != null) {
            lastAlias = alias
        }
        return alias
    }
}

class AliasProvidingExtendedKeyMangerWrapper(private val keyManager: X509ExtendedKeyManager) : X509ExtendedKeyManager(), AliasProvidingKeyMangerWrapper {
    override var lastAlias: String? = null

    override fun getClientAliases(p0: String?, p1: Array<out Principal>?): Array<String> {
        return keyManager.getClientAliases(p0, p1)
    }

    override fun getServerAliases(p0: String?, p1: Array<out Principal>?): Array<String> {
        return keyManager.getServerAliases(p0, p1)
    }

    override fun chooseServerAlias(p0: String?, p1: Array<out Principal>?, p2: Socket?): String? {
        return storeIfNotNull { keyManager.chooseServerAlias(p0, p1, p2) }
    }

    override fun getCertificateChain(p0: String?): Array<X509Certificate> {
        return keyManager.getCertificateChain(p0)
    }

    override fun getPrivateKey(p0: String?): PrivateKey {
        return keyManager.getPrivateKey(p0)
    }

    override fun chooseClientAlias(p0: Array<out String>?, p1: Array<out Principal>?, p2: Socket?): String? {
        return storeIfNotNull { keyManager.chooseClientAlias(p0, p1, p2) }
    }

    override fun chooseEngineClientAlias(p0: Array<out String>?, p1: Array<out Principal>?, p2: SSLEngine?): String? {
        return storeIfNotNull { keyManager.chooseEngineClientAlias(p0, p1, p2) }
    }

    override fun chooseEngineServerAlias(p0: String?, p1: Array<out Principal>?, p2: SSLEngine?): String? {
        return storeIfNotNull { keyManager.chooseEngineServerAlias(p0, p1, p2) }
    }

    private fun storeIfNotNull(func: () -> String?): String? {
        val alias = func()
        if (alias != null) {
            lastAlias = alias
        }
        return alias
    }
}