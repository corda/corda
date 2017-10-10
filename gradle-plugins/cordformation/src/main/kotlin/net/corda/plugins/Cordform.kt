package net.corda.plugins

import groovy.lang.Closure
import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import org.apache.tools.ant.filters.FixCrLfFilter
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Creates nodes based on the configuration of this task in the gradle configuration DSL.
 *
 * See documentation for examples.
 */
@Suppress("unused")
open class Cordform : DefaultTask() {
    /**
     * Optionally the name of a CordformDefinition subclass to which all configuration will be delegated.
     */
    @Suppress("MemberVisibilityCanPrivate")
    var definitionClass: String? = null
    private var directory = Paths.get("build", "nodes")
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
     * This task action will create and install the nodes based on the node configurations added.
     */
    @Suppress("unused")
    @TaskAction
    fun build() {
        project.logger.info("Running Cordform task")
        initializeConfiguration()
        installRunScript()
        nodes.forEach(Node::build)
        generateAndInstallNodeInfos()
    }

    private fun initializeConfiguration() {
        if (null != definitionClass) {
            val cd = loadCordformDefinition()
            cd.nodeConfigurers.forEach {
                it.accept(node {
                    rootDir(directory)
                })
            }
            cd.setup { nodeName -> project.projectDir.toPath().resolve(getNodeByName(nodeName)!!.nodeDir.toPath()) }
        } else {
            nodes.forEach {
                it.rootDir(directory)
            }
        }
    }

    private fun fullNodePath(node: Node): Path = project.projectDir.toPath().resolve(node.nodeDir.toPath())

    private fun generateAndInstallNodeInfos() {
        generateNodeInfos()
        installNodeInfos()
    }

    private fun generateNodeInfos() {
        project.logger.info("Generating node infos")
        val generateTimeoutSeconds = 60L
        val processes = nodes.map { node ->
            project.logger.info("Generating node info for ${fullNodePath(node)}")
            val logDir = File(fullNodePath(node).toFile(), "logs")
            logDir.mkdirs() // Directory may not exist at this point
            Pair(node, ProcessBuilder("java", "-jar", Node.nodeJarName, "--just-generate-node-info")
                    .directory(fullNodePath(node).toFile())
                    .redirectErrorStream(true)
                    // InheritIO causes hangs on windows due the gradle buffer also not being flushed.
                    // Must redirect to output or logger (node log is still written, this is just startup banner)
                    .redirectOutput(File(logDir, "generate-info-log.txt"))
                    .start())
        }
        try {
            processes.parallelStream().forEach { (node, process) ->
                if (!process.waitFor(generateTimeoutSeconds, TimeUnit.SECONDS)) {
                    throw GradleException("Node took longer $generateTimeoutSeconds seconds than too to generate node info - see node log at ${fullNodePath(node)}/logs")
                } else if (process.exitValue() != 0) {
                    throw GradleException("Node exited with ${process.exitValue()} when generating node infos - see node log at ${fullNodePath(node)}/logs")
                }
            }
        } finally {
            // This will be a no-op on success - abort remaining on failure
            processes.forEach {
                it.second.destroyForcibly()
            }
        }
    }

    private fun installNodeInfos() {
        project.logger.info("Installing node infos")
        for (source in nodes) {
            for (destination in nodes) {
                if (source.nodeDir != destination.nodeDir) {
                    project.copy {
                        it.apply {
                            from(fullNodePath(source).toString())
                            include("nodeInfo-*")
                            into(fullNodePath(destination).resolve(CordformNode.NODE_INFO_DIRECTORY).toString())
                        }
                    }
                }
            }
        }
    }
}
