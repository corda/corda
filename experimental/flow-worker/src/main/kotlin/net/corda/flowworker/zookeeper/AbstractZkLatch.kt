package net.corda.flowworker.zookeeper

import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.AfterConnectionEstablished
import org.apache.curator.framework.state.ConnectionState
import org.apache.curator.framework.state.ConnectionStateListener
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

abstract class AbstractZkLatch(client: CuratorFramework) : Closeable, ConnectionStateListener {
    companion object {
        private val logger = contextLogger()
    }


    private val watchedClient = client.newWatcherRemoveCuratorFramework()
    private val _started = AtomicBoolean()
    val started get() = _started.get()
    private val startTask = AtomicReference<Future<*>>()


    fun start() {
        require(_started.compareAndSet(false, true)) { "Cannot be started more than once." }
        startTask.set(AfterConnectionEstablished.execute(watchedClient) {
            try {
                watchedClient.connectionStateListenable.addListener(this)
                try {
                    initiateLatch(watchedClient)
                } catch (e: Exception) {
                    logger.error("An error occurred while resetting leadership.", e)
                }
            } finally {
                startTask.set(null)
            }
        })
    }

    override fun stateChanged(client: CuratorFramework, newState: ConnectionState) {
        logger.info("State change. New state: $newState")
        when (newState) {
            ConnectionState.RECONNECTED -> {
                try {
                    if (watchedClient.connectionStateErrorPolicy.isErrorState(ConnectionState.SUSPENDED)) {
                        logger.info("Client reconnected. Resetting latch.")
                        initiateLatch(watchedClient)
                    }
                } catch (e: Exception) {
                    logger.error("Could not reset leader latch.", e)
                    reset(watchedClient)
                }
            }

            ConnectionState.SUSPENDED -> {
                if (watchedClient.connectionStateErrorPolicy.isErrorState(ConnectionState.SUSPENDED))
                    reset(watchedClient)
            }

            ConnectionState.LOST -> reset(watchedClient)

            else -> logger.debug { "Ignoring state change $newState" }
        }
    }

    override fun close() {
        require(_started.compareAndSet(true, false)) { "Already closed or has not been started." }
        startTask.getAndSet(null)?.cancel(true)
        try {
            watchedClient.removeWatchers()
            reset(watchedClient)
        } catch (e: Exception) {
            throw IOException(e)
        } finally {
            watchedClient.connectionStateListenable.removeListener(this)
        }
    }



    protected abstract fun initiateLatch(startedClient: CuratorFramework)

    protected abstract fun reset(startedClient: CuratorFramework)
}