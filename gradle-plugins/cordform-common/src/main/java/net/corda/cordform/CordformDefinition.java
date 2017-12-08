package net.corda.cordform;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public abstract class CordformDefinition {
    private Path nodesDirectory = Paths.get("build", "nodes");
    private final List<Consumer<CordformNode>> nodeConfigurers = new ArrayList<>();
    private final Set<String> cordappPackages = new HashSet<>();

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

    public Set<String> getCordappPackages() {
        return cordappPackages;
    }

    /**
     * Make arbitrary changes to the node directories before they are started.
     * @param context Lookup of node directory by node name.
     */
    public abstract void setup(@Nonnull CordformContext context);
}
