package net.corda.plugins

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import groovy.lang.Closure
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.cordform.CordformNode
import org.apache.commons.io.FilenameUtils
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents a node that will be installed.
 */
open class Node @Inject constructor(private val project: Project, private val objectFactory: ObjectFactory) : CordformNode() {
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
                cordapp({
                    coordinates = it.toString()
                })
            }
        }

    private val internalCordapps = mutableListOf<Cordapp>()

    internal var additionalCordapps = mutableListOf<File>()
    internal lateinit var nodeDir: File
        private set
    internal lateinit var rootDir: File
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
     * Enables SSH access on given port
     *
     * @param sshdPort The port for SSH server to listen on
     */
    fun sshdPort(sshdPort: Int?) {
        config = config.withValue("sshd.port", ConfigValueFactory.fromAnyRef(sshdPort))
    }

    internal fun build() {
        if (config.hasPath("webAddress")) {
            installWebserverJar()
        }
        installAgentJar()
        installBuiltCordapp()
        installCordapps()
        installConfig()
        appendOptionalConfig()
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
        this.rootDir = rootDir.toFile()
        nodeDir = File(this.rootDir, dirName)
        Files.createDirectories(nodeDir.toPath())
    }

    private fun configureProperties() {
        config = config.withValue("rpcUsers", ConfigValueFactory.fromIterable(rpcUsers))
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
        val webJar = Cordformation.verifyAndGetRuntimeJar(project, "corda-webserver")
        project.copy {
            it.apply {
                from(webJar)
                into(nodeDir)
                rename(webJar.name, webJarName)
            }
        }
    }

    /**
     * Installs this project's cordapp to this directory.
     */
    private fun installBuiltCordapp() {
        val cordappsDir = File(nodeDir, "cordapps")
        project.copy {
            it.apply {
                from(project.tasks.getByName("jar"))
                into(cordappsDir)
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
                into(cordappsDir)
            }
        }

        installCordappConfigs(cordapps)
    }

    /**
     * Installs the jolokia monitoring agent JAR to the node/drivers directory
     */
    private fun installAgentJar() {
        val jolokiaVersion = project.rootProject.ext<String>("jolokia_version")
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
        val cordappsDir = File(nodeDir, "cordapps")
        cordapps.filter { it.config != null }
                .map { Pair<String, String>("${FilenameUtils.removeExtension(it.jarFile.name)}.conf", it.config!!) }
                .forEach { project.file(File(cordappsDir, it.first)).writeText(it.second) }
    }

    private fun createTempConfigFile(): File {
        val options = ConfigRenderOptions
                .defaults()
                .setOriginComments(false)
                .setComments(false)
                .setFormatted(true)
                .setJson(false)
        val configFileText = config.root().render(options).split("\n").toList()
        // Need to write a temporary file first to use the project.copy, which resolves directories correctly.
        val tmpDir = File(project.buildDir, "tmp")
        Files.createDirectories(tmpDir.toPath())
        var fileName = "${nodeDir.getName()}.conf"
        val tmpConfFile = File(tmpDir, fileName)
        Files.write(tmpConfFile.toPath(), configFileText, StandardCharsets.UTF_8)
        return tmpConfFile
    }

    /**
     * Installs the configuration file to the root directory and detokenises it.
     */
    private fun installConfig() {
        val options = ConfigRenderOptions
                .defaults()
                .setOriginComments(false)
                .setComments(false)
                .setFormatted(true)
                .setJson(false)
        val configFileText = config.root().render(options).split("\n").toList()

        // Need to write a temporary file first to use the project.copy, which resolves directories correctly.
        val tmpDir = File(project.buildDir, "tmp")
        tmpDir.mkdir()
        val tmpConfFile = File(tmpDir, "node.conf")
        Files.write(tmpConfFile.toPath(), configFileText, StandardCharsets.UTF_8)

        project.copy {
            it.apply {
                from(tmpConfFile)
                into(nodeDir)
            }
        }
    }

    /**
     * Appends installed config file with properties from an optional file.
     */
    private fun appendOptionalConfig() {
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
                val confFile = File(project.buildDir.path + "/../" + nodeDir, "node.conf")
                confFile.appendBytes(optionalConfig.readBytes())
            }
        }
    }

    /**
     * Installs other cordapps to this node's cordapps directory.
     */
    internal fun installCordapps() {
        additionalCordapps.addAll(getCordappList())
        val cordappsDir = File(nodeDir, "cordapps")
        project.copy {
            it.apply {
                from(additionalCordapps)
                into(cordappsDir)
            }
        }
    }

    /**
     * Add a cordapp to this node
     *
     * @param configureClosure A groovy closure to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(configureClosure: Closure<in Cordapp>): Cordapp {
        val cordapp = project.configure(objectFactory.newInstance(Cordapp::class.java), configureClosure) as Cordapp
        addCordapp(cordapp)
        return cordapp
    }

    /**
     * Add a cordapp to this node
     *
     * @param configureFunc A lambda to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(configureFunc: Cordapp.() -> Unit): Cordapp {
        val cordapp = objectFactory.newInstance(Cordapp::class.java).apply { configureFunc() }
        addCordapp(cordapp)
        return cordapp
    }


    /**
     * Gets a list of cordapps based on what dependent cordapps were specified.
     *
     * @return List of this node's cordapps.
     */
    private fun getCordappList(): Collection<ResolvedCordapp> {
        val cordappConfiguration = project.configuration("cordapp")
        // Cordapps can sometimes contain a GString instance which fails the equality test with the Java string
        @Suppress("RemoveRedundantCallsOfConversionMethods")
        return internalCordapps.map { cordapp ->
            val cordappName = cordapp.coordinates!!.toString()
            val cordappFile = cordappConfiguration.files { cordappName == (it.group + ":" + it.name + ":" + it.version) }

            when {
                cordappFile.size == 0 -> throw GradleException("Cordapp $cordappName not found in cordapps configuration.")
                cordappFile.size > 1 -> throw GradleException("Multiple files found for $cordappName")
                else -> ResolvedCordapp(cordappFile.single(), cordapp.config)
            }
        }
    }

    private fun addCordapp(cordapp: Cordapp) {
        // TODO: Use gradle @Input annotation to make this required in the build script
        if (cordapp.coordinates == null) {
            throw GradleException("cordapp is missing coordinates field")
        }
        internalCordapps += cordapp
    }
}
