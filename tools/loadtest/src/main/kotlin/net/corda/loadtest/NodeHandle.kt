package net.corda.loadtest

import net.corda.core.node.NodeInfo
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(NodeHandle::class.java)

data class NodeHandle(
        val configuration: LoadTestConfiguration,
        val connection: NodeConnection,
        val info: NodeInfo
)

fun <A> NodeHandle.doWhileStopped(action: NodeHandle.() -> A): A {
    return connection.doWhileClientStopped {
        connection.runShellCommandGetOutput("sudo systemctl stop ${configuration.remoteSystemdServiceName}").getResultOrThrow()
        try {
            action()
        } finally {
            connection.runShellCommandGetOutput("sudo systemctl start ${configuration.remoteSystemdServiceName}").getResultOrThrow()
            waitUntilUp()
        }
    }
}

fun <A> NodeHandle.doWhileSigStopped(action: NodeHandle.() -> A): A {
    val pid = getNodePid()
    log.info("PID is $pid")
    connection.runShellCommandGetOutput("sudo kill -SIGSTOP $pid").getResultOrThrow()
    try {
        return action()
    } finally {
        connection.runShellCommandGetOutput("sudo kill -SIGCONT $pid").getResultOrThrow()
    }
}

fun NodeHandle.clearDb() = doWhileStopped {
    connection.runShellCommandGetOutput("sudo rm ${configuration.remoteNodeDirectory}/persistence.mv.db").getResultOrThrow()
}

fun NodeHandle.waitUntilUp() {
    log.info("Waiting for ${info.legalIdentity} to come online")
    connection.runShellCommandGetOutput("until sudo netstat -tlpn | grep ${configuration.remoteMessagingPort} > /dev/null ; do sleep 1 ; done")
}

fun NodeHandle.getNodePid(): String {
    return connection.runShellCommandGetOutput("sudo netstat -tlpn | grep ${configuration.remoteMessagingPort} | awk '{print $7}' | grep -oE '[0-9]+'").getResultOrThrow()
}
