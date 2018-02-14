package net.corda.plugins

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import groovy.lang.Closure
import net.corda.cordform.CordformNode
import net.corda.cordform.RpcSettings
import org.apache.commons.io.FilenameUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

/**
 * Represents a node that will be installed.
 */
open class Node @Inject constructor(private val project: Project) : CordformNode() {
    private data class ResolvedCordapp(val jarFile: File, val config: String?)

    companion object {
        @JvmStatic
        val webJarName = "corda-webserver.jar"
        private val configFileProperty = "configFile"
    }

    /**
     * Set the list of CorDapps to install to the plugins directory. Each cordapp is a fully qualified Maven
     * dependency name, eg: com.example:product-name:0.1
     *
     * @note Your app will be installed by default and does not need to be included here.
     * @note Type is any due to gradle's use of "GStrings" - each value will have "toString" called on it
     */
    var cordapps: MutableList<Any>
        get() = internalCordapps as MutableList<Any>
        @Deprecated("Use cordapp instead - setter will be removed by Corda V4.0")
        set(value) {
            value.forEach {
                cordapp(it.toString())
            }
        }

    private val internalCordapps = mutableListOf<Cordapp>()
    private val builtCordapp = Cordapp(project)
    internal lateinit var nodeDir: File
        private set
    internal lateinit var rootDir: File
        private set
    internal lateinit var containerName: String
        private set
    internal var rpcSettings: RpcSettings = RpcSettings()
        private set
    internal var webserverJar: String? = null
        private set

    /**
     * Sets whether this node will use HTTPS communication.
     *
     * @param isHttps True if this node uses HTTPS communication.
     */
    fun https(isHttps: Boolean) {
        config = config.withValue("useHTTPS", ConfigValueFactory.fromAnyRef(isHttps))
    }

    /**
     * Sets the H2 port for this node
     */
    fun h2Port(h2Port: Int) {
        config = config.withValue("h2port", ConfigValueFactory.fromAnyRef(h2Port))
    }

    fun useTestClock(useTestClock: Boolean) {
        config = config.withValue("useTestClock", ConfigValueFactory.fromAnyRef(useTestClock))
    }

    /**
     * Specifies RPC settings for the node.
     */
    fun rpcSettings(configureClosure: Closure<in RpcSettings>) {
        rpcSettings = project.configure(RpcSettings(), configureClosure) as RpcSettings
        config = rpcSettings.addTo("rpcSettings", config)
    }

    /**
     * Enables SSH access on given port
     *
     * @param sshdPort The port for SSH server to listen on
     */
    fun sshdPort(sshdPort: Int) {
        config = config.withValue("sshdAddress",
                ConfigValueFactory.fromAnyRef("$DEFAULT_HOST:$sshdPort"))
    }

    /**
     * Install a cordapp to this node
     *
     * @param coordinates The coordinates of the [Cordapp]
     * @param configureClosure A groovy closure to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(coordinates: String, configureClosure: Closure<in Cordapp>): Cordapp {
        val cordapp = project.configure(Cordapp(coordinates), configureClosure) as Cordapp
        internalCordapps += cordapp
        return cordapp
    }

    /**
     * Install a cordapp to this node
     *
     * @param cordappProject A project that produces a cordapp JAR
     * @param configureClosure A groovy closure to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(cordappProject: Project, configureClosure: Closure<in Cordapp>): Cordapp {
        val cordapp = project.configure(Cordapp(cordappProject), configureClosure) as Cordapp
        internalCordapps += cordapp
        return cordapp
    }

    /**
     * Install a cordapp to this node
     *
     * @param cordappProject A project that produces a cordapp JAR
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(cordappProject: Project): Cordapp {
        return Cordapp(cordappProject).apply {
            internalCordapps += this
        }
    }

    /**
     * Install a cordapp to this node
     *
     * @param coordinates The coordinates of the [Cordapp]
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(coordinates: String): Cordapp {
        return Cordapp(coordinates).apply {
            internalCordapps += this
        }
    }

    /**
     * Install a cordapp to this node
     *
     * @param configureFunc A lambda to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(coordinates: String, configureFunc: Cordapp.() -> Unit): Cordapp {
        return Cordapp(coordinates).apply {
            configureFunc()
            internalCordapps += this
        }
    }

    /**
     * Configures the default cordapp automatically added to this node from this project
     *
     * @param configureClosure A groovy closure to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun projectCordapp(configureClosure: Closure<in Cordapp>): Cordapp {
        project.configure(builtCordapp, configureClosure) as Cordapp
        return builtCordapp
    }

    /**
     * The webserver JAR to be used by this node.
     *
     * If not provided, the default development webserver is used.
     *
     * @param webserverJar The file path of the webserver JAR to use.
     */
    fun webserverJar(webserverJar: String) {
        this.webserverJar = webserverJar
    }

