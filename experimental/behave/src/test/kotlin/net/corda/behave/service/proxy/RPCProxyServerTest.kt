package net.corda.behave.service.proxy

import net.corda.core.internal.checkOkResponse
import net.corda.core.internal.openHttpConnection
import net.corda.core.utilities.NetworkHostAndPort
import org.junit.Test

class RPCProxyServerTest {

    @Test
    fun `execute RPCOp`() {

//        RPCProxyServer(NetworkHostAndPort("localhost", 0),
//                       webService = RPCProxyWebService()).use {
//            it.start()
//            it.doPost("rpcOps", Byte)
//        }
    }

    private fun RPCProxyServer.doPost(path: String, payload: ByteArray) {
        val url = java.net.URL("http://$hostAndPort/rpc/$path")
        url.openHttpConnection().apply {
            doOutput = true
            requestMethod = "POST"
            setRequestProperty("Content-Type", javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM)
            outputStream.write(payload)
            checkOkResponse()
        }
    }
}