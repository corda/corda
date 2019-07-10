package net.corda.testing.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.toFuture
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.testing.node.internal.TestStartedNode
import rx.Observable
import kotlin.reflect.KClass

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

inline fun <reified P : FlowLogic<*>> TestStartedNode.registerCordappFlowFactory(
        initiatingFlowClass: KClass<out FlowLogic<*>>,
        initiatedFlowVersion: Int = 1,
        noinline flowFactory: (FlowSession) -> P): CordaFuture<P> {

    val observable = internals.registerInitiatedFlowFactory(
            initiatingFlowClass.java,
            P::class.java,
            InitiatedFlowFactory.CorDapp(initiatedFlowVersion, "", flowFactory),
            track = true)
    return observable.toFuture()
}

fun <T : FlowLogic<*>> TestStartedNode.registerCoreFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>,
                                                               initiatedFlowClass: Class<T>,
                                                               flowFactory: (FlowSession) -> T, track: Boolean): Observable<T> {
    return this.internals.registerInitiatedFlowFactory(initiatingFlowClass, initiatedFlowClass, InitiatedFlowFactory.Core(flowFactory), track)
}