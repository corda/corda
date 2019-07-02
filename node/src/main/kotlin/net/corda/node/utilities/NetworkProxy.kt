package net.corda.node.utilities

import net.corda.core.internal.VisibleForTesting
import net.corda.node.services.config.NetworkServicesConfig
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy

class ProxyAuthSetter private constructor(config: NetworkServicesConfig) {
    companion object {
        private var instance: ProxyAuthSetter? = null

        fun getInstance(config: NetworkServicesConfig): ProxyAuthSetter {
            return instance ?: run {
                instance = ProxyAuthSetter(config)
                instance!!
            }
        }

        @VisibleForTesting
        internal fun unsetInstance() {
            instance = null
        }
    }

    val proxy: Proxy? = configureProxy(config)

    private fun configureProxy(config: NetworkServicesConfig): Proxy? {
        // Need to enable basic authentication for https tunnelling through a http proxy
        // This was disabled by default in Java 1.8.111 for security reasons.
        // cf https://bugs.openjdk.java.net/browse/JDK-8210814
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "")

        if (config.proxyType != Proxy.Type.DIRECT && config.proxyAddress != null) {
            if (config.proxyUser != null && config.proxyPassword != null) {
                Authenticator.setDefault(object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication? {
                        if (requestorType == RequestorType.PROXY &&
                                requestingHost == config.proxyAddress.host &&
                                requestingPort == config.proxyAddress.port) {
                            return PasswordAuthentication(config.proxyUser, config.proxyPassword.toCharArray())
                        }
                        return null
                    }
                })
            }
            return Proxy(config.proxyType, InetSocketAddress(config.proxyAddress.host, config.proxyAddress.port))
        }
        return null
    }
}

fun createProxy(config: NetworkServicesConfig): Proxy? {
    return ProxyAuthSetter.getInstance(config).proxy
}