    internal fun build() {
        if (config.hasPath("webAddress")) {
            installWebserverJar()
        }
        installAgentJar()
        installCordapps()
        installConfig()
    }

    internal fun buildDocker() {
        project.copy {
            it.apply {
                from(Cordformation.getPluginFile(project, "net/corda/plugins/Dockerfile"))
                from(Cordformation.getPluginFile(project, "net/corda/plugins/run-corda.sh"))
                into("$nodeDir/")
            }
        }
        installAgentJar()
        installCordapps()
    }

    internal fun rootDir(rootDir: Path) {
        if (name == null) {
            project.logger.error("Node has a null name - cannot create node")
            throw IllegalStateException("Node has a null name - cannot create node")
        }
        // Parsing O= part directly because importing BouncyCastle provider in Cordformation causes problems
        // with loading our custom X509EdDSAEngine.
        val organizationName = name.trim().split(",").firstOrNull { it.startsWith("O=") }?.substringAfter("=")
        val dirName = organizationName ?: name
        containerName = dirName.replace("\\s+".toRegex(), "-").toLowerCase()
        this.rootDir = rootDir.toFile()
        nodeDir = File(this.rootDir, dirName.replace("\\s", ""))
        Files.createDirectories(nodeDir.toPath())
    }

    private fun configureProperties() {
        if (rpcUsers != null) {
            config = config.withValue("security", ConfigValueFactory.fromMap(mapOf(
                    "authService" to mapOf(
                            "dataSource" to mapOf(
                                    "type" to "INMEMORY",
                                    "users" to rpcUsers)))))

        }

        if (notary != null) {
            config = config.withValue("notary", ConfigValueFactory.fromMap(notary))
        }
        if (extraConfig != null) {
            config = config.withFallback(ConfigFactory.parseMap(extraConfig))
        }
    }

    /**
     * Installs the corda webserver JAR to the node directory
     */
    private fun installWebserverJar() {
        // If no webserver JAR is provided, the default development webserver is used.
        val webJar = if (webserverJar == null) {
            project.logger.info("Using default development webserver.")
            Cordformation.verifyAndGetRuntimeJar(project, "corda-webserver")
        } else {
            project.logger.info("Using custom webserver: $webserverJar.")
            File(webserverJar)
        }
        
        project.copy {
            it.apply {
                from(webJar)
                into(nodeDir)
                rename(webJar.name, webJarName)
            }
        }
    }

    /**
     * Installs the jolokia monitoring agent JAR to the node/drivers directory
     */
    private fun installAgentJar() {
        // TODO: improve how we re-use existing declared external variables from root gradle.build
        val jolokiaVersion = try { project.rootProject.ext<String>("jolokia_version") } catch (e: Exception) { "1.3.7" }
        val agentJar = project.configuration("runtime").files {
            (it.group == "org.jolokia") &&
                    (it.name == "jolokia-jvm") &&
                    (it.version == jolokiaVersion)
            // TODO: revisit when classifier attribute is added. eg && (it.classifier = "agent")
        }.first()  // should always be the jolokia agent fat jar: eg. jolokia-jvm-1.3.7-agent.jar
        project.logger.info("Jolokia agent jar: $agentJar")
        if (agentJar.isFile) {
            val driversDir = File(nodeDir, "drivers")
            project.copy {
                it.apply {
                    from(agentJar)
                    into(driversDir)
                }
            }
        }
    }

    private fun installCordappConfigs(cordapps: Collection<ResolvedCordapp>) {
        val cordappsDir = project.file(File(nodeDir, "cordapps"))
        cordappsDir.mkdirs()
        cordapps.filter { it.config != null }
                .map { Pair<String, String>("${FilenameUtils.removeExtension(it.jarFile.name)}.conf", it.config!!) }
                .forEach { project.file(File(cordappsDir, it.first)).writeText(it.second) }
    }

