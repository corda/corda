package net.corda.behave.node.configuration

import net.corda.behave.database.DatabaseType
import net.corda.behave.node.Distribution
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.writeText
import net.corda.core.utilities.contextLogger
import java.nio.file.Path

class Configuration(
        val name: String,
        val distribution: Distribution = Distribution.MASTER,
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
        val notary: NotaryConfiguration = NotaryConfiguration(),
        val cordapps: CordappConfiguration = CordappConfiguration(),
        vararg configElements: ConfigurationTemplate
) {

    private val developerMode = true

    val cordaX500Name: CordaX500Name by lazy({
        CordaX500Name(name, location, country)
    })

    private val basicConfig = """
            |myLegalName="C=$country,L=$location,O=$name"
            |keyStorePassword="cordacadevpass"
            |trustStorePassword="trustpass"
            |devMode=$developerMode
            |jarDirs = [ "../libs" ]
            """.trimMargin()

    private val extraConfig = (configElements.toList() + listOf(users, nodeInterface))
            .joinToString(separator = "\n") { it.generate(this) }

    fun writeToFile(file: Path) {
        file.writeText(this.generate())
        log.debug(this.generate())
    }

    private fun generate() = listOf(basicConfig, database.config(), extraConfig)
            .filter { it.isNotBlank() }
            .joinToString("\n")

    companion object {
        private val log = contextLogger()
        const val DEFAULT_PASSWORD = "S0meS3cretW0rd"
    }
}
