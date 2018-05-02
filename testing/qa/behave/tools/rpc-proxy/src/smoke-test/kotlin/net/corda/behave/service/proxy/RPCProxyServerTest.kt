package net.corda.behave.service.proxy

import net.corda.core.internal.checkOkResponse
import net.corda.core.internal.openHttpConnection
import net.corda.core.utilities.NetworkHostAndPort
import org.junit.Test
import java.net.URL

class RPCProxyServerTest {

    private val rpcProxyHostAndPort = NetworkHostAndPort("localhost", 13000)
    private val nodeHostAndPort = NetworkHostAndPort("localhost", 12000)

    @Test
    fun `execute RPCOp`() {
        RPCProxyServer(rpcProxyHostAndPort,
                webService = RPCProxyWebService(nodeHostAndPort)).use {
            it.start()
            it.doGet("my-ip")
        }
    }

    private fun RPCProxyServer.doGet(path: String) {
        return URL("http://$rpcProxyHostAndPort/rpc/$path").openHttpConnection().checkOkResponse()
    }
}