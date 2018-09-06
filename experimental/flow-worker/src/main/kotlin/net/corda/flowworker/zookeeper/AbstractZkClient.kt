package net.corda.flowworker.zookeeper

import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.curator.retry.RetryForever
import org.apache.curator.retry.RetryNTimes
import org.apache.curator.utils.CloseableUtils
import java.io.Closeable

abstract class AbstractZkClient(connectionString: String,
                                retryInterval: Int = 500,
                                retryCount: Int = 1) : Closeable {

    protected val client: CuratorFramework

    init {
        val retryPolicy = if (retryCount == -1) {
            RetryForever(retryInterval)
        } else {
            RetryNTimes(retryCount, retryInterval)
        }
        client = CuratorFrameworkFactory.newClient(connectionString, retryPolicy)
    }

    fun start() {
        if (client.state != CuratorFrameworkState.STARTED) {
            client.start()
            startInternal()
        }
    }

    protected abstract fun startInternal()

    fun isStarted(): Boolean {
        return client.state == CuratorFrameworkState.STARTED
    }

    override fun close() {
        CloseableUtils.closeQuietly(client)
    }
}
