package com.r3corda.node.internal

import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.linearHeadsOfType
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.node.api.*
import com.r3corda.node.utilities.*
import java.time.LocalDateTime
import java.util.*
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaType

class APIServerImpl(val node: AbstractNode) : APIServer {

    override fun serverTime(): LocalDateTime = LocalDateTime.now(node.services.clock)

    override fun queryStates(query: StatesQuery): List<StateRef> {
        // We're going to hard code two options here for now and assume that all LinearStates are deals
        // Would like to maybe move to a model where we take something like a JEXL string, although don't want to develop
        // something we can't later implement against a persistent store (i.e. need to pick / build a query engine)
        if (query is StatesQuery.Selection) {
            if (query.criteria is StatesQuery.Criteria.AllDeals) {
                val states = node.services.walletService.linearHeads
                return states.values.map { it.ref }
            } else if (query.criteria is StatesQuery.Criteria.Deal) {
                val states = node.services.walletService.linearHeadsOfType<DealState>().filterValues {
                    it.state.ref == query.criteria.ref
                }
                return states.values.map { it.ref }
            }
        }
        return emptyList()
    }

    override fun fetchStates(states: List<StateRef>): Map<StateRef, ContractState?> {
        return node.services.walletService.statesForRefs(states)
    }

    override fun fetchTransactions(txs: List<SecureHash>): Map<SecureHash, SignedTransaction?> {
        throw UnsupportedOperationException()
    }

    override fun buildTransaction(type: ContractDefRef, steps: List<TransactionBuildStep>): SerializedBytes<WireTransaction> {
        throw UnsupportedOperationException()
    }

    override fun generateTransactionSignature(tx: SerializedBytes<WireTransaction>): DigitalSignature.WithKey {
        throw UnsupportedOperationException()
    }

    override fun commitTransaction(tx: SerializedBytes<WireTransaction>, signatures: List<DigitalSignature.WithKey>): SecureHash {
        throw UnsupportedOperationException()
    }

    override fun invokeProtocolSync(type: ProtocolRef, args: Map<String, Any?>): Any? {
        return invokeProtocolAsync(type, args).get()
    }

    private fun invokeProtocolAsync(type: ProtocolRef, args: Map<String, Any?>): ListenableFuture<out Any?> {
        if (type is ProtocolClassRef) {
            val clazz = Class.forName(type.className)
            if (ProtocolLogic::class.java.isAssignableFrom(clazz)) {
                // TODO for security, check annotated as exposed on API?  Or have PublicProtocolLogic... etc
                nextConstructor@ for (constructor in clazz.kotlin.constructors) {
                    val params = HashMap<KParameter, Any?>()
                    for (parameter in constructor.parameters) {
                        if (parameter.isOptional && !args.containsKey(parameter.name)) {
                            // OK to be missing
                        } else if (args.containsKey(parameter.name)) {
                            val value = args[parameter.name]
                            if (value is Any) {
                                // TODO consider supporting more complex test here to support coercing numeric/Kotlin types
                                if (!(parameter.type.javaType as Class<*>).isAssignableFrom(value.javaClass)) {
                                    // Not null and not assignable
                                    break@nextConstructor
                                }
                            } else if (!parameter.type.isMarkedNullable) {
                                // Null and not nullable
                                break@nextConstructor
                            }
                            params[parameter] = value
                        } else {
                            break@nextConstructor
                        }
                    }
                    // If we get here then we matched every parameter
                    val protocol = constructor.callBy(params) as ProtocolLogic<*>
                    ANSIProgressRenderer.progressTracker = protocol.progressTracker
                    val future = node.smm.add("api-call", protocol)
                    return future
                }
            }
            throw UnsupportedOperationException("Could not find matching protocol and constructor for: $type $args")
        } else {
            throw UnsupportedOperationException("Unsupported ProtocolRef type: $type")
        }
    }

    override fun fetchProtocolsRequiringAttention(query: StatesQuery): Map<StateRef, ProtocolRequiringAttention> {
        throw UnsupportedOperationException()
    }

    override fun provideProtocolResponse(protocol: ProtocolInstanceRef, choice: SecureHash, args: Map<String, Any?>) {
        throw UnsupportedOperationException()
    }

}
