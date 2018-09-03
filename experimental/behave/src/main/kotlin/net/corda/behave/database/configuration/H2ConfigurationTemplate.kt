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