package sandbox.org.bouncycastle.asn1.x509;

import sandbox.org.bouncycastle.asn1.ASN1Object;

/**
 * This is a dummy class that implements just enough of {@link org.bouncycastle.asn1.x509.AlgorithmIdentifier}
 * to allow us to compile {@link sandbox.net.corda.core.crypto.Crypto}.
 */
@SuppressWarnings("unused")
public class AlgorithmIdentifier extends ASN1Object {
    public static AlgorithmIdentifier getInstance(Object obj) {
        throw new UnsupportedOperationException("Dummy class - not implemented");
    }
}
