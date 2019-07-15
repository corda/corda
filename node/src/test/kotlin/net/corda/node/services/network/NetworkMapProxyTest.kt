package net.corda.node.services.network

import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.base64ToRealString
import net.corda.core.utilities.seconds
import net.corda.node.VersionInfo
import net.corda.node.services.config.NetworkServicesConfig
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.node.internal.network.NetworkMapServer
import org.apache.http.auth.AuthenticationException
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
import java.net.InetAddress
import java.net.Proxy
import java.net.URL
import javax.servlet.ServletConfig
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.test.assertEquals

class AuthenticatedHttpProxy() : ProxyServlet() {
    override fun init(config: ServletConfig?) {

        super.init(config)
        user = config!!.getInitParameter("user")
        password = config!!.getInitParameter("password")
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

class NetworkMapProxyTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val cacheTimeout = 100000.seconds

    private val myHostname = InetAddress.getLocalHost().hostName

    private lateinit var server: NetworkMapServer
    private lateinit var networkMapClient: NetworkMapClient

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
        val proxy = ConnectHandler()
        server.handler = proxy

        // Setup proxy servlet
        val context = ServletContextHandler(proxy, "/", ServletContextHandler.SESSIONS)
        val proxyServlet = ServletHolder(AuthenticatedHttpProxy::class.java)
//        proxyServlet.setInitParameter("proxyTo", "localhost:$serverPort")
//        proxyServlet.setInitParameter("prefix", "/")
        proxyServlet.setInitParameter("user", "proxyUser")
        proxyServlet.setInitParameter("password", "proxyPW")
        context.addServlet(proxyServlet, "/*")

        return server
    }

    @Before
    fun setUp() {
        server = NetworkMapServer(cacheTimeout, hostAndPort = NetworkHostAndPort(myHostname, serverPort))
        val address = server.start()
        networkMapClient = NetworkMapClient(NetworkServicesConfig(URL("http://no.such.address"), URL("http://$address"), proxyAddress = NetworkHostAndPort(myHostname, httpProxyPort), proxyType = Proxy.Type.HTTP, proxyPassword = "proxyPW", proxyUser = "proxyUser"),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
        httpProxy.start()
    }

    @After
    fun tearDown() {
        server.close()
        httpProxy.stop()
    }

    @Test
    fun `download NetworkParameters correctly`() {
        // The test server returns same network parameter for any hash.
        val parametersHash = server.networkParameters.serialize().hash
        val networkParameters = networkMapClient.getNetworkParameters(parametersHash).verified()
        assertEquals(server.networkParameters, networkParameters)
    }
}