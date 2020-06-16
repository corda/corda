package sandbox.org.bouncycastle.asn1;

import sandbox.java.lang.Object;

import java.io.IOException;

/**
 * This is a dummy class that implements just enough of {@link org.bouncycastle.asn1.ASN1Object}
 * to allow us to compile {@link sandbox.net.corda.core.crypto.Crypto}.
 */
@SuppressWarnings("RedundantThrows")
public class ASN1Object extends Object implements ASN1Encodable {
    public byte[] getEncoded() throws IOException {
        throw new UnsupportedOperationException("Dummy class - not implemented");
    }
}
