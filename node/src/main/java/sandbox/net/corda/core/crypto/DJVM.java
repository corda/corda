package sandbox.net.corda.core.crypto;

import org.jetbrains.annotations.NotNull;
import sandbox.java.lang.Integer;
import sandbox.java.lang.String;
import sandbox.java.util.ArrayList;
import sandbox.java.util.List;
import sandbox.org.bouncycastle.asn1.x509.AlgorithmIdentifier;

import java.io.IOException;

/**
 * Helper class for {@link sandbox.net.corda.core.crypto.Crypto}.
 * Deliberately package-private.
 */
final class DJVM {
    private DJVM() {}

    @NotNull
    static SignatureScheme toDJVM(@NotNull net.corda.core.crypto.SignatureScheme scheme) {
        // The AlgorithmParameterSpec is deliberately left as null
        // because it is computationally expensive to generate these
        // objects inside the sandbox every time.
        return new SignatureScheme(
            scheme.getSchemeNumberID(),
            String.toDJVM(scheme.getSchemeCodeName()),
            toDJVM(scheme.getSignatureOID()),
            toDJVM(scheme.getAlternativeOIDs()),
            String.toDJVM(scheme.getProviderName()),
            String.toDJVM(scheme.getAlgorithmName()),
            String.toDJVM(scheme.getSignatureName()),
            null,
            Integer.toDJVM(scheme.getKeySize()),
            String.toDJVM(scheme.getDesc())
        );
    }

    static org.bouncycastle.asn1.x509.AlgorithmIdentifier fromDJVM(@NotNull AlgorithmIdentifier oid) {
        try {
            return org.bouncycastle.asn1.x509.AlgorithmIdentifier.getInstance(oid.getEncoded());
        } catch (IOException e) {
            throw sandbox.java.lang.DJVM.toRuleViolationError(e);
        }
    }

    static AlgorithmIdentifier toDJVM(@NotNull org.bouncycastle.asn1.x509.AlgorithmIdentifier oid) {
        try {
            return AlgorithmIdentifier.getInstance(oid.getEncoded());
        } catch (IOException e) {
            throw sandbox.java.lang.DJVM.toRuleViolationError(e);
        }
    }

    @NotNull
    static List<AlgorithmIdentifier> toDJVM(@NotNull java.util.List<org.bouncycastle.asn1.x509.AlgorithmIdentifier> list) {
        ArrayList<AlgorithmIdentifier> djvmList = new ArrayList<>(list.size());
        for (org.bouncycastle.asn1.x509.AlgorithmIdentifier oid : list) {
            djvmList.add(toDJVM(oid));
        }
        return djvmList;
    }
}
