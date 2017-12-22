package net.corda.attestation.host.web

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.*
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener
import javax.servlet.annotation.WebListener

/**
 * Creates an @ApplicationScoped resource without having to use CDI.
 */
@WebListener
class ThreadPoolListener : ServletContextListener {
    companion object {
        const val threadPoolAttr = "Thread-Pool"
        private val log: Logger = LoggerFactory.getLogger(ThreadPoolListener::class.java)
    }

    private lateinit var pool: ExecutorService

    override fun contextInitialized(evt: ServletContextEvent) {
        log.info("Creating thread pool")
        pool = Executors.newCachedThreadPool()
        evt.servletContext.setAttribute(threadPoolAttr, pool)
    }

    override fun contextDestroyed(evt: ServletContextEvent) {
        log.info("Destroying thread pool")
        pool.shutdown()
        try {
            evt.servletContext.removeAttribute(threadPoolAttr)
            if (!pool.awaitTermination(30, SECONDS)) {
                pool.shutdownNow()
            }
        } catch (e: InterruptedException) {
            log.error("Thread pool timed out on shutdown")
        }
    }
}
