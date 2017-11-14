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
    private val notaryMap: HashMap<String, Boolean> = hashMapOf()

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
        generateAndInstallNetworkParameters()
    }

    private fun initializeConfiguration() {
        if (definitionClass != null) {
            val cd = loadCordformDefinition()
            cd.nodeConfigurers.forEach {
                val node = node { }
                it.accept(node)
                node.rootDir(directory)
            }
            cd.setup { nodeName -> project.projectDir.toPath().resolve(getNodeByName(nodeName)!!.nodeDir.toPath()) }
        } else {
            nodes.forEach {
                it.rootDir(directory)
            }
        }
    }

    private fun generateAndInstallNetworkParameters() {
        project.logger.info("Generating network parameters")
        gatherNotaries()
        val networkParamsProcess = buildNetworkParamsProcess()
        try {
            validateParametersProcess(networkParamsProcess, nodes[0].logDirectory())
        } finally {
            networkParamsProcess.destroyForcibly()
        }
        project.logger.info("Installing network parameters")
        val sourcePath = nodes[0].fullPath().toString()
        for (destination in nodes.drop(1)) {
            project.copy {
                it.apply {
                    from(sourcePath)
                    include("network-parameters")
                    into(destination.fullPath().toString())
                }
            }
        }
    }

    private fun gatherNotaries() {
        val notaryNodes = nodes.filter { it.notary != null }
        notaryNodes.forEach {
            notaryMap[it.name] = it.notary.getOrDefault("validating", false) as Boolean
        }
    }

    private fun buildNetworkParamsProcess(): Process = buildProcess(nodes[0], generateParamsCommand(nodes[0]), "generate-params.log").second

    private fun validateParametersProcess(process: Process, logsPath: Path) {
        val generateTimeoutSeconds = 60L
        if (!process.waitFor(generateTimeoutSeconds, TimeUnit.SECONDS)) {
            throw GradleException("Network parameters generation process too more than $generateTimeoutSeconds seconds - see logs at $logsPath")
        }
        if (process.exitValue() != 0) {
            throw GradleException("Network parameters generation process exited with ${process.exitValue()} - see logs at $logsPath")
        }
        project.logger.info("Generated network parameters")
    }

    private fun generateParamsCommand(node: Node): List<String> = listOf(
            "java",
            "-cp",
            Node.cordaNodeJarName,
            "net.corda.node.internal.networkParametersGenerator.NetworkParametersGenerator",
            "--base-directory",
            node.fullPath().toString(),
            "--notaries",
            notaryMap.map { (key, value) -> key + ":" + value.toString() }.joinToString("#")
    )

    private fun generateAndInstallNodeInfos() {
        generateNodeInfos()
        installNodeInfos()
    }

    private fun generateNodeInfos() {
        project.logger.info("Generating node infos")
        val nodeProcesses = buildNodeProcesses()
        try {
            validateNodeProcessess(nodeProcesses)
        } finally {
            destroyNodeProcesses(nodeProcesses)
        }
    }

    private fun buildNodeProcesses(): Map<Node, Process> {
        val command = generateNodeInfoCommand()
        return nodes.map {
                    it.makeLogDirectory()
                    buildProcess(it, command, "generate-info.log") }.toMap()
    }

    private fun validateNodeProcessess(nodeProcesses: Map<Node, Process>) {
        nodeProcesses.forEach { (node, process) ->
            validateNodeProcess(node, process)
        }
    }

    private fun destroyNodeProcesses(nodeProcesses: Map<Node, Process>) {
        nodeProcesses.forEach { (_, process) ->
            process.destroyForcibly()
        }
    }

    private fun buildProcess(node: Node, command: List<String>, logFile: String): Pair<Node, Process> {
        val process = ProcessBuilder(command)
                .directory(node.fullPath().toFile())
                .redirectErrorStream(true)
                // InheritIO causes hangs on windows due the gradle buffer also not being flushed.
                // Must redirect to output or logger (node log is still written, this is just startup banner)
                .redirectOutput(node.logFile(logFile).toFile())
                .addEnvironment("CAPSULE_CACHE_DIR", Node.capsuleCacheDir)
                .start()
        return Pair(node, process)
    }

    private fun generateNodeInfoCommand(): List<String> = listOf(
            "java",
            "-Dcapsule.log=verbose",
            "-Dcapsule.dir=${Node.capsuleCacheDir}",
            "-jar",
            Node.nodeJarName,
            "--just-generate-node-info"
    )

    private fun validateNodeProcess(node: Node, process: Process) {
        val generateTimeoutSeconds = 60L
        if (!process.waitFor(generateTimeoutSeconds, TimeUnit.SECONDS)) {
            throw GradleException("Node took longer $generateTimeoutSeconds seconds than too to generate node info - see node log at ${node.fullPath()}/logs")
        }
        if (process.exitValue() != 0) {
            throw GradleException("Node exited with ${process.exitValue()} when generating node infos - see node log at ${node.fullPath()}/logs")
        }
        project.logger.info("Generated node info for ${node.fullPath()}")
    }

    private fun installNodeInfos() {
        project.logger.info("Installing node infos")
        for (source in nodes) {
            for (destination in nodes) {
                if (source.nodeDir != destination.nodeDir) {
                    project.copy {
                        it.apply {
                            from(source.fullPath().toString())
                            include("nodeInfo-*")
                            into(destination.fullPath().resolve(CordformNode.NODE_INFO_DIRECTORY).toString())
                        }
                    }
                }
            }
        }
    }
    private fun Node.logFile(name: String): Path = this.logDirectory().resolve(name)
    private fun ProcessBuilder.addEnvironment(key: String, value: String) = this.apply { environment().put(key, value) }
}
