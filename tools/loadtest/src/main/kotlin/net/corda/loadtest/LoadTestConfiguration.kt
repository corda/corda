package net.corda.loadtest

import java.nio.file.Path

/**
 * @param sshUser The UNIX username to use for SSH auth.
 * @param localCertificatesBaseDirectory The base directory to put node certificates in.
 * @param localTunnelStartingPort The local starting port to allocate tunneling ports from.
 * @param nodeHosts The nodes' resolvable addresses.
 * @param remoteNodeDirectory The remote node directory.
 * @param remoteMessagingPort The remote Artemis messaging port.
 * @param remoteSystemdServiceName The name of the node's systemd service
 * @param seed An optional starting seed for the [SplittableRandom] RNG. Note that specifying the seed may not be enough
 *     to make a load test reproducible due to unpredictable node behaviour, but it should make the local number
 *     generation deterministic as long as [SplittableRandom.split] is used as required. This RNG is also used as input
 *     for disruptions.
 */
data class LoadTestConfiguration(
        val sshUser: String,
        val localCertificatesBaseDirectory: Path,
        val localTunnelStartingPort: Int,
        val nodeHosts: List<String>,
        val remoteNodeDirectory: Path,
        val remoteMessagingPort: Int,
        val remoteSystemdServiceName: String,
        val seed: Long?
)
