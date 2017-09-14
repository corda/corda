package net.corda.cordform;

import org.bouncycastle.asn1.x500.X500Name;
import java.nio.file.Path;

public interface CordformContext {
    Path baseDirectory(String nodeName);
}
