package net.corda.nodeapi.internal

import io.netty.channel.ChannelPipeline
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQClientProtocolManager
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler
import org.apache.activemq.artemis.spi.core.remoting.ClientConnectionLifeCycleListener
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager
import org.apache.activemq.artemis.spi.core.remoting.Connector
import org.apache.activemq.artemis.spi.core.remoting.ConnectorFactory
import org.apache.activemq.artemis.utils.ConfigurationHelper
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService

class CordaNettyConnectorFactory : ConnectorFactory {
    override fun createConnector(configuration: MutableMap<String, Any>?,
                                 handler: BufferHandler?,
                                 listener: ClientConnectionLifeCycleListener?,
                                 closeExecutor: Executor,
                                 threadPool: Executor,
                                 scheduledThreadPool: ScheduledExecutorService,
                                 protocolManager: ClientProtocolManager?): Connector {
        val threadPoolName = ConfigurationHelper.getStringProperty(ArtemisTcpTransport.THREAD_POOL_NAME_NAME, "Connector", configuration)
        setThreadPoolName(threadPool, closeExecutor, scheduledThreadPool, threadPoolName)
        val trace = ConfigurationHelper.getBooleanProperty(ArtemisTcpTransport.TRACE_NAME, false, configuration)
        return NettyConnector(
                configuration,
                handler,
                listener,
                closeExecutor,
                threadPool,
                scheduledThreadPool,
                MyClientProtocolManager("$threadPoolName-netty", trace)
        )
    }

    override fun isReliable(): Boolean = false

    override fun getDefaults(): Map<String?, Any?> = NettyConnector.DEFAULT_CONFIG

    private fun setThreadPoolName(threadPool: Executor, closeExecutor: Executor, scheduledThreadPool: ScheduledExecutorService, name: String) {
        threadPool.setThreadPoolName("$name-artemis")
        // Artemis will actually wrap the same backing Executor to create multiple "OrderedExecutors". In this scenerio both the threadPool
        // and the closeExecutor are the same when it comes to the pool names. If however they are different then given them separate names.
        if (threadPool.rootExecutor !== closeExecutor.rootExecutor) {
            closeExecutor.setThreadPoolName("$name-artemis-closer")
        }
        // The scheduler is separate
        scheduledThreadPool.setThreadPoolName("$name-artemis-scheduler")
    }


    private class MyClientProtocolManager(private val threadPoolName: String, private val trace: Boolean) : ActiveMQClientProtocolManager() {
        override fun addChannelHandlers(pipeline: ChannelPipeline) {
            applyThreadPoolName()
            super.addChannelHandlers(pipeline)
            if (trace) {
                pipeline.addLast("logger", LoggingHandler(LogLevel.INFO))
            }
        }

        /**
         * [NettyConnector.start] does not provide a way to configure the thread pool name, so we modify the thread name accordingly.
         */
        private fun applyThreadPoolName() {
            with(Thread.currentThread()) {
                name = name.replace("nioEventLoopGroup", threadPoolName)  // pool and thread numbers are preserved
            }
        }
    }
}
