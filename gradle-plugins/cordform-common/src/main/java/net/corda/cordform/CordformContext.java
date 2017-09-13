package net.corda.cordform;

import javax.security.auth.x500.X500Principal;
import java.nio.file.Path;

public interface CordformContext {
    Path baseDirectory(X500Principal nodeName);
}
