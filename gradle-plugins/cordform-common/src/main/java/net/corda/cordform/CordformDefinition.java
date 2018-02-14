package net.corda.cordform;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class CordformDefinition {
    private Path nodesDirectory = Paths.get("build", "nodes");
    private final List<Consumer<CordformNode>> nodeConfigurers = new ArrayList<>();
    /**
     * A list of Cordapp maven coordinates and project name
     *
     * If maven coordinates are set project name is ignored
     */
    private final List<CordappDependency> cordappDeps = new ArrayList<>();

    public Path getNodesDirectory() {
        return nodesDirectory;
    }

    public void setNodesDirectory(Path nodesDirectory) {
        this.nodesDirectory = nodesDirectory;
    }

    public List<Consumer<CordformNode>> getNodeConfigurers() {
        return nodeConfigurers;
    }

    public void addNode(Consumer<CordformNode> configurer) {
        nodeConfigurers.add(configurer);
    }

    /**
     * Cordapp maven coordinates or project names (ie; net.corda:finance:0.1 or ":finance") to scan for when resolving cordapp JARs
     */
    public List<CordappDependency> getCordappDependencies() {
        return cordappDeps;
    }

    /**
     * Make arbitrary changes to the node directories before they are started.
     * @param context Lookup of node directory by node name.
     */
    public abstract void setup(@Nonnull CordformContext context);
}
