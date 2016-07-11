package com.r3corda.core.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.UntrustworthyData
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
abstract class ProtocolLogic<out T> {

    /** Reference to the [Fiber] instance that is the top level controller for the entire flow. */
    lateinit var psm: ProtocolStateMachine<*>

    /** This is where you should log things to. */
    val logger: Logger get() = psm.logger

    /**
     * Provides access to big, heavy classes that may be reconstructed from time to time, e.g. across restarts. It is
     * only available once the protocol has started, which means it cannnot be accessed in the constructor. Either
     * access this lazily or from inside [call].
     */
    val serviceHub: ServiceHub get() = psm.serviceHub

    /**
     * The topic to use when communicating with other parties. If more than one topic is required then use sub-protocols.
     * Note that this is temporary until protocol sessions are properly implemented.
     */
    protected abstract val topic: String

    // Kotlin helpers that allow the use of generic types.
    inline fun <reified T : Any> sendAndReceive(destination: Party,
                                                sessionIDForSend: Long,
                                                sessionIDForReceive: Long,
                                                payload: Any): UntrustworthyData<T> {
        return psm.sendAndReceive(topic, destination, sessionIDForSend, sessionIDForReceive, payload, T::class.java)
    }

    inline fun <reified T : Any> receive(sessionIDForReceive: Long): UntrustworthyData<T> {
        return receive(sessionIDForReceive, T::class.java)
    }

    @Suspendable fun <T : Any> receive(sessionIDForReceive: Long, receiveType: Class<T>): UntrustworthyData<T> {
        return psm.receive(topic, sessionIDForReceive, receiveType)
    }

    @Suspendable fun send(destination: Party, sessionID: Long, payload: Any) {
        psm.send(topic, destination, sessionID, payload)
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
        if (ours != null && theirs != null) {
            if (ours.currentStep == ProgressTracker.UNSTARTED) {
                logger.warn("ProgressTracker has not been started for $this")
                ours.nextStep()
            }
            ours.setChildProgressTracker(ours.currentStep, theirs)
        }
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