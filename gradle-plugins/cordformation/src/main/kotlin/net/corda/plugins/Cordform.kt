package net.corda.plugins

import groovy.lang.Closure
import net.corda.cordform.CordformDefinition
import org.apache.tools.ant.filters.FixCrLfFilter
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarInputStream

/**
 * Creates nodes based on the configuration of this task in the gradle configuration DSL.
 *
 * See documentation for examples.
 */
@Suppress("unused")
open class Cordform : DefaultTask() {
    private companion object {
        private val defaultDirectory: Path = Paths.get("build", "nodes")
    }

    /**
     * Optionally the name of a CordformDefinition subclass to which all configuration will be delegated.
     */
    @Suppress("MemberVisibilityCanPrivate")
    var definitionClass: String? = null
    private var directory = defaultDirectory
    private val nodes = mutableListOf<Node>()

    /**
     * Set the directory to install nodes into.
     *
     * @param directory The directory the nodes will be installed into.
     */
    fun directory(directory: String) {
        this.directory = Paths.get(directory)
    }

    /**
     * Add a node configuration.
     *
     * @param configureClosure A node configuration that will be deployed.
     */
    @Suppress("MemberVisibilityCanPrivate")
    fun node(configureClosure: Closure<in Node>) {
        nodes += project.configure(Node(project), configureClosure) as Node
    }

    /**
     * Add a node configuration
     *
     * @param configureFunc A node configuration that will be deployed
     */
    @Suppress("MemberVisibilityCanPrivate")
    fun node(configureFunc: Node.() -> Any?): Node {
        val node = Node(project).apply { configureFunc() }
        nodes += node
        return node
    }

    /**
     * Returns a node by name.
     *
     * @param name The name of the node as specified in the node configuration DSL.
     * @return A node instance.
     */
    private fun getNodeByName(name: String): Node? = nodes.firstOrNull { it.name == name }

    /**
     * Installs the run script into the nodes directory.
     */
    private fun installRunScript() {
        project.copy {
            it.apply {
                from(Cordformation.getPluginFile(project, "net/corda/plugins/runnodes.jar"))
                fileMode = Cordformation.executableFileMode
                into("$directory/")
            }
        }

        project.copy {
            it.apply {
                from(Cordformation.getPluginFile(project, "net/corda/plugins/runnodes"))
                // Replaces end of line with lf to avoid issues with the bash interpreter and Windows style line endings.
                filter(mapOf("eol" to FixCrLfFilter.CrLf.newInstance("lf")), FixCrLfFilter::class.java)
                fileMode = Cordformation.executableFileMode
                into("$directory/")
            }
        }

        project.copy {
            it.apply {
                from(Cordformation.getPluginFile(project, "net/corda/plugins/runnodes.bat"))
                into("$directory/")
            }
        }
    }

    /**
     * The definitionClass needn't be compiled until just before our build method, so we load it manually via sourceSets.main.runtimeClasspath.
     */
    private fun loadCordformDefinition(): CordformDefinition {
        val plugin = project.convention.getPlugin(JavaPluginConvention::class.java)
        val classpath = plugin.sourceSets.getByName(MAIN_SOURCE_SET_NAME).runtimeClasspath
        val urls = classpath.files.map { it.toURI().toURL() }.toTypedArray()
        return URLClassLoader(urls, CordformDefinition::class.java.classLoader)
                .loadClass(definitionClass)
                .asSubclass(CordformDefinition::class.java)
                .newInstance()
    }

    /**
     * The NetworkBootstrapper needn't be compiled until just before our build method, so we load it manually via sourceSets.main.runtimeClasspath.
     */
    private fun loadNetworkBootstrapperClass(): Class<*> {
        val plugin = project.convention.getPlugin(JavaPluginConvention::class.java)
        val classpath = plugin.sourceSets.getByName(MAIN_SOURCE_SET_NAME).runtimeClasspath
        val urls = classpath.files.map { it.toURI().toURL() }.toTypedArray()
        return URLClassLoader(urls, javaClass.classLoader).loadClass("net.corda.nodeapi.internal.network.NetworkBootstrapper")
    }

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    fun build() {
        project.logger.info("Running Cordform task")
        initializeConfiguration()
        installRunScript()
        nodes.forEach(Node::build)
        bootstrapNetwork()
    }

    private fun initializeConfiguration() {
        if (definitionClass != null) {
            val cd = loadCordformDefinition()
            // If the user has specified their own directory (even if it's the same default path) then let them know
            // it's not used and should just rely on the one in CordformDefinition
            require(directory === defaultDirectory) {
                "'directory' cannot be used when 'definitionClass' is specified. Use CordformDefinition.nodesDirectory instead."
            }
            directory = cd.nodesDirectory
            val cordapps = cd.getMatchingCordapps()
            cd.nodeConfigurers.forEach {
                val node = node { }
                it.accept(node)
                node.rootDir(directory)
                node.installCordapps(cordapps)
            }
            cd.setup { nodeName -> project.projectDir.toPath().resolve(getNodeByName(nodeName)!!.nodeDir.toPath()) }
        } else {
            nodes.forEach {
                it.rootDir(directory)
            }
        }
    }

    private fun bootstrapNetwork() {
        val networkBootstrapperClass = loadNetworkBootstrapperClass()
        val networkBootstrapper = networkBootstrapperClass.newInstance()
        val bootstrapMethod = networkBootstrapperClass.getMethod("bootstrap", Path::class.java).apply { isAccessible = true }
        // Call NetworkBootstrapper.bootstrap
        try {
            val rootDir = project.projectDir.toPath().resolve(directory).toAbsolutePath().normalize()
            bootstrapMethod.invoke(networkBootstrapper, rootDir)
        } catch (e: InvocationTargetException) {
            throw e.cause!!
        }
    }

    private fun CordformDefinition.getMatchingCordapps(): List<File> {
        val cordappJars = project.configuration("cordapp").files
        return cordappPackages.map { `package` ->
            val cordappsWithPackage = cordappJars.filter { it.containsPackage(`package`) }
            when (cordappsWithPackage.size) {
                0 -> throw IllegalArgumentException("There are no cordapp dependencies containing the package $`package`")
                1 -> cordappsWithPackage[0]
                else -> throw IllegalArgumentException("More than one cordapp dependency contains the package $`package`: $cordappsWithPackage")
            }
        }
    }

    private fun File.containsPackage(`package`: String): Boolean {
        JarInputStream(inputStream()).use {
            while (true) {
                val name = it.nextJarEntry?.name ?: break
                if (name.endsWith(".class") && name.replace('/', '.').startsWith(`package`)) {
                    return true
                }
            }
            return false
        }
    }
}
