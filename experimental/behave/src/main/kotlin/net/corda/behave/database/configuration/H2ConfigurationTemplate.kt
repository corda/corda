/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.database.configuration

import net.corda.behave.database.DatabaseConfigurationTemplate
import net.corda.behave.node.configuration.DatabaseConfiguration

class H2ConfigurationTemplate : DatabaseConfigurationTemplate() {

    override val connectionString: (DatabaseConfiguration) -> String
        get() = { "jdbc:h2:tcp://${it.host}:${it.port}/${it.database}" }

    override val config: (DatabaseConfiguration) -> String
        get() = {
            """
            |h2port=${it.port}
            """
        }
}