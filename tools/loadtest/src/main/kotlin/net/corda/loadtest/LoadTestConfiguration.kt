/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.loadtest

import net.corda.nodeapi.internal.config.User
import java.nio.file.Path
import java.util.concurrent.ForkJoinPool

/**
 * @param sshUser The UNIX username to use for SSH auth.
 * @param localTunnelStartingPort The local starting port to allocate tunneling ports from.
 * @param nodeHosts The nodes' resolvable addresses.
 * @param rpcUser The RPC user's name and passward to establish the RPC connection.
 * @param remoteNodeDirectory The remote node directory.
 * @param rpcPort The remote Artemis messaging port for RPC.
 * @param remoteSystemdServiceName The name of the node's systemd service
 * @param seed An optional starting seed for the [SplittableRandom] RNG. Note that specifying the seed may not be enough
 *     to make a load test reproducible due to unpredictable node behaviour, but it should make the local number
 *     generation deterministic as long as [SplittableRandom.split] is used as required. This RNG is also used as input
 *     for disruptions.
 * @param mode Indicates the type of test.
 * @param executionFrequency Indicates how many commands we should execute per second.
 * @param generateCount Number of total commands to generate. Note that the actual number of generated commands may
 *     exceed this, it is used just for cutoff.
 * @param parallelism Number of concurrent threads to use to run commands. Note that the actual parallelism may be
 *     further limited by the batches that [generate] returns.
 */
data class LoadTestConfiguration(
        val sshUser: String = System.getProperty("user.name"),
        val localTunnelStartingPort: Int,
        val nodeHosts: List<String>,
        val rpcUser: User,
        val remoteNodeDirectory: Path,
        val rpcPort: Int,
        val remoteSystemdServiceName: String,
        val seed: Long?,
        val mode: TestMode = TestMode.LOAD_TEST,
        val executionFrequency: Int = 2,
        val generateCount: Int = 10000,
        val parallelism: Int = ForkJoinPool.getCommonPoolParallelism())

data class RemoteNode(val hostname: String, val systemdServiceName: String, val sshUserName: String, val rpcUser: User, val rpcPort: Int, val nodeDirectory: Path)

enum class TestMode {
    LOAD_TEST,
    STABILITY_TEST
}
