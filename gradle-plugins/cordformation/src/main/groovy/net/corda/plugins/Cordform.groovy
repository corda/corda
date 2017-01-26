package net.corda.plugins

import org.apache.tools.ant.filters.FixCrLfFilter
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.nio.file.Path
import java.nio.file.Paths
/**
 * Creates nodes based on the configuration of this task in the gradle configuration DSL.
 *
 * See documentation for examples.
 */
class Cordform extends DefaultTask {
    protected Path directory = Paths.get("./build/nodes")
    protected List<Node> nodes = new ArrayList<Node>()
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
        nodes << project.configure(new Node(project), configureClosure)
    }

    /**
     * Returns a node by name.
     *
     * @param name The name of the node as specified in the node configuration DSL.
     * @return A node instance.
     */
    protected Node getNodeByName(String name) {
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
    protected void installRunScript() {
        project.copy {
            from Cordformation.getPluginFile(project, "net/corda/plugins/runnodes")
            from Cordformation.getPluginFile(project, "net/corda/plugins/runnodes.bat")
            filter { String line -> line.replace("JAR_NAME", Node.JAR_NAME) }
            // Replaces end of line with lf to avoid issues with the bash interpreter and Windows style line endings.
            filter(FixCrLfFilter.class, eol: FixCrLfFilter.CrLf.newInstance("lf"))
            fileMode 0755
            into "${directory}/"
        }
    }

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    void build() {
        installRunScript()
        Node networkMapNode = getNodeByName(networkMapNodeName)
        nodes.each {
            if(it != networkMapNode) {
                it.networkMapAddress(networkMapNode.getArtemisAddress(), networkMapNodeName)
            }
            it.build(directory.toFile())
        }
    }
}

