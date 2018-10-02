package net.corda.nodeapi.internal.protonwrapper.netty

import java.net.Socket
import java.security.Principal
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509KeyManager

interface AliasProvidingKeyMangerWrapper : X509KeyManager {
    var lastAlias: String?
}


class AliasProvidingKeyMangerWrapperImpl(private val keyManager: X509KeyManager) : AliasProvidingKeyMangerWrapper, X509KeyManager by keyManager {
    override var lastAlias: String? = null

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? {
        return storeIfNotNull { keyManager.chooseServerAlias(keyType, issuers, socket) }
    }

    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? {
        return storeIfNotNull { keyManager.chooseClientAlias(keyType, issuers, socket) }
    }

    private fun storeIfNotNull(func: () -> String?): String? {
        val alias = func()
        if (alias != null) {
            lastAlias = alias
        }
        return alias
    }
}

class AliasProvidingExtendedKeyMangerWrapper(private val keyManager: X509ExtendedKeyManager) : X509ExtendedKeyManager(), X509KeyManager by keyManager, AliasProvidingKeyMangerWrapper {
    override var lastAlias: String? = null

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? {
        return storeIfNotNull { keyManager.chooseServerAlias(keyType, issuers, socket) }
    }

    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? {
        return storeIfNotNull { keyManager.chooseClientAlias(keyType, issuers, socket) }
    }

    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String? {
        return storeIfNotNull { keyManager.chooseEngineClientAlias(keyType, issuers, engine) }
    }

    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? {
        return storeIfNotNull { keyManager.chooseEngineServerAlias(keyType, issuers, engine) }
    }

    private fun storeIfNotNull(func: () -> String?): String? {
        val alias = func()
        if (alias != null) {
            lastAlias = alias
        }
        return alias
    }
}