package net.corda.nodeapi.internal.proxy

import net.corda.nodeapi.internal.protonwrapper.netty.ProxyConfig
import net.corda.nodeapi.internal.protonwrapper.netty.ProxyVersion
import java.lang.IllegalArgumentException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy

data class ProxySettings(val proxy: Proxy, val auth: Authenticator?)

object ProxyUtils {

    fun fromConfig(proxyConfig: ProxyConfig): ProxySettings {
        val address = InetSocketAddress(proxyConfig.proxyAddress.host, proxyConfig.proxyAddress.port)

        val authenticator: Authenticator? = if (proxyConfig.userName != null || proxyConfig.password != null) {
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(proxyConfig.userName, proxyConfig.password?.toCharArray() ?: CharArray(0))
                }
            }
        } else null

        return when (proxyConfig.version) {
            ProxyVersion.SOCKS4 -> ProxySettings(Proxy(Proxy.Type.SOCKS, address), null)
            ProxyVersion.SOCKS5 -> ProxySettings(Proxy(Proxy.Type.SOCKS, address), authenticator)
            ProxyVersion.HTTP -> ProxySettings(Proxy(Proxy.Type.HTTP, address), authenticator)
            else -> throw IllegalArgumentException("Unhandled ProxyVersion: ${proxyConfig.version}")
        }
    }
}