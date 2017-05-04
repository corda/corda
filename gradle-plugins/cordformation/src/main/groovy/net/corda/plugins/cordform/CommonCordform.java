package net.corda.plugins.cordform;

import org.bouncycastle.asn1.x500.X500Name;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;

public abstract class CommonCordform {
    public final Path driverDirectory;
    public final ArrayList<Consumer<? super CommonNode>> nodeConfigurers = new ArrayList<>();
    public final X500Name networkMapNodeName;

    public CommonCordform(Path driverDirectory, X500Name networkMapNodeName) {
        this.driverDirectory = driverDirectory;
        this.networkMapNodeName = networkMapNodeName;
    }

    public void addNode(Consumer<? super CommonNode> configurer) {
        nodeConfigurers.add(configurer);
    }

    public abstract void setUp(CordformContext context);
}
