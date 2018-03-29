/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman

import com.google.common.primitives.Booleans
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import java.net.URL
import java.nio.file.Path
import java.util.*

data class NetworkManagementServerConfig( // TODO: Move local signing to signing server.
        val address: NetworkHostAndPort,
        val dataSourceProperties: Properties,
        val database: DatabaseConfig = DatabaseConfig(),

        val doorman: DoormanConfig?,
        val networkMap: NetworkMapConfig?,
        val revocation: CertificateRevocationConfig?,

        // TODO Should be part of a localSigning sub-config
        val keystorePath: Path? = null,
        // TODO Should be part of a localSigning sub-config
        val rootStorePath: Path? = null,
        val keystorePassword: String?,
        // TODO Should be part of a localSigning sub-config
        val caPrivateKeyPassword: String?,
        // TODO Should be part of a localSigning sub-config
        val rootKeystorePassword: String?,
        // TODO Should be part of a localSigning sub-config
        val rootPrivateKeyPassword: String?
) {
    companion object {
        // TODO: Do we really need these defaults?
        val DEFAULT_APPROVE_INTERVAL = 5.seconds
        val DEFAULT_SIGN_INTERVAL = 5.seconds
    }
}

data class DoormanConfig(val approveAll: Boolean = false,
                         val jira: JiraConfig? = null,
                         val approveInterval: Long = NetworkManagementServerConfig.DEFAULT_APPROVE_INTERVAL.toMillis()) {
    init {
        require(Booleans.countTrue(approveAll, jira != null) == 1) {
            "Either 'approveAll' or 'jira' config settings need to be specified but not both"
        }
    }
}

data class CertificateRevocationConfig(val approveAll: Boolean = false,
                                       val jira: JiraConfig? = null,
                                       val localSigning: LocalSigning?,
                                       val crlCacheTimeout: Long,
                                       val approveInterval: Long = NetworkManagementServerConfig.DEFAULT_APPROVE_INTERVAL.toMillis()) {
    init {
        require(Booleans.countTrue(approveAll, jira != null) == 1) {
            "Either 'approveAll' or 'jira' config settings need to be specified but not both"
        }
    }

    data class LocalSigning(val crlUpdateInterval: Long,
                            val crlEndpoint: URL)
}

data class NetworkMapConfig(val cacheTimeout: Long,
        // TODO: Move signing to signing server.
                            val signInterval: Long = NetworkManagementServerConfig.DEFAULT_SIGN_INTERVAL.toMillis())

enum class Mode {
    // TODO CA_KEYGEN now also generates the network map cert, so it should be renamed.
    DOORMAN,
    CA_KEYGEN,
    ROOT_KEYGEN
}

data class JiraConfig(
        val address: String,
        val projectCode: String,
        val username: String,
        val password: String
)