    private fun createTempConfigFile(configObject: ConfigObject): File {
        val options = ConfigRenderOptions
                .defaults()
                .setOriginComments(false)
                .setComments(false)
                .setFormatted(true)
                .setJson(false)
        val configFileText = configObject.render(options).split("\n").toList()
        // Need to write a temporary file first to use the project.copy, which resolves directories correctly.
        val tmpDir = File(project.buildDir, "tmp")
        Files.createDirectories(tmpDir.toPath())
        var fileName = "${nodeDir.name}.conf"
        val tmpConfFile = File(tmpDir, fileName)
        Files.write(tmpConfFile.toPath(), configFileText, StandardCharsets.UTF_8)
        return tmpConfFile
    }

    /**
     * Installs the configuration file to the root directory and detokenises it.
     */
    fun installConfig() {
        configureProperties()
        val tmpConfFile = createTempConfigFile(config.root())
        appendOptionalConfig(tmpConfFile)
        project.copy {
            it.apply {
                from(tmpConfFile)
                into(rootDir)
            }
        }
    }

    /**
     * Installs the Dockerized configuration file to the root directory and detokenises it.
     */
    internal fun installDockerConfig() {
        configureProperties()
        val dockerConf = config
                .withValue("p2pAddress", ConfigValueFactory.fromAnyRef("$containerName:$p2pPort"))
                .withValue("rpcSettings.address", ConfigValueFactory.fromAnyRef("$containerName:${rpcSettings.port}"))
                .withValue("rpcSettings.adminAddress", ConfigValueFactory.fromAnyRef("$containerName:${rpcSettings.adminPort}"))
                .withValue("detectPublicIp", ConfigValueFactory.fromAnyRef(false))
        val tmpConfFile = createTempConfigFile(dockerConf.root())
        appendOptionalConfig(tmpConfFile)
        project.copy {
            it.apply {
                from(tmpConfFile)
                into(rootDir)
            }
        }
    }

    /**
     * Appends installed config file with properties from an optional file.
     */
    private fun appendOptionalConfig(confFile: File) {
        val optionalConfig: File? = when {
            project.findProperty(configFileProperty) != null -> //provided by -PconfigFile command line property when running Gradle task
                File(project.findProperty(configFileProperty) as String)
            config.hasPath(configFileProperty) -> File(config.getString(configFileProperty))
            else -> null
        }

        if (optionalConfig != null) {
            if (!optionalConfig.exists()) {
                project.logger.error("$configFileProperty '$optionalConfig' not found")
            } else {
                confFile.appendBytes(optionalConfig.readBytes())
            }
        }
    }


    /**
     * Installs the jolokia monitoring agent JAR to the node/drivers directory
     */
    private fun installCordapps() {
        val cordapps = getCordappList()
        val cordappsDir = File(nodeDir, "cordapps")
        project.copy {
            it.apply {
                from(cordapps.map { it.jarFile })
                into(project.file(cordappsDir))
            }
        }

        installCordappConfigs(cordapps)
    }

    /**
     * Gets a list of cordapps based on what dependent cordapps were specified.
     *
     * @return List of this node's cordapps.
     */
    private fun getCordappList(): Collection<ResolvedCordapp> =
            internalCordapps.map { cordapp -> resolveCordapp(cordapp) } + resolveBuiltCordapp()

    private fun resolveCordapp(cordapp: Cordapp): ResolvedCordapp {
        val cordappConfiguration = project.configuration("cordapp")
        val cordappName = if (cordapp.project != null) cordapp.project.name else cordapp.coordinates
        val cordappFile = cordappConfiguration.files {
            when {
                (it is ProjectDependency) && (cordapp.project != null) -> it.dependencyProject == cordapp.project
                cordapp.coordinates != null -> {
                    // Cordapps can sometimes contain a GString instance which fails the equality test with the Java string
                    @Suppress("RemoveRedundantCallsOfConversionMethods")
                    val coordinates = cordapp.coordinates.toString()
                    coordinates == (it.group + ":" + it.name + ":" + it.version)
                }
                else -> false
            }
        }

        return when {
            cordappFile.size == 0 -> throw GradleException("Cordapp $cordappName not found in cordapps configuration.")
            cordappFile.size > 1 -> throw GradleException("Multiple files found for $cordappName")
            else -> ResolvedCordapp(cordappFile.single(), cordapp.config)
        }
    }

    private fun resolveBuiltCordapp(): ResolvedCordapp {
        val projectCordappFile = project.tasks.getByName("jar").outputs.files.singleFile
        return ResolvedCordapp(projectCordappFile, builtCordapp.config)
    }
}
