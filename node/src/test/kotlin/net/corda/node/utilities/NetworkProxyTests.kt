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

    /**
     * Check we can get http proxy auth credentials if and only if server address, port and requestor type
     * match (i.e. the requestor type is PROXY for http proxies)
     */
    @Test
    fun testHttpSettingFromConfig() {
        val config = NetworkServicesConfig(URL("https://doorman"), URL("http://networkmap"), proxyType = Proxy.Type.HTTP, proxyAddress = NetworkHostAndPort("localhost", 1234), proxyPassword = "pw", proxyUser = "user")
        val proxy = ProxyAuthSetter.getInstance(config)

        Assert.assertNotNull(proxy.proxy)
        Assert.assertEquals(proxy.proxy!!.address(), InetSocketAddress("localhost", 1234))

        val pwAuth = Authenticator.requestPasswordAuthentication("localhost", InetAddress.getByName("localhost"), 1234, "https", "PROXY", "PROXY", URL("https://some.where.over.the/rainbow"), Authenticator.RequestorType.PROXY)
        Assert.assertNotNull(pwAuth)
        Assert.assertEquals("user", pwAuth.userName)

        val pwAuthDifferentServer = Authenticator.requestPasswordAuthentication("foo.bar.com", InetAddress.getByName("localhost"), 1234, "https", "PROXY", "PROXY", URL("https://some.where.over.the/rainbow"), Authenticator.RequestorType.PROXY)
        Assert.assertNull(pwAuthDifferentServer)

        val pwAuthWrongType = Authenticator.requestPasswordAuthentication("localhost", InetAddress.getByName("localhost"), 1234, "https", "PROXY", "PROXY", URL("https://some.where.over.the/rainbow"), Authenticator.RequestorType.SERVER)
        Assert.assertNull(pwAuthWrongType)
    }

    /**
     * Check we can get socks proxy auth credentials if and only if server address, port and requestor type
     * match (i.e. the requestor type is SERVER for socks proxies)
     */
    @Test
    fun testSocksSettingFromConfig() {
        val config = NetworkServicesConfig(URL("https://doorman"), URL("http://networkmap"), proxyType = Proxy.Type.SOCKS, proxyAddress = NetworkHostAndPort("localhost", 1234), proxyPassword = "pw", proxyUser = "user")
        val proxy = ProxyAuthSetter.getInstance(config)

        Assert.assertNotNull(proxy.proxy)
        Assert.assertEquals(proxy.proxy!!.address(), InetSocketAddress("localhost", 1234))

        val pwAuth = Authenticator.requestPasswordAuthentication("localhost", InetAddress.getByName("localhost"), 1234, "https", "PROXY", "PROXY", URL("https://some.where.over.the/rainbow"), Authenticator.RequestorType.SERVER)
        Assert.assertNotNull(pwAuth)
        Assert.assertEquals("user", pwAuth.userName)

        val pwAuthDifferentServer = Authenticator.requestPasswordAuthentication("foo.bar.com", InetAddress.getByName("localhost"), 1234, "https", "PROXY", "PROXY", URL("https://some.where.over.the/rainbow"), Authenticator.RequestorType.SERVER)
        Assert.assertNull(pwAuthDifferentServer)

        val pwAuthWrongType = Authenticator.requestPasswordAuthentication("localhost", InetAddress.getByName("localhost"), 1234, "https", "PROXY", "PROXY", URL("https://some.where.over.the/rainbow"), Authenticator.RequestorType.PROXY)
        Assert.assertNull(pwAuthWrongType)
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