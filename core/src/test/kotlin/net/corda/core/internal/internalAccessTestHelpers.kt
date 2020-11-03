package net.corda.core.internal

import net.corda.core.contracts.*
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.internal.AttachmentsClassLoaderCache
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.WireTransaction

/**
 * A set of functions in core:test that allows testing of core internal classes in the core-tests project.
 */

fun WireTransaction.accessGroupHashes() = this.groupHashes

fun WireTransaction.accessGroupMerkleRoots() = this.groupsMerkleRoots
fun WireTransaction.accessAvailableComponentHashes() = this.availableComponentHashes

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
        digestService: DigestService = DigestService.default
): LedgerTransaction = LedgerTransaction.create(inputs, outputs, commands, attachments, id, notary, timeWindow, privacySalt, networkParameters, references, componentGroups, serializedInputs, serializedReferences, isAttachmentTrusted, attachmentsClassLoaderCache, digestService)

fun createContractCreationError(txId: SecureHash, contractClass: String, cause: Throwable) = TransactionVerificationException.ContractCreationError(txId, contractClass, cause)
fun createContractRejection(txId: SecureHash, contract: Contract, cause: Throwable) = TransactionVerificationException.ContractRejection(txId, contract, cause)
