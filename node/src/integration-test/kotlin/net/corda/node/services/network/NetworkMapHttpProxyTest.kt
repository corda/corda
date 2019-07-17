package net.corda.node.services.network

import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.base64ToRealString
import net.corda.core.utilities.seconds
import net.corda.node.VersionInfo
import net.corda.node.services.config.NetworkServicesConfig
import net.corda.node.utilities.ProxyAuthSetter
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.node.internal.network.NetworkMapServer
import org.assertj.core.api.Assertions
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.proxy.ConnectHandler
import org.eclipse.jetty.proxy.ProxyServlet
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import sun.net.www.protocol.http.AuthCacheImpl
import sun.net.www.protocol.http.AuthCacheValue
import java.io.IOException
import java.net.Authenticator
import java.net.InetAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.SSLException
import javax.servlet.ServletConfig
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.test.assertEquals

// For http connections, we need to check the credentials on handling the actual get request.
class AuthenticatedHttpProxy : ProxyServlet() {
    override fun init(config: ServletConfig?) {

        super.init(config)
        user = config!!.getInitParameter("user")
        password = config.getInitParameter("password")
    }

    override fun service(clientRequest: HttpServletRequest?, proxyResponse: HttpServletResponse?) {
        if (validateProxyAuth(clientRequest, proxyResponse)) {
            return super.service(clientRequest, proxyResponse)
        }
    }

    private fun validateProxyAuth(clientRequest: HttpServletRequest?, proxyResponse: HttpServletResponse?): Boolean {
        val auth: String? = clientRequest!!.getHeader("Proxy-Authorization")
        if (auth == null) {
            proxyResponse!!.addHeader("Proxy-Authenticate", "Basic realm=*")
            sendProxyResponseError(clientRequest, proxyResponse, HttpStatus.PROXY_AUTHENTICATION_REQUIRED_407)
            return false
        }

        val s = auth.split(" ")
        if (s.size != 2 || s[0].toLowerCase() != "basic") {
            sendProxyResponseError(clientRequest, proxyResponse, HttpStatus.PROXY_AUTHENTICATION_REQUIRED_407)
            return false
        }
        val credentials = s[1].base64ToRealString().split(":")
        if (credentials.size != 2) {
            sendProxyResponseError(clientRequest, proxyResponse, HttpStatus.FORBIDDEN_403)
            return false
        }
        if (credentials[0] != user || credentials[1] != password) {
            sendProxyResponseError(clientRequest, proxyResponse, HttpStatus.FORBIDDEN_403)
            return false
        }
        return true
    }

    private lateinit var user: String
    private lateinit var password: String
}

// For https connections, we need to check the credentials when establishing the https tunnel.
class AuthenticatedConnectHandler(private val user: String, private val password: String) : ConnectHandler() {
    override fun handleAuthentication(request: HttpServletRequest?, response: HttpServletResponse?, address: String?): Boolean {

        val auth: String? = request!!.getHeader("Proxy-Authorization")
        if (auth == null) {
            response!!.addHeader("Proxy-Authenticate", "Basic realm=*")
            return false
        }

        val s = auth.split(" ")
        if (s.size != 2 || s[0].toLowerCase() != "basic") {
            return false
        }
        val credentials = s[1].base64ToRealString().split(":")
        if (credentials.size != 2) {
            return false
        }
        if (credentials[0] != user || credentials[1] != password) {
            return false
        }
        return true
    }
}

class NetworkMapHttpProxyTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val cacheTimeout = 100000.seconds

    private val myHostname = InetAddress.getLocalHost().hostName

    private lateinit var server: NetworkMapServer
    private lateinit var serverAddress: NetworkHostAndPort

    private val portAllocator = incrementalPortAllocation()
    private val httpProxyPort = portAllocator.nextPort()
    private val serverPort = portAllocator.nextPort()
    private val httpProxy: Server = reverseJettyProxy()

    private fun reverseJettyProxy(): Server {
        val server = Server()

        val connector = ServerConnector(server)
        connector.host = myHostname
        connector.port = httpProxyPort

        server.connectors = arrayOf<Connector>(connector)

        // Setup proxy handler to handle CONNECT methods
        val proxy = AuthenticatedConnectHandler("proxyUser", "proxyPW")
        server.handler = proxy

        // Setup proxy servlet
        val context = ServletContextHandler(proxy, "/", ServletContextHandler.SESSIONS)
        val proxyServlet = ServletHolder(AuthenticatedHttpProxy::class.java)
        proxyServlet.setInitParameter("user", "proxyUser")
        proxyServlet.setInitParameter("password", "proxyPW")
        context.addServlet(proxyServlet, "/*")

        return server
    }

    @Before
    fun setUp() {
        server = NetworkMapServer(cacheTimeout, hostAndPort = NetworkHostAndPort(myHostname, serverPort))
        serverAddress = server.start()
        httpProxy.start()
    }

    @After
    fun tearDown() {
        server.close()
        httpProxy.stop()
        ProxyAuthSetter.unsetInstance()
        Authenticator.setDefault(null)
        AuthCacheValue.setAuthCache(AuthCacheImpl())
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
    fun `download NetworkParameters correctly via authenticated proxy`() {
        // The test server returns same network parameter for any hash.
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("http://no.such.address"), URL("http://$serverAddress"), proxyAddress = NetworkHostAndPort(myHostname, httpProxyPort), proxyType = Proxy.Type.HTTP, proxyPassword = "proxyPW", proxyUser = "proxyUser"),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        val networkParameters = networkMapClient.getNetworkParameters(parametersHash).verified()
        assertEquals(server.networkParameters, networkParameters)
    }

    @Test
    fun `download fails without credentials`() {
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("http://no.such.address"), URL("http://$serverAddress"), proxyAddress = NetworkHostAndPort(myHostname, httpProxyPort), proxyType = Proxy.Type.HTTP),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        Assertions.assertThatExceptionOfType(IOException::class.java).isThrownBy {
            networkMapClient.getNetworkParameters(parametersHash).verified()
        }.withMessageContaining("Error 407 Proxy Authentication Required")
    }

    @Test
    fun `download fails wrong credentials`() {
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("http://no.such.address"), URL("http://$serverAddress"), proxyAddress = NetworkHostAndPort(myHostname, httpProxyPort), proxyType = Proxy.Type.HTTP, proxyUser = "proxyUser", proxyPassword = "ThisIsNotAPassword"),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        Assertions.assertThatExceptionOfType(IOException::class.java).isThrownBy {
            networkMapClient.getNetworkParameters(parametersHash).verified()
        }.withMessageContaining("403 Forbidden")
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
    fun `download NetworkParameters fails via authenticated proxy via https`() {
        // The test server returns same network parameter for any hash.
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("https://no.such.address"), URL("https://$serverAddress"), proxyAddress = NetworkHostAndPort(myHostname, httpProxyPort), proxyType = Proxy.Type.HTTP, proxyPassword = "proxyPW", proxyUser = "proxyUser"),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        Assertions.assertThatExceptionOfType(SSLException::class.java).isThrownBy {
            networkMapClient.getNetworkParameters(parametersHash).verified()
        }.withMessageContaining("Unrecognized SSL message, plaintext connection?")
    }

    @Test
    fun `download fails without credentials via https`() {
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("https://no.such.address"), URL("https://$serverAddress"), proxyAddress = NetworkHostAndPort(myHostname, httpProxyPort), proxyType = Proxy.Type.HTTP),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        Assertions.assertThatExceptionOfType(IOException::class.java).isThrownBy {
            networkMapClient.getNetworkParameters(parametersHash).verified()
        }.withMessageContaining("Response Code 407:")
    }

    @Test
    fun `download fails wrong credentials via https`() {
        val networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("https://no.such.address"), URL("https://$serverAddress"), proxyAddress = NetworkHostAndPort(myHostname, httpProxyPort), proxyType = Proxy.Type.HTTP, proxyUser = "proxyUser", proxyPassword = "ThisIsNotAPassword"),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        val parametersHash = server.networkParameters.serialize().hash
        Assertions.assertThatExceptionOfType(IOException::class.java).isThrownBy {
            networkMapClient.getNetworkParameters(parametersHash).verified()
        }.withMessageContaining("Response Code 407:")
    }
}