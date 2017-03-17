package net.corda.loadtest

import com.typesafe.config.Config
import net.corda.nodeapi.config.getValue
import java.nio.file.Path

/**
 * @param sshUser The UNIX username to use for SSH auth.
 * @param localCertificatesBaseDirectory The base directory to put node certificates in.
 * @param localTunnelStartingPort The local starting port to allocate tunneling ports from.
 * @param nodeHosts The nodes' resolvable addresses.
 * @param rpcUsername The RPC user's name to establish the RPC connection as.
 * @param rpcPassword The RPC user's password.
 * @param remoteNodeDirectory The remote node directory.
 * @param remoteMessagingPort The remote Artemis messaging port.
 * @param remoteSystemdServiceName The name of the node's systemd service
 * @param seed An optional starting seed for the [SplittableRandom] RNG. Note that specifying the seed may not be enough
 *     to make a load test reproducible due to unpredictable node behaviour, but it should make the local number
 *     generation deterministic as long as [SplittableRandom.split] is used as required. This RNG is also used as input
 *     for disruptions.
 */
data class LoadTestConfiguration(
        val config: Config
) {
    val sshUser: String by config
    val localCertificatesBaseDirectory: Path by config
    val localTunnelStartingPort: Int by config
    val nodeHosts: List<String> = config.getStringList("nodeHosts")
    val rpcUsername: String by config
    val rpcPassword: String by config
    val remoteNodeDirectory: Path by config
    val remoteMessagingPort: Int by config
    val remoteSystemdServiceName: String by config
    val seed: Long? by config
}
