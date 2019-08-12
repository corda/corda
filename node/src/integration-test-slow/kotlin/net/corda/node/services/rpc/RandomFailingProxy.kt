package net.corda.node.services.rpc

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simple proxy that can be restarted and introduces random latencies.
 *
 * Also acts as a mock load balancer.
 */
class RandomFailingProxy(val serverPort: Int, val remotePort: Int) : AutoCloseable {
    private val threadPool = Executors.newCachedThreadPool()
    private val stopCopy = AtomicBoolean(false)
    private var currentServerSocket: ServerSocket? = null
    private val rnd = ThreadLocal.withInitial { Random() }

    fun start(): RandomFailingProxy {
        stopCopy.set(false)
        currentServerSocket = ServerSocket(serverPort)
        threadPool.execute {
            try {
                currentServerSocket.use { serverSocket ->
                    while (!stopCopy.get() && !serverSocket!!.isClosed) {
                        handleConnection(serverSocket.accept())
                    }
                }
            } catch (e: SocketException) {
                // The Server socket could be closed
            }
        }
        return this
    }

    private fun handleConnection(socket: Socket) {
        threadPool.execute {
            socket.use { _ ->
                try {
                    Socket("localhost", remotePort).use { target ->
                        // send message to node
                        threadPool.execute {
                            try {
                                socket.getInputStream().flakeyCopyTo(target.getOutputStream())
                            } catch (e: IOException) {
                                // Thrown when the connection to the target server dies.
                            }
                        }
                        target.getInputStream().flakeyCopyTo(socket.getOutputStream())
                    }
                } catch (e: IOException) {
                    // Thrown when the connection to the target server dies.
                }
            }
        }
    }

    fun stop(): RandomFailingProxy {
        stopCopy.set(true)
        currentServerSocket?.close()
        return this
    }

    private val failOneConnection = AtomicBoolean(false)
    fun failConnection() {
        failOneConnection.set(true)
    }

    override fun close() {
        try {
            stop()
            threadPool.shutdownNow()
        } catch (e: Exception) {
            // Nothing can be done.
        }
    }

    private fun InputStream.flakeyCopyTo(out: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long {
        var bytesCopied: Long = 0
        val buffer = ByteArray(bufferSize)
        var bytes = read(buffer)
        while (bytes >= 0 && !stopCopy.get()) {
            // Introduce intermittent slowness.
            if (rnd.get().nextInt().rem(700) == 0) {
                Thread.sleep(rnd.get().nextInt(2000).toLong())
            }
            if (failOneConnection.compareAndSet(true, false)) {
                throw IOException("Randomly dropped one connection")
            }
            out.write(buffer, 0, bytes)
            bytesCopied += bytes
            bytes = read(buffer)
        }
        return bytesCopied
    }
}