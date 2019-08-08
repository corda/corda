package net.corda.nodeapi.internal.proxy

import net.corda.nodeapi.internal.protonwrapper.netty.ProxyConfig
import net.corda.nodeapi.internal.protonwrapper.netty.ProxyVersion
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy

data class ProxySettings(val proxy: Proxy, val auth: Authenticator?, val additionalSetupFn: (() -> Unit)? = null)

object ProxyUtils {

    fun fromConfig(proxyConfig: ProxyConfig): ProxySettings {

        val authenticator: Authenticator? = if (proxyConfig.userName != null || proxyConfig.password != null) {

            // Java uses different requestor types for SOCKS and HTTP proxies - we only
            // want to return credentials when server address, port and requestor type
            // match what has been configured
            val allowedRequestorType =
                    when (proxyConfig.version) {
                        ProxyVersion.HTTP -> Authenticator.RequestorType.PROXY
                        ProxyVersion.SOCKS4 -> Authenticator.RequestorType.SERVER
                        ProxyVersion.SOCKS5 -> Authenticator.RequestorType.SERVER
                        else -> null
                    }

            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    return if (requestorType == allowedRequestorType &&
                            requestingHost == proxyConfig.proxyAddress.host &&
                            requestingPort == proxyConfig.proxyAddress.port) {
                        PasswordAuthentication(proxyConfig.userName, proxyConfig.password?.toCharArray() ?: CharArray(0))
                    } else {
                        null
                    }
                }
            }
        } else null

        val address = InetSocketAddress(proxyConfig.proxyAddress.host, proxyConfig.proxyAddress.port)

        return when (proxyConfig.version) {
            ProxyVersion.SOCKS4 -> ProxySettings(Proxy(Proxy.Type.SOCKS, address), null)
            ProxyVersion.SOCKS5 -> ProxySettings(Proxy(Proxy.Type.SOCKS, address), authenticator)
            ProxyVersion.HTTP -> ProxySettings(Proxy(Proxy.Type.HTTP, address), authenticator, ::httpsAdditionalSetup)
            else -> throw IllegalArgumentException("Unhandled ProxyVersion: ${proxyConfig.version}")
        }
    }

    private fun httpsAdditionalSetup() {
        // Need to enable basic authentication for https tunnelling through a http proxy
        // This was disabled by default in Java 1.8.111 for security reasons.
        // cf https://bugs.openjdk.java.net/browse/JDK-8210814
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "")
    }
}