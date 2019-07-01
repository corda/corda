package net.corda.node.utilities

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.NetworkServicesConfig
import org.junit.After
import org.junit.Assert
import org.junit.Test
import java.net.*

class NetworkProxyTests {
    @After
    fun tearDown() {
        ProxyAuthSetter.unsetInstance()
    }

    @Test
    fun testSettingFromConfig() {
        val config = NetworkServicesConfig(URL("https://doorman"), URL("http://networkmap"), proxyType = Proxy.Type.HTTP, proxyAddress = NetworkHostAndPort("localhost", 1234), proxyPassword = "pw", proxyUser = "user")
        val proxy = ProxyAuthSetter.getInstance(config)

        Assert.assertNotNull(proxy.proxy)
        Assert.assertEquals(proxy.proxy!!.address(), InetSocketAddress("localhost", 1234))

        val pwAuth = Authenticator.requestPasswordAuthentication("localhost", InetAddress.getByName("localhost"), 1234, "https", "PROXY", "PROXY", URL("https://some.where.over.the/rainbow"), Authenticator.RequestorType.PROXY)
        Assert.assertNotNull(pwAuth)
        Assert.assertEquals("user", pwAuth.userName)
    }

    @Test
    fun testCanSetProxyConfigOnlyOnce() {
        val config = NetworkServicesConfig(URL("https://doorman"), URL("http://networkmap"), proxyType = Proxy.Type.HTTP, proxyAddress = NetworkHostAndPort("localhost", 1234), proxyPassword = "pw", proxyUser = "user")
        var proxy = ProxyAuthSetter.getInstance(config)

        Assert.assertNotNull(proxy.proxy)
        Assert.assertEquals(proxy.proxy!!.address(), InetSocketAddress("localhost", 1234))

        val config2 = NetworkServicesConfig(URL("https://doorman"), URL("http://networkmap"), proxyType = Proxy.Type.HTTP, proxyAddress = NetworkHostAndPort("localhost2", 5678), proxyPassword = "pw", proxyUser = "user")
        proxy = ProxyAuthSetter.getInstance(config2)

        Assert.assertNotNull(proxy.proxy)
        Assert.assertEquals(proxy.proxy!!.address(), InetSocketAddress("localhost", 1234))
    }
}