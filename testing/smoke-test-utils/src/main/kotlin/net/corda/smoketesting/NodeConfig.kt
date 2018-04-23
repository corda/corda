/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.smoketesting

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory.empty
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.config.User

class NodeConfig(
        val legalName: CordaX500Name,
        val p2pPort: Int,
        val rpcPort: Int,
        val rpcAdminPort: Int,
        val isNotary: Boolean,
        val users: List<User>,
        val runMigration: Boolean = true,
        val jarDirs: List<String> = emptyList()
) {
    companion object {
        val renderOptions: ConfigRenderOptions = ConfigRenderOptions.defaults().setOriginComments(false)
    }

    val commonName: String get() = legalName.organisation

    /*
     * The configuration object depends upon the networkMap,
     * which is mutable.
     */
    //TODO Make use of Any.toConfig
    private fun toFileConfig(): Config {
        val config = empty()
                .withValue("myLegalName", valueFor(legalName.toString()))
                .withValue("p2pAddress", addressValueFor(p2pPort))
                .withValue("rpcSettings", empty()
                        .withValue("address", addressValueFor(rpcPort))
                        .withValue("adminAddress", addressValueFor(rpcAdminPort))
                        .root())
                .withValue("rpcUsers", valueFor(users.map(User::toMap).toList()))
                .withValue("database", valueFor(mapOf("runMigration" to runMigration)))
                .withValue("useTestClock", valueFor(true))
                .withValue("jarDirs", valueFor(jarDirs))
        return if (isNotary) {
            config.withValue("notary", ConfigValueFactory.fromMap(mapOf("validating" to true)))
        } else {
            config
        }
    }

    fun toText(): String = toFileConfig().root().render(renderOptions)

    private fun <T> valueFor(any: T): ConfigValue? = ConfigValueFactory.fromAnyRef(any)

    private fun addressValueFor(port: Int) = valueFor("localhost:$port")
}
