package net.corda.demobench.web

import com.google.common.util.concurrent.RateLimiter
import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.isDirectory
import net.corda.core.internal.until
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.minutes
import net.corda.demobench.model.NodeConfigWrapper
import net.corda.demobench.readErrorLines
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

class WebServer internal constructor(private val webServerController: WebServerController) : AutoCloseable {
    private companion object {
        private val log = contextLogger()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var process: Process? = null

    @Throws(IOException::class)
    fun open(config: NodeConfigWrapper): CordaFuture<URI> {
        val nodeDir = config.nodeDir

        if (!nodeDir.isDirectory()) {
            log.warn("Working directory '{}' does not exist.", nodeDir.toAbsolutePath())
            return openFuture()
        }

        val legalName = config.nodeConfig.myLegalName
        try {
            val p = webServerController.process()
                    .directory(nodeDir.toFile())
                    .start()
            process = p

            log.info("Launched Web Server for '{}'", legalName)

            // Close these streams because no-one is using them.
            safeClose(p.outputStream)
            safeClose(p.inputStream)

            executor.submit {
                val exitValue = p.waitFor()
                val errors = p.readErrorLines()
                process = null

                if (errors.isEmpty()) {
                    log.info("Web Server for '{}' has exited (value={})", legalName, exitValue)
                } else {
                    log.error("Web Server for '{}' has exited (value={}, {})", legalName, exitValue, errors)
                }
            }

            val future = openFuture<URI>()
            thread {
                future.capture {
                    log.info("Waiting for web server for $legalName to start ...")
                    waitForStart(config.nodeConfig.webAddress.port)
                }
            }
            return future
        } catch (e: IOException) {
            log.error("Failed to launch Web Server for '{}': {}", legalName, e.message)
            throw e
        }
    }

    override fun close() {
        executor.shutdown()
        process?.destroy()
    }

    private fun safeClose(c: AutoCloseable) {
        try {
            c.close()
        } catch (e: Exception) {
            log.error("Failed to close stream: '{}'", e.message)
        }
    }

    private fun waitForStart(port: Int): URI {
        val url = URI("http://localhost:$port/")
        val rateLimiter = RateLimiter.create(2.0)
        val start = Instant.now()
        val timeout = 1.minutes
        while ((start until Instant.now()) < timeout) {
            try {
                rateLimiter.acquire()
                val conn = url.toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 500  // msec
                conn.requestMethod = "HEAD"
                conn.connect()
                conn.disconnect()
                return url
            } catch (e: IOException) {
            }
        }
        throw TimeoutException("Web server did not start within ${timeout.seconds} seconds")
    }
}
