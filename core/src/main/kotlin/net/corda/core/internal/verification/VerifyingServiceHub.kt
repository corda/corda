package net.corda.core.internal.verification

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.AttachmentResolutionException
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.getRequiredTransaction
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.WireTransaction

@Suppress("TooManyFunctions", "ThrowsCount")
interface VerifyingServiceHub : ServiceHub, NodeVerificationSupport {
    override fun loadContractAttachment(stateRef: StateRef): Attachment {
        // We may need to recursively chase transactions if there are notary changes.
        return loadContractAttachment(stateRef, null)
    }

    private fun loadContractAttachment(stateRef: StateRef, forContractClassName: String?): Attachment {
        val stx = getRequiredTransaction(stateRef.txhash)
        val ctx = stx.coreTransaction
        return when (ctx) {
            is WireTransaction -> {
                val contractClassName = forContractClassName ?: ctx.outRef<ContractState>(stateRef.index).state.contract
                ctx.attachments
                        .asSequence()
                        .mapNotNull { id -> loadAttachmentContainingContract(id, contractClassName) }
                        .firstOrNull() ?: throw AttachmentResolutionException(stateRef.txhash)
            }
            is ContractUpgradeWireTransaction -> {
                attachments.openAttachment(ctx.upgradedContractAttachmentId) ?: throw AttachmentResolutionException(stateRef.txhash)
            }
            is NotaryChangeWireTransaction -> {
                val transactionState = getSerializedState(stateRef).deserialize()
                val input = ctx.inputs.firstOrNull() ?: throw AttachmentResolutionException(stateRef.txhash)
                loadContractAttachment(input, transactionState.contract)
            }
            else -> throw UnsupportedOperationException("Attempting to resolve attachment for index ${stateRef.index} of a " +
                    "${ctx.javaClass} transaction. This is not supported.")
        }
    }

    private fun loadAttachmentContainingContract(id: SecureHash, contractClassName: String): Attachment? {
        return attachments.openAttachment(id)?.takeIf { it is ContractAttachment && contractClassName in it.allContracts }
    }

    override fun loadState(stateRef: StateRef): TransactionState<*> = getSerializedState(stateRef).deserialize()

    override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> = loadStatesInternal(stateRefs, LinkedHashSet())

    fun <T : ContractState, C : MutableCollection<StateAndRef<T>>> loadStatesInternal(input: Iterable<StateRef>, output: C): C {
        return input.mapTo(output, ::toStateAndRef)
    }
}

fun ServicesForResolution.toVerifyingServiceHub(): VerifyingServiceHub {
    if (this is VerifyingServiceHub) {
        return this
    }
    // All ServicesForResolution instances should also implement VerifyingServiceHub, which is something we can enforce with the
    // @DoNotImplement annotation. The only exception however is MockServices, which does not since it's public API and VerifyingServiceHub
    // is internal. Instead, MockServices has a private VerifyingServiceHub "view" which we get at via reflection.
    var clazz: Class<*> = javaClass
    while (true) {
        if (clazz.name == "net.corda.testing.node.MockServices") {
            return clazz.getDeclaredMethod("getVerifyingView").apply { isAccessible = true }.invoke(this) as VerifyingServiceHub
        }
        clazz = clazz.superclass ?: throw ClassCastException("${javaClass.name} is not a VerifyingServiceHub")
    }
}
