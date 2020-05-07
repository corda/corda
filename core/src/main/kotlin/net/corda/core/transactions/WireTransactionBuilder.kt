package net.corda.core.transactions

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.CordaSerializable
import java.util.*

/**
 * A simple, serializable class that contains the contents of a transaction builder so that it may be sent over the wire,
 * reconstructed, modified and subsequently returned as a [SignedTransaction] by a given counter party.
 */
@CordaSerializable
data class WireTransactionBuilder(
        private val notary: Party?,
        private val lockId: UUID,
        private val inputs: List<StateRef>,
        private val attachments: List<AttachmentId>,
        private val outputs: List<TransactionState<ContractState>>,
        private val commands: List<Command<*>>,
        private val window: TimeWindow?,
        private val privacySalt: PrivacySalt,
        private val references: List<StateRef>
) {
    /**
     * A utility function used by both the initiating party and the counter party to transform a provided
     * [WireTransactionBuilder] into a [TransactionBuilder] for further modification.
     *
     * Before returning a [TransactionBuilder] the node that wishes to continue modification will verify that
     * the signature provided by the counter party over the contents of the provided [WireTransactionBuilder].
     *
     * @param serviceHub The [ServiceHub] of the node that will continue to modify the transaction
     * @return Returns a [TransactionBuilder] suitable for further modification by a node
     */
    @Suspendable
    fun toTransactionBuilder(serviceHub: ServiceHub): TransactionBuilder = TransactionBuilder(
        notary,
        lockId,
        inputs.toMutableList(),
        attachments.toMutableList(),
        outputs.toMutableList(),
        commands.toMutableList(),
        window,
        privacySalt,
        references.toMutableList(),
        serviceHub)
}