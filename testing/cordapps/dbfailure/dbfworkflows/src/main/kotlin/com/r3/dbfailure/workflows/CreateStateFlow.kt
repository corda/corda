package com.r3.dbfailure.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.dbfailure.contracts.DbFailureContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

object CreateStateFlow {

    enum class ErrorTarget(val targetNumber: Int) {
        NoError(0),
        ServiceSqlSyntaxError(1),
        ServiceNullConstraintViolation(2),
        ServiceValidUpdate(3),
        ServiceReadState(4),
        ServiceCheckForState(5),
        ServiceThrowInvalidParameter(6),
        TxInvalidState(10),
        FlowSwallowErrors(100),
        ServiceSwallowErrors(1000)
    }

    fun errorTargetsToNum(vararg targets: ErrorTarget): Int {
        return targets.map { it.targetNumber }.sum()
    }

    private val targetMap = ErrorTarget.values().associateBy(ErrorTarget::targetNumber)

    fun getServiceTarget(target: Int?): ErrorTarget {
        return target?.let { targetMap.getValue(it % 10) } ?: CreateStateFlow.ErrorTarget.NoError
    }

    fun getServiceExceptionHandlingTarget(target: Int?): ErrorTarget {
        return target?.let { targetMap.getValue(((it / 1000) % 10) * 1000) } ?: CreateStateFlow.ErrorTarget.NoError
    }

    fun getTxTarget(target: Int?): ErrorTarget {
        return target?.let { targetMap.getValue(((it / 10) % 10) * 10) } ?: CreateStateFlow.ErrorTarget.NoError
    }

    fun getFlowTarget(target: Int?): ErrorTarget {
        return target?.let { targetMap.getValue(((it / 100) % 10) * 100) } ?: CreateStateFlow.ErrorTarget.NoError
    }

    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val randomValue: String, private val errorTarget: Int) : FlowLogic<UniqueIdentifier>() {

        @Suspendable
        override fun call(): UniqueIdentifier {
            logger.info("Test flow: starting")
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val txTarget = getTxTarget(errorTarget)
            logger.info("Test flow: The tx error target is $txTarget")
            val state = DbFailureContract.TestState(UniqueIdentifier(), ourIdentity, if (txTarget == CreateStateFlow.ErrorTarget.TxInvalidState) null else randomValue, errorTarget)
            val txCommand = Command(DbFailureContract.Commands.Create(), ourIdentity.owningKey)

            logger.info("Test flow: tx builder")
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(state)
                    .addCommand(txCommand)

            logger.info("Test flow: verify")
            txBuilder.verify(serviceHub)

            val signedTx = serviceHub.signInitialTransaction(txBuilder)

            try {
                logger.info("Test flow: recording transaction")
                serviceHub.recordTransactions(signedTx)
            } catch (t: Throwable) {
                if (getFlowTarget(errorTarget) == CreateStateFlow.ErrorTarget.FlowSwallowErrors) {
                    logger.info("Test flow: Swallowing all exception! Muahahaha!", t)
                } else {
                    logger.info("Test flow: caught exception - rethrowing")
                    throw t
                }
            }
            logger.info("Test flow: returning")
            return state.linearId
        }
    }
}