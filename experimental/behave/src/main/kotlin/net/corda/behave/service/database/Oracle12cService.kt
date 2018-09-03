package net.corda.behave.service.database

import net.corda.behave.database.DatabaseType
import net.corda.behave.service.ServiceSettings
import net.corda.core.utilities.minutes

class Oracle12cService(
        name: String,
        port: Int,
        password: String,
        settings: ServiceSettings = ServiceSettings(startupTimeout = 10.minutes)
) : OracleService(name, port, "Database initialized.", password, settings) {

    override val baseImage = "sath89/oracle-12c"
    override val type = DatabaseType.ORACLE_12C

    companion object {
        const val driver = "ojdbc6.jar"
    }
}