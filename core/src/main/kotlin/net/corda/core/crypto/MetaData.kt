package net.corda.core.crypto

import net.corda.core.serialization.serialize
import java.security.PublicKey
import java.time.Instant

/**
 * A [MetaData] object adds extra information to the signed message.
 * Its subclasses are used to setup a unified digital signature model enabling full, partial, fully or partially blind and metaData attached signatures (such as an attached timestamp).
 * If the MetaData subclass object contains the clearData, then the signature protocol works as follows: s = sign(MetaData.hashBytes)
 * Currently, the DLT's protocol supports only [MetaData.Full] and [MetaData.WithClearData] objects, as both contain clearData(usually the Merkle root).
 * When signatureType is set to FULL_MERKLE, then visibleInputs and signedInputs can be ignored.
 * Note: We could omit signatureType as it can always be defined by combining visibleInputs and signedInputs,
 * but it helps to speed up the process when FULL_MERKLE is used, and thus we can bypass the extra check on boolean arrays.
 *
 * @param schemeCodeName a signature scheme's code name (e.g. ECDSA_SECP256K1_SHA256).
 * @param versionID DLT's version.
 * @param signatureType type of the signature, see [SignatureType] (e.g. FULL_MERKLE, PARTIAL, BLIND, PARTIAL_AND_BLIND).
 * @param timestamp the signature's timestamp as provided by the signer.
 * @param visibleInputs for partially/fully blind signatures. We use Merkle tree boolean index flags (from left to right)
 * indicating what parts of the transaction were visible when the signature was calculated.
 * @param signedInputs for partial signatures. We use Merkle tree boolean index flags (from left to right)
 * indicating what parts of the Merkle tree are actually signed.
 */
abstract class MetaData(
        val schemeCodeName: String,
        val versionID: String,
        val signatureType: SignatureType = SignatureType.FULL_MERKLE,
        val timestamp: Instant?,
        val visibleInputs: BooleanArray?,
        val signedInputs: BooleanArray?)
{
    /**
     * This subclass of [MetaData] contains the clearData, but not the public key of the signer.
     * This is currently not utilized in DLT.
     * @param clearData data to be signed (usually the Merkle root).
     * TODO: change [BooleanArray] to [BitSet] for memory efficiency.
     */
    open class WithClearData(
            schemeCodeName: String,
            versionID: String,
            signatureType: SignatureType,
            timestamp: Instant?,
            visibleInputs: BooleanArray?,
            signedInputs: BooleanArray?,
            val clearData: ByteArray)
        : MetaData(schemeCodeName, versionID, signatureType, timestamp, visibleInputs, signedInputs)

    /**
     * This subclass of [MetaData] contains the public key of the signer, but not the clearData.
     * This class is currently not supported from DLT and should be avoided, please use [MetaData.Full] instead.
     * @param publicKey the signer's public key.
     */
    @Deprecated("This class is currently not utilized in DLT and should be avoided.", ReplaceWith("MetaData.Full"), DeprecationLevel.WARNING)
    open class WithKey(
            schemeCodeName: String,
            versionID: String,
            signatureType: SignatureType,
            timestamp: Instant?,
            visibleInputs: BooleanArray?,
            signedInputs: BooleanArray?,
            val publicKey: PublicKey)
        : MetaData(schemeCodeName, versionID, signatureType, timestamp, visibleInputs, signedInputs)

    /**
     * This is the full version of [MetaData] that contains both the signer's public key and clearData.
     * [MetaData.Full] is the recommended [MetaData] subclass for exchanging signatures.
     * @param clearData data to be signed (usually the Merkle root).
     * @param publicKey the signer's public key.
     */
    open class Full(
            schemeCodeName: String,
            versionID: String,
            signatureType: SignatureType,
            timestamp: Instant?,
            visibleInputs: BooleanArray?,
            signedInputs: BooleanArray?,
            clearData: ByteArray,
            val publicKey: PublicKey)
        : WithClearData(schemeCodeName, versionID, signatureType, timestamp, visibleInputs, signedInputs, clearData)

    fun hashBytes() = this.serialize().hash.bytes
}
