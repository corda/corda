/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.testing.node.internal.TestStartedNode
import kotlin.reflect.KClass

/**
 * Allows to simplify writing flows that simply rend a message back to an initiating flow.
 */
class Answer<out R : Any>(session: FlowSession, override val answer: R, closure: (result: R) -> Unit = {}) : SimpleAnswer<R>(session, closure)

/**
 * Allows to simplify writing flows that simply rend a message back to an initiating flow.
 */
abstract class SimpleAnswer<out R : Any>(private val session: FlowSession, private val closure: (result: R) -> Unit = {}) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val tmp = answer
        closure(tmp)
        session.send(tmp)
    }

    protected abstract val answer: R
}

/**
 * A flow that does not do anything when triggered.
 */
class NoAnswer(private val closure: () -> Unit = {}) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = closure()
}

/**
 * Allows to register a flow of type [R] against an initiating flow of type [I].
 */
inline fun <I : FlowLogic<*>, reified R : FlowLogic<*>> TestStartedNode.registerInitiatedFlow(initiatingFlowType: KClass<I>, crossinline construct: (session: FlowSession) -> R) {
    internalRegisterFlowFactory(initiatingFlowType.java, InitiatedFlowFactory.Core { session -> construct(session) }, R::class.javaObjectType, true)
}

/**
 * Allows to register a flow of type [Answer] against an initiating flow of type [I], returning a valure of type [R].
 */
inline fun <I : FlowLogic<*>, reified R : Any> TestStartedNode.registerAnswer(initiatingFlowType: KClass<I>, value: R) {
    internalRegisterFlowFactory(initiatingFlowType.java, InitiatedFlowFactory.Core { session -> Answer(session, value) }, Answer::class.javaObjectType, true)
}

/**
 * Extracts data from a [Map[FlowSession, UntrustworthyData<Any>]] without performing checks and casting to [R].
 */
@Suppress("UNCHECKED_CAST")
infix fun <R : Any> Map<FlowSession, UntrustworthyData<Any>>.from(session: FlowSession): R = this[session]!!.unwrap { it as R }

/**
 * Creates a [Pair([session], [Class])] from this [Class].
 */
infix fun <T : Class<out Any>> T.from(session: FlowSession): Pair<FlowSession, T> = session to this

/**
 * Creates a [Pair([session], [Class])] from this [KClass].
 */
infix fun <T : Any> KClass<T>.from(session: FlowSession): Pair<FlowSession, Class<T>> = session to this.javaObjectType

/**
 * Suspends until a message has been received for each session in the specified [sessions].
 *
 * Consider [receiveAll(receiveType: Class<R>, sessions: List<FlowSession>): List<UntrustworthyData<R>>] when the same type is expected from all sessions.
 *
 * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
 * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly
 * corrupted data in order to exploit your code.
 *
 * @returns a [Map] containing the objects received, wrapped in an [UntrustworthyData], by the [FlowSession]s who sent them.
 */
@Suspendable
fun FlowLogic<*>.receiveAll(session: Pair<FlowSession, Class<out Any>>, vararg sessions: Pair<FlowSession, Class<out Any>>): Map<FlowSession, UntrustworthyData<Any>> {
    val allSessions = arrayOf(session, *sessions)
    allSessions.enforceNoDuplicates()
    return receiveAllMap(mapOf(*allSessions))
}

/**
 * Suspends until a message has been received for each session in the specified [sessions].
 *
 * Consider [sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, UntrustworthyData<Any>>] when sessions are expected to receive different types.
 *
 * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
 * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly
 * corrupted data in order to exploit your code.
 *
 * @returns a [List] containing the objects received, wrapped in an [UntrustworthyData], with the same order of [sessions].
 */
@Suspendable
fun <R : Any> FlowLogic<*>.receiveAll(receiveType: Class<R>, session: FlowSession, vararg sessions: FlowSession): List<UntrustworthyData<R>> = receiveAll(receiveType, listOf(session, *sessions))

/**
 * Suspends until a message has been received for each session in the specified [sessions].
 *
 * Consider [sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, UntrustworthyData<Any>>] when sessions are expected to receive different types.
 *
 * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
 * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly
 * corrupted data in order to exploit your code.
 *
 * @returns a [List] containing the objects received, wrapped in an [UntrustworthyData], with the same order of [sessions].
 */
@Suspendable
inline fun <reified R : Any> FlowLogic<*>.receiveAll(session: FlowSession, vararg sessions: FlowSession): List<UntrustworthyData<R>> = receiveAll(R::class.javaObjectType, listOf(session, *sessions))

private fun Array<out Pair<FlowSession, Class<out Any>>>.enforceNoDuplicates() {
    require(this.size == this.toSet().size) { "A flow session can only appear once as argument." }
}