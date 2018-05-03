package net.corda.core.crypto.internal

import net.corda.core.crypto.*
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.math.GroupElement
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec
import org.apache.commons.lang.SystemUtils
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import java.security.*

/**
 *  Required for multi-transaction signing at once.
 *  Get the hash value that is actually signed.
 *  The txId is returned when [partialMerkleTree] is null,
 *  else the root of the tree is computed and returned.
 *  Note that the hash of the txId should be a leaf in the tree, not the txId itself.
 */
internal fun originalSignedHash(txId: SecureHash, partialMerkleTree: PartialMerkleTree?): SecureHash {
    return if (partialMerkleTree != null) {
        val usedHashes = mutableListOf<SecureHash>()
        val root = PartialMerkleTree.rootAndUsedHashes(partialMerkleTree.root, usedHashes)
        require(txId.sha256() in usedHashes) { "Transaction with id:$txId is not a leaf in the provided partial Merkle tree" }
        root
    } else {
        txId
    }
}

/**
 * Return true if EdDSA publicKey is point at infinity.
 * For EdDSA a custom function is required as it is not supported by the I2P implementation.
 */
internal fun isEdDSAPointAtInfinity(publicKey: EdDSAPublicKey): Boolean {
    return publicKey.a.toP3() == (Crypto.EDDSA_ED25519_SHA512.algSpec as EdDSANamedCurveSpec).curve.getZero(GroupElement.Representation.P3)
}

/** Check if a [PrivateKey] satisfies algorithm specs. */
internal fun validatePrivateKey(signatureScheme: SignatureScheme, key: PrivateKey): Boolean {
    return when (key) {
        is BCECPrivateKey -> key.parameters == signatureScheme.algSpec
        is EdDSAPrivateKey -> key.params == signatureScheme.algSpec
        is BCRSAPrivateKey, is BCSphincs256PrivateKey -> true // TODO: Check if non-ECC keys satisfy params (i.e. approved/valid RSA modulus size).
        else -> throw IllegalArgumentException("Unsupported key type: ${key::class}")
    }
}

/**
 * Check if a public key satisfies algorithm specs.
 * For ECC algorithms key should lie on the curve and not being point-at-infinity.
 */
internal fun validatePublicKey(signatureScheme: SignatureScheme, key: PublicKey): Boolean {
    return when (key) {
        is BCECPublicKey, is EdDSAPublicKey -> Crypto.publicKeyOnCurve(signatureScheme, key)
        is BCRSAPublicKey, is BCSphincs256PublicKey -> true // TODO: Check if non-ECC keys satisfy params (i.e. approved/valid RSA modulus size).
        else -> throw IllegalArgumentException("Unsupported key type: ${key::class}")
    }
}

/** Find [SignatureScheme] by schemeNumberID. */
internal fun findSignatureScheme(schemeNumberID: Int): SignatureScheme {
    return signatureSchemeNumberIDMap[schemeNumberID]
            ?: throw IllegalArgumentException("Unsupported key/algorithm for schemeCodeName: $schemeNumberID")
}

/**
 * Normalise an algorithm identifier by converting [DERNull] parameters into a Kotlin null value.
 */
internal fun normaliseAlgorithmIdentifier(id: AlgorithmIdentifier): AlgorithmIdentifier {
    return if (id.parameters is DERNull) {
        AlgorithmIdentifier(id.algorithm, null)
    } else {
        id
    }
}

/** Required from Crypto.doVerify and Crypto.isValid functions. */
internal fun schemeAndSignableData(txId: SecureHash, transactionSignature: TransactionSignature) : Pair<SignatureScheme, SignableData> {
    // TODO: consider accepting only metadata advertised scheme. At the moment we are proactively checking
    //      if key and metadata schemes match.
    val keyScheme = Crypto.findSignatureScheme(transactionSignature.by)
    val metadataScheme = signatureSchemeNumberIDMap[transactionSignature.signatureMetadata.schemeNumberID]
    require(metadataScheme != null) { "Signature scheme with numberID: $metadataScheme is not supported" }
    // Bypassing the following requirement when metadataScheme == Crypto.COMPOSITE_KEY due to backwards compatibility purposes.
    // TODO: consider removing the COMPOSITE_KEY check.
    require(keyScheme == metadataScheme || metadataScheme == Crypto.COMPOSITE_KEY) { "Signature scheme of metadata with numberID: $metadataScheme does not correspond to the public key scheme numberID: $keyScheme" }
    val signableData = SignableData(originalSignedHash(txId, transactionSignature.partialMerkleTree), transactionSignature.signatureMetadata)
    return Pair(keyScheme, signableData)
}

/** Pick [SecureRandom] implementation based on OS. */
internal val platformSecureRandomFactory: () -> SecureRandom = when {
    SystemUtils.IS_OS_LINUX -> {
        { SecureRandom.getInstance("NativePRNGNonBlocking") }
    }
    else -> SecureRandom::getInstanceStrong
}
