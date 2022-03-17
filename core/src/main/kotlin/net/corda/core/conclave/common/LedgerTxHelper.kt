package net.corda.core.conclave.common

import net.corda.core.conclave.common.dto.ConclaveLedgerTxModel
import net.corda.core.conclave.common.dto.ConclaveNetworkParams
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.SerializedStateAndRef
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import java.time.Duration

class LedgerTxHelper {
    companion object {
        @JvmStatic
        fun toLedgerTxInternal(conclaveLedgerTxModel: ConclaveLedgerTxModel, dependencies: Set<SignedTransaction>): LedgerTransaction {
            val wireTransaction = conclaveLedgerTxModel.signedTransaction.tx
            val dependencyMap = dependencies.associateBy { it.tx.id }

            val serializedResolvedInputs = conclaveLedgerTxModel.inputStates.map {
                resolveStateRefBinaryComponent(it.ref, dependencyMap) ?: throw TransactionResolutionException(it.ref.txhash)
            }

            val serializedResolvedReferences = conclaveLedgerTxModel.references.map {
                resolveStateRefBinaryComponent(it.ref, dependencyMap) ?: throw TransactionResolutionException(it.ref.txhash)
            }

            return LedgerTransaction.createForConclaveVerify(
                    inputs = conclaveLedgerTxModel.inputStates.toList(),
                    outputs = wireTransaction.outputs,
                    commands = wireTransaction.commands.map {
                        CommandWithParties(it.signers, emptyList(), it.value)
                    },
                    attachments = conclaveLedgerTxModel.attachments.toList(),
                    id = wireTransaction.id,
                    notary = wireTransaction.notary,
                    timeWindow = wireTransaction.timeWindow,
                    privacySalt = wireTransaction.privacySalt,
                    networkParameters = conclaveLedgerTxModel.networkParameters.toNetworkParams(),
                    references = conclaveLedgerTxModel.references.toList(),
                    componentGroups = wireTransaction.componentGroups,
                    serializedInputs = serializedResolvedInputs,
                    serializedReferences = serializedResolvedReferences,
                    digestService = wireTransaction.digestService
            )
        }

        @JvmStatic
        fun resolveStateRefBinaryComponent(stateRef: StateRef, dependencyMap: Map<SecureHash, SignedTransaction>)
            : SerializedStateAndRef? {

            val wireTransaction = dependencyMap[stateRef.txhash]?.tx
                    ?: throw TransactionResolutionException(stateRef.txhash)

            val resolvedState = wireTransaction.componentGroups
                    .firstOrNull { it.groupIndex == ComponentGroupEnum.OUTPUTS_GROUP.ordinal }
                    ?.components
                    ?.get(stateRef.index) as SerializedBytes<TransactionState<ContractState>>?

            return resolvedState?.let { SerializedStateAndRef(it, stateRef) }
        }
    }
}

private fun ConclaveNetworkParams.toNetworkParams(): NetworkParameters? {
    return NetworkParameters(
            this.minimumPlatformVersion, this.notaries, this.maxMessageSize, this.maxTransactionSize, this.modifiedTime, this.epoch, emptyMap(), Duration.ofDays(1000), emptyMap()
    )
}
