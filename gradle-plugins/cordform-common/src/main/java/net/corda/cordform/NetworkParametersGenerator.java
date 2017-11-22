package net.corda.cordform;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface NetworkParametersGenerator {
    /**
     * Run generation of network parameters for [Cordformation]. Nodes need to have already all [NodeInfo]s files in their
     * base directories, these files will be used to extract notary identities.
     *
     * @param notaryMap - map where key is notary name, value indicates if a notary is validating or not
     * @param nodesDirs - nodes directories that will be used for network parameters generation. Network parameters
     *                  file will be dropped into each directory on this list.
     */
    void run(Map<String, Boolean> notaryMap, List<Path> nodesDirs);
}