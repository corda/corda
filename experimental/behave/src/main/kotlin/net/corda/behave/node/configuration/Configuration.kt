package net.corda.behave.node.configuration

import net.corda.behave.database.DatabaseType
import net.corda.behave.logging.getLogger
import net.corda.behave.node.*
import net.corda.core.identity.CordaX500Name
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
        val notary: NotaryConfiguration = NotaryConfiguration(),
        val cordapps: CordappConfiguration = CordappConfiguration(),
        vararg configElements: ConfigurationTemplate
) {

    private val developerMode = (distribution.type == Distribution.Type.CORDA)

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

    fun writeToFile(file: File) {
        FileUtils.writeStringToFile(file, this.generate(), "UTF-8")
        log.info(this.generate())
    }

    private fun generate() = listOf(basicConfig, database.config(), extraConfig)
            .filter { it.isNotBlank() }
            .joinToString("\n")

    companion object {
        private val log = getLogger<Configuration>()
        val DEFAULT_PASSWORD = "S0meS3cretW0rd"
    }

}
