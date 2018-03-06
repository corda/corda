/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.node.configuration

import net.corda.behave.database.DatabaseType
import net.corda.behave.node.*
import org.apache.commons.io.FileUtils
import java.io.File

class Configuration(
        val name: String,
        val distribution: Distribution = Distribution.LATEST_MASTER,
        val databaseType: DatabaseType = DatabaseType.H2,
        val location: String = "London",
        val country: String = "GB",
        val users: UserConfiguration = UserConfiguration().withUser("corda", DEFAULT_PASSWORD),
        val nodeInterface: NetworkInterface = NetworkInterface(),
        val database: DatabaseConfiguration = DatabaseConfiguration(
                databaseType,
                nodeInterface.host,
                nodeInterface.dbPort,
                password = DEFAULT_PASSWORD
        ),
        vararg configElements: ConfigurationTemplate
) {

    private val developerMode = true

    private val useHttps = false

    private val basicConfig = """
            |myLegalName="C=$country,L=$location,O=$name"
            |keyStorePassword="cordacadevpass"
            |trustStorePassword="trustpass"
            |extraAdvertisedServiceIds=[ "" ]
            |useHTTPS=$useHttps
            |devMode=$developerMode
            |jarDirs = [ "../libs" ]
            """.trimMargin()

    private val extraConfig = (configElements.toList() + listOf(users, nodeInterface))
            .joinToString(separator = "\n") { it.generate(this) }

    fun writeToFile(file: File) {
        FileUtils.writeStringToFile(file, this.generate(), "UTF-8")
    }

    private fun generate() = listOf(basicConfig, database.config(), extraConfig)
            .filter { it.isNotBlank() }
            .joinToString("\n")

    companion object {

        private val DEFAULT_PASSWORD = "S0meS3cretW0rd"

    }

}
