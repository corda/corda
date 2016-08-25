package com.r3corda.node.services.monitor

import co.paralleluniverse.common.util.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.contracts.asset.Cash
import com.r3corda.contracts.asset.InsufficientBalanceException
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.toStringShort
import com.r3corda.core.messaging.Message
import com.r3corda.core.messaging.MessageRecipients
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.node.services.Wallet
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.serialization.serialize
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.services.api.AbstractNodeService
import com.r3corda.node.services.persistence.DataVending
import com.r3corda.node.services.statemachine.StateMachineManager
import com.r3corda.node.utilities.AddOrRemove
import org.slf4j.LoggerFactory
import java.security.KeyPair
import java.security.PublicKey
import java.time.Instant
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * Service which allows external clients to monitor the wallet service and state machine manager, as well as trigger
 * actions within the node. The service also sends requests for user input back to clients, for example to enter
 * additional information while a protocol runs, or confirm an action.
 *
 * This is intended to enable a range of tools from end user UI to ops tools which monitor health across a number of nodes.
 */
// TODO: Implement authorization controls+
// TODO: Replace this entirely with a publish/subscribe based solution on a to-be-written service (likely JMS or similar),
//       rather than implement authentication and publish/subscribe ourselves.
// TODO: Clients need to be able to indicate whether they support interactivity (no point in sending requests for input
//       to a monitoring tool)
@ThreadSafe
class WalletMonitorService(net: MessagingService, val smm: StateMachineManager, val services: ServiceHub)
    : AbstractNodeService(net, services.networkMapCache) {
    companion object {
        val REGISTER_TOPIC = "platform.wallet_monitor.register"
        val DEREGISTER_TOPIC = "platform.wallet_monitor.deregister"
        val STATE_TOPIC = "platform.wallet_monitor.state_snapshot"
        val IN_EVENT_TOPIC = "platform.wallet_monitor.in"
        val OUT_EVENT_TOPIC = "platform.wallet_monitor.out"

        val logger = loggerFor<WalletMonitorService>()
    }

    val listeners: MutableSet<RegisteredListener> = HashSet()

    data class RegisteredListener(val recipients: MessageRecipients, val sessionID: Long)

    init {
        addMessageHandler(REGISTER_TOPIC) { req: RegisterRequest -> processRegisterRequest(req) }
        addMessageHandler(DEREGISTER_TOPIC) { req: DeregisterRequest -> processDeregisterRequest(req) }
        addMessageHandler(OUT_EVENT_TOPIC) { req: ClientToServiceCommandMessage -> processEventRequest(req) }

        // Notify listeners on state changes
        services.storageService.validatedTransactions.updates.subscribe { tx -> notifyTransaction(tx) }
        services.walletService.updates.subscribe { update -> notifyWalletUpdate(update) }
        smm.changes.subscribe { change ->
            val fiberId: Long = change.third
            val logic: ProtocolLogic<*> = change.first
            val progressTracker = logic.progressTracker

            notifyEvent(ServiceToClientEvent.StateMachine(Instant.now(), fiberId, logic.javaClass.name, change.second))
            if (progressTracker != null) {
                when (change.second) {
                    AddOrRemove.ADD -> progressTracker.changes.subscribe { progress ->
                        notifyEvent(ServiceToClientEvent.Progress(Instant.now(), fiberId, progress.toString()))
                    }
                    AddOrRemove.REMOVE -> {
                        // Nothing to do
                    }
                }
            }
        }
    }

    @VisibleForTesting
    internal fun notifyWalletUpdate(update: Wallet.Update)
            = notifyEvent(ServiceToClientEvent.OutputState(Instant.now(), update.consumed, update.produced))

    @VisibleForTesting
    internal fun notifyTransaction(transaction: SignedTransaction)
        = notifyEvent(ServiceToClientEvent.Transaction(Instant.now(), transaction))

    private fun processEventRequest(reqMessage: ClientToServiceCommandMessage) {
        val req = reqMessage.command
        val result: TransactionBuildResult? =
                try {
                    when (req) {
                        is ClientToServiceCommand.IssueCash -> issueCash(req)
                        is ClientToServiceCommand.PayCash -> initatePayment(req)
                        is ClientToServiceCommand.ExitCash -> exitCash(req)
                        else -> throw IllegalArgumentException("Unknown request type ${req.javaClass.name}")
                    }
                } catch(ex: Exception) {
                    TransactionBuildResult.Failed(ex.message)
                }

        // Send back any result from the event. Not all events (especially TransactionInput) produce a
        // result.
        if (result != null) {
            val event = ServiceToClientEvent.TransactionBuild(Instant.now(), req.id, result)
            val respMessage = net.createMessage(IN_EVENT_TOPIC, reqMessage.sessionID,
                    event.serialize().bits)
            net.send(respMessage, reqMessage.getReplyTo(services.networkMapCache))
        }
    }

    /**
     * Process a request from a monitor to remove them from the subscribers.
     */
    fun processDeregisterRequest(req: DeregisterRequest) {
        val message: Message
        try {
            // TODO: Session ID should be managed by the messaging layer, so it handles ensuring that the
            //       request comes from the same endpoint that registered at the start.
            listeners.remove(RegisteredListener(req.replyToRecipient, req.sessionID))
            message = net.createMessage(DEREGISTER_TOPIC, req.sessionID, DeregisterResponse(true).serialize().bits)
        } catch (ex: IllegalStateException) {
            message = net.createMessage(DEREGISTER_TOPIC, req.sessionID, DeregisterResponse(false).serialize().bits)
        }
        net.send(message, req.replyToRecipient)
    }

    /**
     * Process a request from a monitor to add them to the subscribers. This includes hooks to authenticate the request,
     * but currently all requests pass (and there's no access control on wallets, so it has no actual meaning).
     */
    fun processRegisterRequest(req: RegisterRequest) {
        val message: Message
        try {
            message = net.createMessage(REGISTER_TOPIC, req.sessionID, RegisterResponse(true).serialize().bits)
            listeners.add(RegisteredListener(req.replyToRecipient, req.sessionID))
            val stateMessage = StateSnapshotMessage(services.walletService.currentWallet.states.map { it.state.data }.toList(),
                    smm.allStateMachines.map { it.javaClass.name })
            net.send(net.createMessage(STATE_TOPIC, DEFAULT_SESSION_ID, stateMessage.serialize().bits), req.replyToRecipient)
        } catch (ex: IllegalStateException) {
            message = net.createMessage(REGISTER_TOPIC, req.sessionID, RegisterResponse(false).serialize().bits)
        }
        net.send(message, req.replyToRecipient)
    }

    private fun notifyEvent(event: ServiceToClientEvent) = listeners.forEach { monitor ->
        net.send(net.createMessage(IN_EVENT_TOPIC, monitor.sessionID, event.serialize().bits),
                monitor.recipients)
    }

    /**
     * Notifies the node associated with the [recipient] public key. Returns a future holding a Boolean of whether the
     * node accepted the transaction or not.
     */
    private fun notifyRecipientAboutTransaction(
            recipient: PublicKey,
            transaction: SignedTransaction
    ): ListenableFuture<Unit> {
        val recipientNodeInfo = services.networkMapCache.getNodeByPublicKey(recipient) ?: throw PublicKeyLookupFailed(recipient)
        return DataVending.Service.notify(net, services.storageService.myLegalIdentity,
                recipientNodeInfo, transaction)
    }

    // TODO: Make a lightweight protocol that manages this workflow, rather than embedding it directly in the service
    private fun initatePayment(req: ClientToServiceCommand.PayCash): TransactionBuildResult {
        val builder: TransactionBuilder = TransactionType.General.Builder(null)
        // TODO: Have some way of restricting this to states the caller controls
        try {
            Cash().generateSpend(builder, Amount(req.pennies, req.tokenDef.product), req.owner,
                    // TODO: Move cash state filtering by issuer down to the contract itself
                    services.walletService.currentWallet.statesOfType<Cash.State>().filter { it.state.data.amount.token == req.tokenDef },
                    setOf(req.tokenDef.issuer.party))
            .forEach {
                val key = services.keyManagementService.keys[it] ?: throw IllegalStateException("Could not find signing key for ${it.toStringShort()}")
                builder.signWith(KeyPair(it, key))
            }
            val tx = builder.toSignedTransaction()
            services.walletService.notify(tx.tx)
            notifyRecipientAboutTransaction(req.owner, tx)
            return TransactionBuildResult.Complete(tx, "Cash payment completed")
        } catch(ex: InsufficientBalanceException) {
            return TransactionBuildResult.Failed(ex.message ?: "Insufficient balance")
        }
    }

    // TODO: Make a lightweight protocol that manages this workflow, rather than embedding it directly in the service
    private fun exitCash(req: ClientToServiceCommand.ExitCash): TransactionBuildResult {
        val builder: TransactionBuilder = TransactionType.General.Builder(null)
        val issuer = PartyAndReference(services.storageService.myLegalIdentity, req.issueRef)
        Cash().generateExit(builder, Amount(req.pennies, Issued(issuer, req.currency)),
                services.walletService.currentWallet.statesOfType<Cash.State>().filter { it.state.data.owner == issuer.party.owningKey })
        builder.signWith(services.storageService.myLegalIdentityKey)
        val tx = builder.toSignedTransaction()
        services.walletService.notify(tx.tx)
        // Notify the owners
        val inputStatesNullable = services.walletService.statesForRefs(tx.tx.inputs)
        val inputStates = inputStatesNullable.values.filterNotNull().map { it.data }
        if (inputStatesNullable.size != inputStates.size) {
            val unresolvedStateRefs = inputStatesNullable.filter { it.value == null }.map { it.key }
            throw InputStateRefResolveFailed(unresolvedStateRefs)
        }
        inputStates.filterIsInstance<Cash.State>().map { it.owner }.toSet().forEach {
            notifyRecipientAboutTransaction(it, tx)
        }
        return TransactionBuildResult.Complete(tx, "Cash destruction completed")
    }

    // TODO: Make a lightweight protocol that manages this workflow, rather than embedding it directly in the service
    private fun issueCash(req: ClientToServiceCommand.IssueCash): TransactionBuildResult {
        val builder: TransactionBuilder = TransactionType.General.Builder(notary = req.notary)
        val issuer = PartyAndReference(services.storageService.myLegalIdentity, req.issueRef)
        Cash().generateIssue(builder, Amount(req.pennies, Issued(issuer, req.currency)), req.recipient, req.notary)
        builder.signWith(services.storageService.myLegalIdentityKey)
        val tx = builder.toSignedTransaction()
        services.walletService.notify(tx.tx)
        notifyRecipientAboutTransaction(req.recipient, tx)
        return TransactionBuildResult.Complete(tx, "Cash issuance completed")
    }

    class PublicKeyLookupFailed(failedPublicKey: PublicKey) :
            Exception("Failed to lookup public keys $failedPublicKey")

    class InputStateRefResolveFailed(stateRefs: List<StateRef>) :
            Exception("Failed to resolve input StateRefs $stateRefs")
}
