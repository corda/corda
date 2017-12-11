package net.corda.cordform;

import java.nio.file.Path;

public interface CordformContext {
    Path baseDirectory(String nodeName);
}
