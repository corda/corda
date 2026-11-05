package com.example.javaxcordapp

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.UUID
import javax.persistence.EntityManager

/** Records a [JavaxTestState] with the given label, as a transaction with this node as the only participant. */
@StartableByRPC
class CreateJavaxTestStateFlow(private val label: String) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val builder = TransactionBuilder(notary)
                .addOutputState(JavaxTestState(label, UUID.randomUUID(), listOf(ourIdentity)))
                .addCommand(JavaxTestContract.Create(), ourIdentity.owningKey)
        return subFlow(FinalityFlow(serviceHub.signInitialTransaction(builder), emptyList()))
    }
}

/** Counts the recorded states with the given label through `withEntityManager`, typed on the `javax.persistence` API. */
@StartableByRPC
class CountJavaxTestStatesFlow(private val label: String) : FlowLogic<Long>() {
    @Suspendable
    override fun call(): Long = serviceHub.withEntityManager { JavaxTestRepository.count(this, label) }
}

object JavaxTestRepository {
    /** A suspendable flow helper whose descriptor names the `javax.persistence` API, as Quasar sees it when it instruments the class. */
    @Suspendable
    @JvmStatic
    fun count(entityManager: EntityManager, label: String): Long {
        val builder = entityManager.criteriaBuilder
        val query = builder.createQuery(Long::class.javaObjectType)
        val states = query.from(PersistentJavaxTestState::class.java)
        query.select(builder.count(states)).where(builder.equal(states.get<String>("label"), label))
        return entityManager.createQuery(query).singleResult
    }
}
