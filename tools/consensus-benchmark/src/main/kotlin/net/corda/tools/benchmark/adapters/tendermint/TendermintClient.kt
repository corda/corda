package net.corda.tools.benchmark.adapters.tendermint

import com.github.jtendermint.websocket.Websocket
import com.github.jtendermint.websocket.WebsocketException
import com.github.jtendermint.websocket.WebsocketStatus
import com.github.jtendermint.websocket.jsonrpc.Method
import com.github.jtendermint.websocket.jsonrpc.calls.StringParam
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.websocket.CloseReason

class TendermintClient : WebsocketStatus {
    private val log: Logger get() = LoggerFactory.getLogger(TendermintClient::class.java)
    // Uses default host & port
    private val ws = Websocket("ws://127.0.0.1:26657/websocket", this)

    fun start() {
        connectToSocket()
    }

    private fun connectToSocket() {
        while (!ws.isOpen) {
            try {
                log.info("Attempting to establish connection to WebSocket..")
                ws.connect()
                Thread.sleep(100)
            } catch (var3: WebsocketException) {
                System.err.println(var3)
                Thread.sleep(1000L)
            }
        }
    }

    override fun wasOpened() {
        log.info("WebSocket connection open")
    }

    override fun wasClosed(cr: CloseReason?) {
        log.info("WebScoket connection closed, reason: $cr")
        Thread.currentThread().interrupt()
    }

    override fun hadError(t: Throwable) {
        log.error("WebSocket connection error", t)
    }

    fun sendRecord(record: ByteArray) {
        log.debug("Broadcasting a transaction")
        val rpc = StringParam(Method.BROADCAST_TX_ASYNC, record)
        ws.sendMessage(rpc) {
            //ignore
        }
    }

    fun close() {
        ws.disconnect()
    }
}