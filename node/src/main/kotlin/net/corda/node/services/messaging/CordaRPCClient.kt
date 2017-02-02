package net.corda.node.services.messaging

import com.google.common.net.HostAndPort
import net.corda.core.ThreadBox
import net.corda.core.logElapsedTime
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.minutes
import net.corda.core.seconds
import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.SSLConfiguration
import net.corda.node.services.messaging.ArtemisMessagingComponent.ConnectionDirection.Outbound
import org.apache.activemq.artemis.api.core.ActiveMQException
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory
import org.apache.activemq.artemis.api.core.client.ServerLocator
import rx.Observable
import java.io.Closeable
import java.time.Duration
import javax.annotation.concurrent.ThreadSafe

/**
 * An RPC client connects to the specified server and allows you to make calls to the server that perform various
 * useful tasks. See the documentation for [proxy] or review the docsite to learn more about how this API works.
 *
 * @param host The hostname and messaging port of the node.
 * @param config If specified, the SSL configuration to use. If not specified, SSL will be disabled and the node will not be authenticated, nor will RPC traffic be encrypted.
 */
@ThreadSafe
class CordaRPCClient(val host: HostAndPort, override val config: SSLConfiguration?, val serviceConfigurationOverride: (ServerLocator.() -> Unit)? = null) : Closeable, ArtemisMessagingComponent() {
    private companion object {
        val log = loggerFor<CordaRPCClient>()
    }

    // TODO: Certificate handling for clients needs more work.
    private inner class State {
        var running = false
        lateinit var sessionFactory: ClientSessionFactory
        lateinit var session: ClientSession
        lateinit var clientImpl: CordaRPCClientImpl
    }

    private val state = ThreadBox(State())

    /**
     * Opens the connection to the server with the given username and password, then returns itself.
     * Registers a JVM shutdown hook to cleanly disconnect.
     */
    @Throws(ActiveMQException::class)
    fun start(username: String, password: String): CordaRPCClient {
        state.locked {
            check(!running)
            log.logElapsedTime("Startup") {
                checkStorePasswords()
                val serverLocator = ActiveMQClient.createServerLocatorWithoutHA(tcpTransport(Outbound(), host.hostText, host.port)).apply {
                    // TODO: Put these in config file or make it user configurable?
                    threadPoolMaxSize = 1
                    confirmationWindowSize = 100000 // a guess
                    retryInterval = 5.seconds.toMillis()
                    retryIntervalMultiplier = 1.5  // Exponential backoff
                    maxRetryInterval = 3.minutes.toMillis()
                    serviceConfigurationOverride?.invoke(this)
                }
                sessionFactory = serverLocator.createSessionFactory()
                session = sessionFactory.createSession(username, password, false, true, true, serverLocator.isPreAcknowledge, serverLocator.ackBatchSize)
                session.start()
                clientImpl = CordaRPCClientImpl(session, state.lock, username)
                running = true
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            close()
        })

        return this
    }

    /**
     * A convenience function that opens a connection with the given credentials, executes the given code block with all
     * available RPCs in scope and shuts down the RPC connection again. It's meant for quick prototyping and demos. For
     * more control you probably want to control the lifecycle of the client and proxies independently, as well as
     * configuring a timeout and other such features via the [proxy] method.
     *
     * After this method returns the client is closed and can't be restarted.
     */
    @Throws(ActiveMQException::class)
    fun <T> use(username: String, password: String, block: CordaRPCOps.() -> T): T {
        require(!state.locked { running })
        start(username, password)
        (this as Closeable).use {
            return proxy().block()
        }
    }

    /** Shuts down the client and lets the server know it can free the used resources (in a nice way). */
    override fun close() {
        state.locked {
            if (!running) return
            session.close()
            sessionFactory.close()
            running = false
        }
    }

    /**
     * Returns a fresh proxy that lets you invoke RPCs on the server. Calls on it block, and if the server throws an
     * exception then it will be rethrown on the client. Proxies are thread safe but only one RPC can be in flight at
     * once. If you'd like to perform multiple RPCs in parallel, use this function multiple times to get multiple
     * proxies.
     *
     * Creation of a proxy is a somewhat expensive operation that involves calls to the server, so if you want to do
     * calls from many threads at once you should cache one proxy per thread and reuse them. This function itself is
     * thread safe though so requires no extra synchronisation.
     *
     * RPC sends and receives are logged on the net.corda.rpc logger.
     *
     * By default there are no timeouts on calls. This is deliberate, RPCs without timeouts can survive restarts,
     * maintenance downtime and moves of the server. RPCs can survive temporary losses or changes in client connectivity,
     * like switching between wifi networks. You can specify a timeout on the level of a proxy. If a call times
     * out it will throw [RPCException.Deadline].
     *
     * The [CordaRPCOps] defines what client RPCs are available. If an RPC returns an [Observable] anywhere in the
     * object graph returned then the server-side observable is transparently linked to a messaging queue, and that
     * queue linked to another observable on the client side here. *You are expected to use it*. The server will begin
     * buffering messages immediately that it will expect you to drain by subscribing to the returned observer. You can
     * opt-out of this by simply casting the [Observable] to [Closeable] or [AutoCloseable] and then calling the close
     * method on it. You don't have to explicitly close the observable if you actually subscribe to it: it will close
     * itself and free up the server-side resources either when the client or JVM itself is shutdown, or when there are
     * no more subscribers to it. Once all the subscribers to a returned observable are unsubscribed, the observable is
     * closed and you can't then re-subscribe again: you'll have to re-request a fresh observable with another RPC.
     *
     * The proxy and linked observables consume some small amount of resources on the server. It's OK to just exit your
     * process and let the server clean up, but in a long running process where you only need something for a short
     * amount of time it is polite to cast the objects to [Closeable] or [AutoCloseable] and close it when you are done.
     * Finalizers are in place to warn you if you lose a reference to an unclosed proxy or observable.
     *
     * @throws RPCException if the server version is too low or if the server isn't reachable within the given time.
     */
    @JvmOverloads
    @Throws(RPCException::class)
    fun proxy(timeout: Duration? = null, minVersion: Int = 0): CordaRPCOps {
        return state.locked {
            check(running) { "Client must have been started first" }
            log.logElapsedTime("Proxy build") {
                clientImpl.proxyFor(CordaRPCOps::class.java, timeout, minVersion)
            }
        }
    }

    @Suppress("UNUSED")
    private fun finalize() {
        state.locked {
            if (running) {
                rpcLog.warn("A CordaMQClient is being finalised whilst still running, did you forget to call close?")
                close()
            }
        }
    }
}