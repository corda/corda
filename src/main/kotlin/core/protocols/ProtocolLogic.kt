/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.protocols

import co.paralleluniverse.fibers.Suspendable
import core.node.services.ServiceHub
import core.messaging.MessageRecipients
import core.utilities.ProgressTracker
import core.utilities.UntrustworthyData
import org.slf4j.Logger

/**
 * A sub-class of [ProtocolLogic<T>] implements a protocol flow using direct, straight line blocking code. Thus you
 * can write complex protocol logic in an ordinary fashion, without having to think about callbacks, restarting after
 * a node crash, how many instances of your protocol there are running and so on.
 *
 * Invoking the network will cause the call stack to be suspended onto the heap and then serialized to a database using
 * the Quasar fibers framework. Because of this, if you need access to data that might change over time, you should
 * request it just-in-time via the [serviceHub] property which is provided. Don't try and keep data you got from a
 * service across calls to send/receive/sendAndReceive because the world might change in arbitrary ways out from
 * underneath you, for instance, if the node is restarted or reconfigured!
 *
 * Additionally, be aware of what data you pin either via the stack or in your [ProtocolLogic] implementation. Very large
 * objects or datasets will hurt performance by increasing the amount of data stored in each checkpoint.
 *
 * If you'd like to use another ProtocolLogic class as a component of your own, construct it on the fly and then pass
 * it to the [subProtocol] method. It will return the result of that protocol when it completes.
 */
abstract class ProtocolLogic<T> {
    /** Reference to the [Fiber] instance that is the top level controller for the entire flow. */
    lateinit var psm: ProtocolStateMachine<*>

    /** This is where you should log things to. */
    val logger: Logger get() = psm.logger

    /** Provides access to big, heavy classes that may be reconstructed from time to time, e.g. across restarts */
    val serviceHub: ServiceHub get() = psm.serviceHub

    // Kotlin helpers that allow the use of generic types.
    inline fun <reified T : Any> sendAndReceive(topic: String, destination: MessageRecipients, sessionIDForSend: Long,
                                                sessionIDForReceive: Long, obj: Any): UntrustworthyData<T> {
        return psm.sendAndReceive(topic, destination, sessionIDForSend, sessionIDForReceive, obj, T::class.java)
    }
    inline fun <reified T : Any> receive(topic: String, sessionIDForReceive: Long): UntrustworthyData<T> {
        return psm.receive(topic, sessionIDForReceive, T::class.java)
    }
    @Suspendable fun send(topic: String, destination: MessageRecipients, sessionID: Long, obj: Any) {
        psm.send(topic, destination, sessionID, obj)
    }

    /**
     * Invokes the given subprotocol by simply passing through this [ProtocolLogic]s reference to the
     * [ProtocolStateMachine] and then calling the [call] method.
     */
    @Suspendable fun <R> subProtocol(subLogic: ProtocolLogic<R>): R {
        subLogic.psm = psm
        maybeWireUpProgressTracking(subLogic)
        val r = subLogic.call()
        // It's easy to forget this when writing protocols so we just step it to the DONE state when it completes.
        subLogic.progressTracker?.currentStep = ProgressTracker.DONE
        return r
    }

    private fun maybeWireUpProgressTracking(subLogic: ProtocolLogic<*>) {
        val ours = progressTracker
        val theirs = subLogic.progressTracker
        if (ours != null && theirs != null)
            ours.childrenFor[ours.currentStep] = theirs
    }

    /**
     * Override this to provide a [ProgressTracker]. If one is provided and stepped, the framework will do something
     * helpful with the progress reports. If this protocol is invoked as a sub-protocol of another, then the
     * tracker will be made a child of the current step in the parent. If it's null, this protocol doesn't track
     * progress.
     *
     * Note that this has to return a tracker before the protocol is invoked. You can't change your mind half way
     * through.
     */
    open val progressTracker: ProgressTracker? = null

    /** This is where you fill out your business logic. */
    @Suspendable
    abstract fun call(): T
}