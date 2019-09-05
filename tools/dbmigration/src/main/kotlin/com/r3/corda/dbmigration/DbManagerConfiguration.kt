package com.r3.corda.dbmigration

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.common.logging.CordaVersion
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.schemas.MappedSchema
import net.corda.node.VersionInfo
import net.corda.node.internal.DataSourceFactory
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.config.configOf
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.utilities.NotaryLoader
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.SchemaMigration
import net.corda.notary.standalonejpa.JPANotarySchemaV1
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.sql.DataSource

data class Configuration(val dataSourceProperties: Properties,
                         val database: DatabaseConfig,
                         val jarDirs: List<String> = emptyList(),
                         val myLegalName: CordaX500Name,
                         val notary: NotaryConfig?)

abstract class DbManagerConfiguration(private val cmdLineOptions: SharedDbManagerOptions) {
    protected abstract val defaultConfigFileName: String
    abstract val schemas: Set<MappedSchema>
    abstract val classLoader: ClassLoader
    abstract val cordappLoader: CordappLoader?
    abstract val parsedConfig: Config
    abstract val dataSourceProperties: Properties
    val config by lazy { parsedConfig.parseAs(Configuration::class, UnknownConfigKeysPolicy.IGNORE::handle) }
    val notaryLoader by lazy {
        config.notary?.let {
            NotaryLoader(it, VersionInfo(PLATFORM_VERSION, CordaVersion.releaseVersion, CordaVersion.revision, CordaVersion.vendor))
        }
    }
    val baseDirectory: Path by lazy {
        val dir = cmdLineOptions.baseDirectory?.toAbsolutePath()?.normalize()
                ?: throw error("You must specify a base-directory")
        if (!dir.exists()) throw error("Could not find base-directory: '${cmdLineOptions.baseDirectory}'.")
        dir
    }
    val configFile by lazy { baseDirectory / (cmdLineOptions.configFile ?: (defaultConfigFileName)) }
    fun runWithDataSource(withDatasource: (DataSource) -> Unit) {
        val driversFolder = (baseDirectory / "drivers").let { if (it.exists()) listOf(it) else emptyList() }
        val jarDirs = config.jarDirs.map { Paths.get(it) }
        for (jarDir in jarDirs) {
            if (!jarDir.exists()) {
                throw error("Could not find the configured JDBC driver directory: '$jarDir'.")
            }
        }
        val dataSource = createDataSource(driversFolder, jarDirs)
        try {
            withDatasource(dataSource)
        } catch (e: Exception) {
            throw wrappedError("Migration failed:\nCaused By $e", e)
        }
    }

    private fun createDataSource(driversFolder: List<Path>, jarDirs: List<Path>): DataSource {
        return try {
            DataSourceFactory.createDatasourceFromDriverJarFolders(dataSourceProperties, classLoader, driversFolder + jarDirs)
        } catch (e: Exception) {
            throw wrappedError("""Failed to create datasource.
                    |Please check that the correct JDBC driver is installed in one of the following folders:
                    |${(driversFolder + jarDirs).joinToString("\n\t - ", "\t - ")}
                    |Caused By $e""".trimMargin(), e)
        }
    }

    fun runMigrationCommand(schemas: Set<MappedSchema>, withMigration: (SchemaMigration, DataSource) -> Unit): Unit = this.runWithDataSource { dataSource ->
        withMigration(SchemaMigration(schemas, dataSource, config.database, cordappLoader, baseDirectory, ourName = config.myLegalName), dataSource)
    }
}

class ConfigurationException(message: String) : Exception(message)
class WrappedConfigurationException(message: String, val innerException: Exception) : Exception(message)

fun wrappedError(message: String, innerException: Exception): WrappedConfigurationException {
    errorLogger.error(message, innerException)
    return WrappedConfigurationException(message, innerException)
}

fun error(message: String): Throwable {
    errorLogger.error(message)
    return ConfigurationException(message)
}

class DoormanDbManagerConfiguration(cmdLineOptions: SharedDbManagerOptions) : DbManagerConfiguration(cmdLineOptions) {
    override val defaultConfigFileName get() = "network-management.conf"
    private val doormanFatJarPath by lazy {
        val fatJarPath = cmdLineOptions.doormanJarPath!!
        if (!fatJarPath.exists()) {
            error("Could not find the doorman JAR in location: '$fatJarPath'.")
        }
        fatJarPath
    }
    override val cordappLoader = null

    override val parsedConfig: Config by lazy {
        ConfigFactory.parseFile(configFile.toFile()).resolve()
    }

    private fun loadMappedSchema(schemaName: String, classLoader: ClassLoader) = classLoader.loadClass(schemaName).kotlin.objectInstance as MappedSchema
    override val schemas: Set<MappedSchema> by lazy {
        val doormanSchema = "com.r3.corda.networkmanage.common.persistence.NetworkManagementSchemaServices\$SchemaV1"
        setOf(loadMappedSchema(doormanSchema, classLoader))
    }

    private fun classLoaderFromJar(jarPath: Path): ClassLoader = URLClassLoader(listOf(jarPath.toUri().toURL()).toTypedArray())
    override val classLoader by lazy { classLoaderFromJar(doormanFatJarPath) }
    override val dataSourceProperties by lazy { config.dataSourceProperties }
}

class NodeDbManagerConfiguration(cmdLineOptions: SharedDbManagerOptions) : DbManagerConfiguration(cmdLineOptions) {
    private val cordappsFolder by lazy { baseDirectory / "cordapps" }
    private val cordappSchemas by lazy {
        if (config.notary?.jpa != null) {
            cordappLoader.cordappSchemas
        } else {
            cordappLoader.cordappSchemas.union(notaryLoader?.builtInNotary?.customSchemas ?: emptySet())
        }
    }

    override val cordappLoader by lazy { JarScanningCordappLoader.fromDirectories(setOf(cordappsFolder)) }
    override val defaultConfigFileName get() = "node.conf"
    override val classLoader by lazy { cordappLoader.appClassLoader }
    override val schemas: Set<MappedSchema> by lazy { NodeSchemaService(extraSchemas = cordappSchemas).schemaOptions.keys }
    override val parsedConfig: Config by lazy {
        ConfigFactory.parseFile(configFile.toFile())
                .withFallback(configOf("baseDirectory" to cmdLineOptions.baseDirectory.toString()))
                .withFallback(ConfigFactory.parseResources("reference.conf", ConfigParseOptions.defaults().setAllowMissing(true)))
                .resolve()
    }

    override val dataSourceProperties by lazy { config.dataSourceProperties }
}

class JPANotaryDbManagerConfiguration(cmdLineOptions: SharedDbManagerOptions) : DbManagerConfiguration(cmdLineOptions) {
    private val cordappsFolder by lazy { baseDirectory / "cordapps" }

    override val cordappLoader by lazy { JarScanningCordappLoader.fromDirectories(setOf(cordappsFolder)) }
    override val defaultConfigFileName get() = "node.conf"
    override val classLoader by lazy { cordappLoader.appClassLoader }
    override val dataSourceProperties by lazy { config.notary!!.jpa!!.dataSource }
    override val schemas: Set<MappedSchema> by lazy { setOf(JPANotarySchemaV1) }
    override val parsedConfig: Config by lazy {
        ConfigFactory.parseFile(configFile.toFile())
                .withFallback(configOf("baseDirectory" to cmdLineOptions.baseDirectory.toString()))
                .withFallback(ConfigFactory.parseResources("reference.conf", ConfigParseOptions.defaults().setAllowMissing(true)))
                .resolve()
    }
}