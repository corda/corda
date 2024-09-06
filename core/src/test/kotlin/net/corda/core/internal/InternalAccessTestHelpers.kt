package net.corda.core.internal

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.RotatedKeys
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.verification.AbstractVerifier
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.internal.AttachmentsClassLoaderCache
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.WireTransaction
import java.util.function.Supplier

/**
 * A set of functions in core:test that allows testing of core internal classes in the core-tests project.
 */

fun WireTransaction.accessGroupHashes() = this.groupHashes

fun WireTransaction.accessGroupMerkleRoots() = this.groupsMerkleRoots
fun WireTransaction.accessAvailableComponentHashes() = this.availableComponentHashes
fun WireTransaction.accessAvailableComponentNonces() = this.availableComponentNonces

@Suppress("LongParameterList")
fun createLedgerTransaction(
        inputs: List<StateAndRef<ContractState>>,
        outputs: List<TransactionState<ContractState>>,
        commands: List<CommandWithParties<CommandData>>,
        attachments: List<Attachment>,
        id: SecureHash,
        notary: Party?,
        timeWindow: TimeWindow?,
        privacySalt: PrivacySalt,
        networkParameters: NetworkParameters,
        references: List<StateAndRef<ContractState>>,
        componentGroups: List<ComponentGroup>? = null,
        serializedInputs: List<SerializedStateAndRef>? = null,
        serializedReferences: List<SerializedStateAndRef>? = null,
        isAttachmentTrusted: (Attachment) -> Boolean,
        attachmentsClassLoaderCache: AttachmentsClassLoaderCache,
        digestService: DigestService = DigestService.default,
        rotatedKeys: RotatedKeys = RotatedKeys()
): LedgerTransaction {
    return LedgerTransaction.create(
            inputs,
            outputs,
            commands,
            attachments,
            id,
            notary,
            timeWindow,
            privacySalt,
            networkParameters,
            references,
            componentGroups,
            serializedInputs,
            serializedReferences,
            isAttachmentTrusted,
            ::PassthroughVerifier,
            attachmentsClassLoaderCache,
            digestService,
            rotatedKeys
    )
}

fun createContractCreationError(txId: SecureHash, contractClass: String, cause: Throwable) = TransactionVerificationException.ContractCreationError(txId, contractClass, cause)
fun createContractRejection(txId: SecureHash, contract: Contract, cause: Throwable) = TransactionVerificationException.ContractRejection(txId, contract, cause)

/**
 * Verify the [LedgerTransaction] we already have.
 *
 * Note, this is not secure!
 */
private class PassthroughVerifier(ltx: LedgerTransaction, context: SerializationContext,rotatedKeys: RotatedKeys) : AbstractVerifier(ltx, context.deserializationClassLoader, rotatedKeys) {
    override val transaction: Supplier<LedgerTransaction>
        get() = Supplier { ltx }
}
