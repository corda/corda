package com.r3corda.node.services.api

import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.TxWritableStorageService
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolLogicRefFactory

interface MessagingServiceInternal: MessagingService {
    fun stop()

    // Allow messaging service to be signalled by the NetworkMapCache about Nodes
    // Thus providing an opportunity to permission the other Node and possibly to setup a link
    fun registerTrustedAddress(address: SingleMessageRecipient)
}

/**
 * This class lets you start up a [MessagingService]. Its purpose is to stop you from getting access to the methods
 * on the messaging service interface until you have successfully started up the system. One of these objects should
 * be the only way to obtain a reference to a [MessagingService]. Startup may be a slow process: some implementations
 * may let you cast the returned future to an object that lets you get status info.
 *
 * A specific implementation of the controller class will have extra features that let you customise it before starting
 * it up.
 */
interface MessagingServiceBuilder<out T : MessagingServiceInternal> {
    fun start(): ListenableFuture<out T>
}

abstract class ServiceHubInternal : ServiceHub {
    abstract val monitoringService: MonitoringService
    abstract val protocolLogicRefFactory: ProtocolLogicRefFactory

    abstract override val networkService: MessagingServiceInternal

    /**
     * Given a list of [SignedTransaction]s, writes them to the given storage for validated transactions and then
     * sends them to the wallet for further processing. This is intended for implementations to call from
     * [recordTransactions].
     *
     * @param txs The transactions to record.
     */
    internal fun recordTransactionsInternal(writableStorageService: TxWritableStorageService, txs: Iterable<SignedTransaction>) {
        txs.forEach { writableStorageService.validatedTransactions.addTransaction(it) }
        walletService.notifyAll(txs.map { it.tx })
    }

    /**
     * TODO: borrowing this method from service manager work in another branch.  It's required to avoid circular dependency
     *       between SMM and the scheduler.  That particular problem should also be resolved by the service manager work
     *       itself, at which point this method would not be needed (by the scheduler).
     */
    abstract fun <T> startProtocol(loggerName: String, logic: ProtocolLogic<T>): ListenableFuture<T>

    override fun <T : Any> invokeProtocolAsync(logicType: Class<out ProtocolLogic<T>>, vararg args: Any?): ListenableFuture<T> {
        val logicRef = protocolLogicRefFactory.create(logicType, *args)
        @Suppress("UNCHECKED_CAST")
        val logic = protocolLogicRefFactory.toProtocolLogic(logicRef) as ProtocolLogic<T>
        return startProtocol(logicType.simpleName, logic)
    }
}
