package net.corda.cordform;

import javax.security.auth.x500.X500Principal;
import java.nio.file.Path;

public interface CordformContext {
    /**
     * Resolve the base directory of a node.
     *
     * @param nodeName distinguished name of the node.
     */
    Path baseDirectory(String nodeName);
}
