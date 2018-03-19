package net.corda.behave.service.proxy

import net.corda.core.internal.checkOkResponse
import net.corda.core.internal.openHttpConnection
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import org.junit.Test

class RPCProxyServerTest {

    private val rpcProxyHostAndPort = NetworkHostAndPort("localhost", 13000)
    private val nodeHostAndPort = NetworkHostAndPort("localhost", 12000)

    @Test
    fun `execute RPCOp`() {
        RPCProxyServer(rpcProxyHostAndPort,
                webService = RPCProxyWebService(nodeHostAndPort)).use {
            it.start()
            it.doPost("rpcOps", OpaqueBytes.of(0).bytes)
        }
    }

    private fun RPCProxyServer.doPost(path: String, payload: ByteArray) {
        val url = java.net.URL("http://$rpcProxyHostAndPort/rpc/$path")
        url.openHttpConnection().apply {
            doOutput = true
            requestMethod = "POST"
            setRequestProperty("Content-Type", javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM)
            outputStream.write(payload)
            checkOkResponse()
        }
    }
}