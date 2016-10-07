package com.r3corda.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.nio.file.Path
import java.nio.file.Paths

class Cordform extends DefaultTask {
    protected Path directory = Paths.get("./build/nodes")
    protected List<Node> nodes = new ArrayList<Node>()
    protected String networkMapNodeName

    public String directory(String directory) {
        this.directory = Paths.get(directory)
    }

    public String networkMap(String nodeName) {
        networkMapNodeName = nodeName
    }

    public void node(Closure configureClosure) {
        nodes << project.configure(new Node(project), configureClosure)
    }

    protected Node getNodeByName(String name) {
        for(Node node : nodes) {
            if(node.name.equals(networkMapNodeName)) {
                return node
            }
        }

        return null
    }

    protected void installRunScript() {
        project.copy {
            from Cordformation.getPluginFile(project, "com/r3corda/plugins/runnodes")
            filter { String line -> line.replace("JAR_NAME", Node.JAR_NAME) }
            filter(org.apache.tools.ant.filters.FixCrLfFilter.class, eol: org.apache.tools.ant.filters.FixCrLfFilter.CrLf.newInstance("lf"))
            into "${directory}/"
        }
    }

    @TaskAction
    def build() {
        installRunScript()
        Node networkMapNode = getNodeByName(networkMapNodeName)
        nodes.each {
            if(it != networkMapNode) {
                it.networkMapAddress(networkMapNode.getArtemisAddress())
            }
            it.build(directory.toFile())
        }
    }
}

