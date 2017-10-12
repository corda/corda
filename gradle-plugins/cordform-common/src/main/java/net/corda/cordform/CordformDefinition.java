package net.corda.cordform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;

public abstract class CordformDefinition {
    public final Path driverDirectory;
    public final ArrayList<Consumer<? super CordformNode>> nodeConfigurers = new ArrayList<>();

    public CordformDefinition(Path driverDirectory) {
        this.driverDirectory = driverDirectory;
    }

    public void addNode(Consumer<? super CordformNode> configurer) {
        nodeConfigurers.add(configurer);
    }

    /**
     * Make arbitrary changes to the node directories before they are started.
     * @param context Lookup of node directory by node name.
     */
    public abstract void setup(CordformContext context);
}
