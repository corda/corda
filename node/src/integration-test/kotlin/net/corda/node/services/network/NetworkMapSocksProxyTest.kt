package net.corda.node.services.network

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.node.VersionInfo
import net.corda.node.services.config.NetworkServicesConfig
import net.corda.node.utilities.ProxyAuthSetter
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.node.internal.network.NetworkMapServer
import net.corda.utils.netty.AuthenticatedSocksServerInitializer
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.SSLException
import kotlin.test.assertEquals

class NetworkMapSocksProxyTest {
    private class SocksServer(port: Int, user: String, password: String) {
        private val bossGroup = NioEventLoopGroup(1)
        private val workerGroup = NioEventLoopGroup()
        private var closeFuture: ChannelFuture? = null

        init {
            try {
                val b = ServerBootstrap()
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel::class.java)
                        .handler(LoggingHandler(LogLevel.INFO))
                        .childHandler(AuthenticatedSocksServerInitializer(user, password))
                //.childHandler(SocksServerInitializer())
                closeFuture = b.bind(port).sync().channel().closeFuture()
            } catch (ex: Exception) {
                bossGroup.shutdownGracefully()
                workerGroup.shutdownGracefully()
            }
        }

        fun close() {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
            closeFuture?.sync()
        }
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private lateinit var socksProxy: SocksServer
    private lateinit var server: NetworkMapServer
    private lateinit var serverAddress: NetworkHostAndPort

    private val cacheTimeout = 100000.seconds
    private val myHostname = InetAddress.getLocalHost().hostName

    private val portAllocator = incrementalPortAllocation()
    private val socksProxyPort = portAllocator.nextPort()
    private val serverPort = portAllocator.nextPort()

    private val proxyPw = "proxyPW"
    private val proxyUser = "proxyUser"
    private val invalidPw = "this is not a password"

    @Before
    fun setup() {
        server = NetworkMapServer(cacheTimeout, hostAndPort = NetworkHostAndPort(myHostname, serverPort))
        serverAddress = server.start()
        socksProxy = SocksServer(socksProxyPort, proxyUser, proxyPw)
    }

    @After
    fun tearDown() {
        socksProxy.close()
        ProxyAuthSetter.unsetInstance()
    }

    @Test
    fun `download NetworkParameters correctly directly`() {
        // The test server returns same network parameter for any hash.
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("http://no.such.address"), URL("http://$serverAddress"), proxyType = Proxy.Type.DIRECT),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        val networkParameters = networkMapClient.getNetworkParameters(parametersHash).verified()
        assertEquals(server.networkParameters, networkParameters)
    }

    @Test
    fun `download NetworkParameters correctly via authenticated socks proxy`() {
        // The test server returns same network parameter for any hash.
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("http://no.such.address"), URL("http://$serverAddress"), proxyAddress = NetworkHostAndPort(myHostname, socksProxyPort), proxyType = Proxy.Type.SOCKS, proxyPassword = proxyPw, proxyUser = proxyUser),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        val networkParameters = networkMapClient.getNetworkParameters(parametersHash).verified()
        assertEquals(server.networkParameters, networkParameters)
    }

    @Test
    fun `download fails without credentials`() {
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("http://no.such.address"), URL("http://$serverAddress"), proxyAddress = NetworkHostAndPort(myHostname, socksProxyPort), proxyType = Proxy.Type.SOCKS),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        Assertions.assertThatExceptionOfType(IOException::class.java).isThrownBy {
            networkMapClient.getNetworkParameters(parametersHash).verified()
        }.withMessage("SOCKS : authentication failed")
    }

    @Test
    fun `download fails wrong credentials`() {
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("http://no.such.address"), URL("http://$serverAddress"), proxyAddress = NetworkHostAndPort(myHostname, socksProxyPort), proxyType = Proxy.Type.SOCKS, proxyUser = proxyUser, proxyPassword = invalidPw),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        Assertions.assertThatExceptionOfType(IOException::class.java).isThrownBy {
            networkMapClient.getNetworkParameters(parametersHash).verified()
        }.withMessage("SOCKS : authentication failed")
    }

    @Test
    fun `download NetworkParameters directly fails via https`() {
        // The test server returns same network parameter for any hash.
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("http://no.such.address"), URL("https://$serverAddress"), proxyType = Proxy.Type.DIRECT),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        Assertions.assertThatExceptionOfType(SSLException::class.java).isThrownBy {
            networkMapClient.getNetworkParameters(parametersHash).verified()
        }.withMessageContaining("Unrecognized SSL message, plaintext connection?")
    }

    @Test
    fun `download NetworkParameters correctly via authenticated proxy via https`() {
        // The test server returns same network parameter for any hash.
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("https://no.such.address"), URL("https://$serverAddress"), proxyAddress = NetworkHostAndPort(myHostname, socksProxyPort), proxyType = Proxy.Type.SOCKS, proxyPassword = proxyPw, proxyUser = proxyUser),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        Assertions.assertThatExceptionOfType(SSLException::class.java).isThrownBy {
            networkMapClient.getNetworkParameters(parametersHash).verified()
        }.withMessageContaining("Unrecognized SSL message, plaintext connection?")
    }

    @Test
    fun `download fails without credentials via https`() {
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("https://no.such.address"), URL("https://$serverAddress"), proxyAddress = NetworkHostAndPort(myHostname, socksProxyPort), proxyType = Proxy.Type.SOCKS),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        Assertions.assertThatExceptionOfType(IOException::class.java).isThrownBy {
            networkMapClient.getNetworkParameters(parametersHash).verified()
        }.withMessage("SOCKS : authentication failed")
    }

    @Test
    fun `download fails wrong credentials via https`() {
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("https://no.such.address"), URL("https://$serverAddress"), proxyAddress = NetworkHostAndPort(myHostname, socksProxyPort), proxyType = Proxy.Type.SOCKS, proxyUser = proxyUser, proxyPassword = invalidPw),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        Assertions.assertThatExceptionOfType(IOException::class.java).isThrownBy {
            networkMapClient.getNetworkParameters(parametersHash).verified()
        }.withMessage("SOCKS : authentication failed")
    }
}