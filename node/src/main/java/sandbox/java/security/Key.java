package sandbox.java.security;

import sandbox.java.lang.String;

/**
 * This is a dummy class that implements just enough of {@link java.security.Key}
 * to allow us to compile {@link sandbox.net.corda.core.crypto.Crypto}.
 */
public interface Key {
    String getAlgorithm();
    String getFormat();
    byte[] getEncoded();
}
