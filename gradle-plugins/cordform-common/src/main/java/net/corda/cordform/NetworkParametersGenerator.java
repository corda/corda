package net.corda.cordform;

import java.nio.file.Path;
import java.util.List;

public interface NetworkParametersGenerator {
    /**
     * Run generation of network parameters for [Cordformation]. Nodes need to have already their own [NodeInfo] files in their
     * base directories, these files will be used to extract notary identities.
     *
     * @param nodesDirs - nodes directories that will be used for network parameters generation. Network parameters
     *                  file will be dropped into each directory on this list.
     */
    void run(List<Path> nodesDirs);
}