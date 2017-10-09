package net.corda.demobench.web

import com.google.common.util.concurrent.RateLimiter
import net.corda.core.concurrent.CordaFuture
import net.corda.core.utilities.minutes
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.until
import net.corda.core.utilities.loggerFor
import net.corda.demobench.model.NodeConfig
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
        val log = loggerFor<WebServer>()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var process: Process? = null

    @Throws(IOException::class)
    fun open(config: NodeConfig): CordaFuture<URI> {
        val nodeDir = config.nodeDir.toFile()

        if (!nodeDir.isDirectory) {
            log.warn("Working directory '{}' does not exist.", nodeDir.absolutePath)
            return openFuture()
        }

        try {
            val p = webServerController.process()
                    .directory(nodeDir)
                    .start()
            process = p

            log.info("Launched Web Server for '{}'", config.legalName)

            // Close these streams because no-one is using them.
            safeClose(p.outputStream)
            safeClose(p.inputStream)

            executor.submit {
                val exitValue = p.waitFor()
                val errors = p.readErrorLines()
                process = null

                if (errors.isEmpty()) {
                    log.info("Web Server for '{}' has exited (value={})", config.legalName, exitValue)
                } else {
                    log.error("Web Server for '{}' has exited (value={}, {})", config.legalName, exitValue, errors)
                }
            }

            val future = openFuture<URI>()
            thread {
                future.capture {
                    log.info("Waiting for web server for ${config.legalName} to start ...")
                    waitForStart(config.webPort)
                }
            }
            return future
        } catch (e: IOException) {
            log.error("Failed to launch Web Server for '{}': {}", config.legalName, e.message)
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
