package sandbox.net.corda.core.crypto;

import org.jetbrains.annotations.NotNull;
import sandbox.java.lang.Object;
import sandbox.java.lang.String;
import sandbox.java.security.PublicKey;

/**
 * We shall delegate as much cryptography as possible to Corda's
 * underlying {@link net.corda.core.crypto.Crypto} object and its
 * {@link java.security.Provider} classes. This wrapper only needs
 * to implement {@link #equals} and {@link #hashCode}.
 */
final class DJVMPublicKey extends Object implements PublicKey {
    private final java.security.PublicKey underlying;
    private final String algorithm;
    private final String format;
    private final int hashCode;

    DJVMPublicKey(@NotNull java.security.PublicKey underlying) {
        this.underlying = underlying;
        this.algorithm = String.toDJVM(underlying.getAlgorithm());
        this.format = String.toDJVM(underlying.getFormat());
        this.hashCode = underlying.hashCode();
    }

    java.security.PublicKey getUnderlying() {
        return underlying;
    }

    @Override
    public boolean equals(java.lang.Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof DJVMPublicKey)) {
            return false;
        } else {
            return underlying.equals(((DJVMPublicKey) other).underlying);
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String getAlgorithm() {
        return algorithm;
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Override
    public byte[] getEncoded() {
        return underlying.getEncoded();
    }
}
