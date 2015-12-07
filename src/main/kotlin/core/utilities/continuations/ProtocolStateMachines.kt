/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.utilities.continuations

import com.esotericsoftware.kryo.io.Output
import core.messaging.MessagingSystem
import core.messaging.SingleMessageRecipient
import core.messaging.runOnNextMessage
import core.serialization.SerializeableWithKryo
import core.serialization.createKryo
import core.serialization.deserialize
import core.serialization.serialize
import core.utilities.trace
import org.apache.commons.javaflow.Continuation
import org.slf4j.Logger
import java.io.ByteArrayOutputStream

private val CONTINUATION_LOGGER = ThreadLocal<Logger>()

/**
 * A convenience mixing interface that can be implemented by an object that will act as a continuation.
 *
 * A ProtocolStateMachine must implement the run method from [Runnable], and the rest of what this interface
 * provides are pre-defined utility methods to ease implementation of such machines.
 */
@Suppress("UNCHECKED_CAST")
interface ProtocolStateMachine<CONTEXT_TYPE : Any> : Runnable {
    fun context(): CONTEXT_TYPE = Continuation.getContext() as CONTEXT_TYPE
    fun logger(): Logger = CONTINUATION_LOGGER.get()
}

@Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
inline fun <S : SerializeableWithKryo,
        CONTEXT_TYPE : Any> ProtocolStateMachine<CONTEXT_TYPE>.send(topic: String, sessionID: Long, obj: S) =
        Continuation.suspend(ContinuationResult.NotExpectingResponse(topic, sessionID, obj)) as CONTEXT_TYPE

@Suppress("UNCHECKED_CAST")
inline fun <reified R : SerializeableWithKryo,
        CONTEXT_TYPE : Any> ProtocolStateMachine<CONTEXT_TYPE>.sendAndReceive(topic: String, sessionIDForSend: Long,
                                                                              sessionIDForReceive: Long,
                                                                              obj: SerializeableWithKryo) =
        Continuation.suspend(ContinuationResult.ExpectingResponse(topic, sessionIDForSend, sessionIDForReceive,
                obj, R::class.java)) as Pair<CONTEXT_TYPE, R>


@Suppress("UNCHECKED_CAST")
inline fun <reified R : SerializeableWithKryo, CONTEXT_TYPE : Any> receive(topic: String, sessionIDForReceive: Long) =
        Continuation.suspend(ContinuationResult.ExpectingResponse(topic, -1, sessionIDForReceive, null,
                R::class.java)) as Pair<CONTEXT_TYPE, R>

open class ContinuationResult(val topic: String, val sessionIDForSend: Long, val sessionIDForReceive: Long, val obj: SerializeableWithKryo?) {
    class ExpectingResponse<R : SerializeableWithKryo>(
            topic: String,
            sessionIDForSend: Long,
            sessionIDForReceive: Long,
            obj: SerializeableWithKryo?,
            val responseType: Class<R>
    ) : ContinuationResult(topic, sessionIDForSend, sessionIDForReceive, obj)

    class NotExpectingResponse(topic: String, sessionIDForSend: Long, obj: SerializeableWithKryo?) : ContinuationResult(topic, sessionIDForSend, -1, obj)
}

fun Continuation.iterateStateMachine(net: MessagingSystem, otherSide: SingleMessageRecipient,
                                     transientContext: Any, continuationInput: Any?, logger: Logger): Continuation {
    // This will resume execution of the run() function inside the continuation at the place it left off.
    val oldLogger = CONTINUATION_LOGGER.get()
    val nextState = try {
        CONTINUATION_LOGGER.set(logger)
        Continuation.continueWith(this, continuationInput)
    } catch (t: Throwable) {
        logger.error("Caught error whilst invoking protocol state machine", t)
        throw t
    } finally {
        CONTINUATION_LOGGER.set(oldLogger)
    }
    // If continuation returns null, it's finished.
    val req = nextState?.value() as? ContinuationResult ?: return this

    // Else, it wants us to do something: send, receive, or send-and-receive. Firstly, checkpoint it, so we can restart
    // if something goes wrong.
    val bytes = run {
        val stream = ByteArrayOutputStream()
        Output(stream).use {
            createKryo().apply {
                isRegistrationRequired = false
                writeObject(it, nextState)
            }
        }
        stream.toByteArray()
    }

    if (req is ContinuationResult.ExpectingResponse<*>) {
        val topic = "${req.topic}.${req.sessionIDForReceive}"
        net.runOnNextMessage(topic) { netMsg ->
            val obj = netMsg.data.deserialize(req.responseType)
            logger.trace { "<- $topic : message of type ${obj.javaClass.name}" }
            nextState.iterateStateMachine(net, otherSide, transientContext, Pair(transientContext, obj), logger)
        }
    }
    // If an object to send was provided (not null), send it now.
    req.obj?.let {
        val topic = "${req.topic}.${req.sessionIDForSend}"
        logger.trace { "-> $topic : message of type ${it.javaClass.name}" }
        net.send(net.createMessage(topic, it.serialize()), otherSide)
    }
    if (req is ContinuationResult.NotExpectingResponse) {
        // We sent a message, but won't get a response, so we must re-enter the continuation to let it keep going.
        return nextState.iterateStateMachine(net, otherSide, transientContext, transientContext, logger)
    } else {
        return nextState
    }
}
