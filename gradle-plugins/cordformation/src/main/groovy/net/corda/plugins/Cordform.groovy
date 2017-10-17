package net.corda.plugins

import static org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import org.apache.tools.ant.filters.FixCrLfFilter
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.TaskAction
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Creates nodes based on the configuration of this task in the gradle configuration DSL.
 *
 * See documentation for examples.
 */
class Cordform extends DefaultTask {
    /**
     * Optionally the name of a CordformDefinition subclass to which all configuration will be delegated.
     */
    String definitionClass
    protected def directory = Paths.get("build", "nodes")
    private def nodes = new ArrayList<Node>()

    /**
     * Set the directory to install nodes into.
     *
     * @param directory The directory the nodes will be installed into.
     * @return
     */
    void directory(String directory) {
        this.directory = Paths.get(directory)
    }

    /**
     * Add a node configuration.
     *
     * @param configureClosure A node configuration that will be deployed.
     */
    void node(Closure configureClosure) {
        nodes << (Node) project.configure(new Node(project), configureClosure)
    }

    /**
     * Returns a node by name.
     *
     * @param name The name of the node as specified in the node configuration DSL.
     * @return A node instance.
     */
    private Node getNodeByName(String name) {
        for (Node node : nodes) {
            if (node.name == name) {
                return node
            }
        }

        return null
    }

    /**
     * Installs the run script into the nodes directory.
     */
    private void installRunScript() {
        project.copy {
            from Cordformation.getPluginFile(project, "net/corda/plugins/runnodes.jar")
            fileMode 0755
            into "${directory}/"
        }

        project.copy {
            from Cordformation.getPluginFile(project, "net/corda/plugins/runnodes")
            // Replaces end of line with lf to avoid issues with the bash interpreter and Windows style line endings.
            filter(FixCrLfFilter.class, eol: FixCrLfFilter.CrLf.newInstance("lf"))
            fileMode 0755
            into "${directory}/"
        }

        project.copy {
            from Cordformation.getPluginFile(project, "net/corda/plugins/runnodes.bat")
            into "${directory}/"
        }
    }

    /**
     * The definitionClass needn't be compiled until just before our build method, so we load it manually via sourceSets.main.runtimeClasspath.
     */
    private CordformDefinition loadCordformDefinition() {
        def plugin = project.convention.getPlugin(JavaPluginConvention.class)
        def classpath = plugin.sourceSets.getByName(MAIN_SOURCE_SET_NAME).runtimeClasspath
        URL[] urls = classpath.files.collect { it.toURI().toURL() }
        (CordformDefinition) new URLClassLoader(urls, CordformDefinition.classLoader).loadClass(definitionClass).newInstance()
    }

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    void build() {
        initializeConfiguration()
        installRunScript()
        nodes.each {
            it.build()
        }
        generateNodeInfos()
    }

    private initializeConfiguration() {
        if (null != definitionClass) {
            def cd = loadCordformDefinition()
            cd.nodeConfigurers.each { nc ->
                node { Node it ->
                    nc.accept it
                    it.rootDir directory
                }
            }
            cd.setup new CordformContext() {
                Path baseDirectory(String nodeName) {
                    project.projectDir.toPath().resolve(getNodeByName(nodeName).nodeDir.toPath())
                }
            }
        } else {
            nodes.each {
                it.rootDir directory
            }
        }
    }

    Path fullNodePath(Node node) {
        return project.projectDir.toPath().resolve(node.nodeDir.toPath())
    }

    private generateNodeInfos() {
        nodes.each { Node node ->
            def process = new ProcessBuilder("java", "-jar", Node.NODEJAR_NAME, "--just-generate-node-info")
                    .directory(fullNodePath(node).toFile())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
        }
        for (source in nodes) {
            for (destination in nodes) {
                if (source.nodeDir != destination.nodeDir) {
                    project.copy {
                        from fullNodePath(source).toString()
                        include 'nodeInfo-*'
                        into fullNodePath(destination).resolve(Node.NODE_INFO_DIRECTORY).toString()
                    }
                }
            }
        }
    }
}
