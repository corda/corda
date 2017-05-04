package net.corda.plugins

import static org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME

import net.corda.plugins.cordform.CordformContext
import net.corda.plugins.cordform.CommonCordform
import org.apache.tools.ant.filters.FixCrLfFilter
import org.bouncycastle.asn1.x500.X500Name
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
    String common
    protected def directory = Paths.get("build", "nodes")
    private def nodes = new ArrayList<Node>()
    protected String networkMapNodeName

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
     * Set the network map node.
     *
     * @warning Ensure the node name is one of the configured nodes.
     * @param nodeName The name of the node that will host the network map.
     */
    void networkMap(String nodeName) {
        networkMapNodeName = nodeName
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
        for(Node node : nodes) {
            if(node.name == name) {
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

    private CommonCordform commonCordform() {
        def plugin = project.convention.getPlugin(JavaPluginConvention.class)
        def classpath = plugin.sourceSets.getByName(MAIN_SOURCE_SET_NAME).runtimeClasspath
        URL[] urls = classpath.files.collect { it.toURI().toURL() }
        (CommonCordform) new URLClassLoader(urls, CommonCordform.classLoader).loadClass(common).newInstance()
    }

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    void build() {
        String networkMapNodeName
        if (null != common) {
            def cc = commonCordform()
            networkMapNodeName = cc.networkMapNodeName.toString()
            cc.nodeConfigurers.each { nc ->
                node { Node it ->
                    nc.accept it
                    it.rootDir directory
                }
            }
            cc.setUp new CordformContext() {
                Path baseDirectory(X500Name nodeName) {
                    project.projectDir.toPath().resolve(getNodeByName(nodeName.toString()).nodeDir.toPath())
                }
            }
        }
        else {
            networkMapNodeName = this.networkMapNodeName
            nodes.each {
                it.rootDir directory
            }
        }
        installRunScript()
        def networkMapNode = getNodeByName(networkMapNodeName)
        nodes.each {
            if(it != networkMapNode) {
                it.networkMapAddress(networkMapNode.getP2PAddress(), networkMapNodeName)
            }
            it.build()
        }
    }
}
