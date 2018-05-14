/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.webserver

import com.typesafe.config.Config
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.config.getValue
import net.corda.nodeapi.internal.config.parseAs
import java.nio.file.Path

/**
 * [baseDirectory] is not retrieved from the config file but rather from a command line argument.
 */
class WebServerConfig(override val baseDirectory: Path, val config: Config) : NodeSSLConfiguration {
    override val keyStorePassword: String by config
    override val trustStorePassword: String by config
    override val crlCheckSoftFail: Boolean by config
    val useHTTPS: Boolean by config
    val myLegalName: String by config
    val rpcAddress: NetworkHostAndPort by lazy {
        if (config.hasPath("rpcSettings.address")) {
            return@lazy NetworkHostAndPort.parse(config.getConfig("rpcSettings").getString("address"))
        }
        if (config.hasPath("rpcAddress")) {
            return@lazy NetworkHostAndPort.parse(config.getString("rpcAddress"))
        }
        throw Exception("Missing rpc address property. Either 'rpcSettings' or 'rpcAddress' must be specified.")
    }
    val webAddress: NetworkHostAndPort by config
    val runAs: User

    init {
        // TODO: replace with credentials supplied by a user
        val users = if (config.hasPath("rpcUsers")) {
            // TODO: remove this once config format is updated
            config.getConfigList("rpcUsers")
        } else {
            config.getConfigList("security.authService.dataSource.users")
        }
        runAs = users.first().parseAs()
    }
}
