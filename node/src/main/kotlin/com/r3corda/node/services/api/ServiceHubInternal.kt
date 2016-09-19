package com.r3corda.node.services.api

import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.TxWritableStorageService
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolLogicRefFactory

interface MessagingServiceInternal : MessagingService {
    /**
     * Initiates shutdown: if called from a thread that isn't controlled by the executor passed to the constructor
     * then this will block until all in-flight messages have finished being handled and acknowledged. If called
     * from a thread that's a part of the [AffinityExecutor] given to the constructor, it returns immediately and
     * shutdown is asynchronous.
     */
    fun stop()
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
     * sends them to the vault for further processing. This is intended for implementations to call from
     * [recordTransactions].
     *
     * @param txs The transactions to record.
     */
    internal fun recordTransactionsInternal(writableStorageService: TxWritableStorageService, txs: Iterable<SignedTransaction>) {
        txs.forEach { writableStorageService.validatedTransactions.addTransaction(it) }
        vaultService.notifyAll(txs.map { it.tx })
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
