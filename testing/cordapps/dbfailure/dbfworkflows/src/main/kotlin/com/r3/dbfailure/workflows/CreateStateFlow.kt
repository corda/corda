package com.r3.dbfailure.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.dbfailure.contracts.DbFailureContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

// There is a bit of number fiddling in this class to encode/decode the error target instructions
@Suppress("MagicNumber")
object CreateStateFlow {

    // Encoding of error targets
    // 1s are errors actions to be taken in the vault listener in the service
    // 10s are errors caused in the flow
    // 100s control exception handling in the flow
    // 1000s control exception handlling in the service/vault listener
    enum class ErrorTarget(val targetNumber: Int) {
        NoError(0),
        ServiceSqlSyntaxError(10000),
        ServiceNullConstraintViolation(20000),
        ServiceValidUpdate(30000),
        ServiceReadState(40000),
        ServiceCheckForState(50000),
        ServiceThrowInvalidParameter(60000),
        ServiceThrowMotherOfAllExceptions(70000),
        ServiceThrowUnrecoverableError(80000),
        ServiceSqlSyntaxErrorOnConsumed(90000),
        ServiceConstraintViolationException(1000000),
        TxInvalidState(10),
        FlowSwallowErrors(100),
        ServiceSwallowErrors(1000)
    }

    fun errorTargetsToNum(vararg targets: ErrorTarget): Int {
        return targets.map { it.targetNumber }.sum()
    }

    private val targetMap = ErrorTarget.values().associateBy(ErrorTarget::targetNumber)

    fun getServiceTarget(target: Int?): ErrorTarget {
        return target?.let { targetMap.getValue(((it/10000) % 1000)*10000) } ?: CreateStateFlow.ErrorTarget.NoError
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
        companion object {
            var onExitingCall: () -> Unit =  {}
        }

        @Suspendable
        override fun call(): UniqueIdentifier {
            logger.info("Test flow: starting")
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val txTarget = getTxTarget(errorTarget)
            logger.info("Test flow: The tx error target is $txTarget")
            val state = DbFailureContract.TestState(
                UniqueIdentifier(),
                listOf(ourIdentity),
                if (txTarget == CreateStateFlow.ErrorTarget.TxInvalidState) null else randomValue,
                errorTarget, ourIdentity
            )
            val txCommand = Command(DbFailureContract.Commands.Create(), ourIdentity.owningKey)

            logger.info("Test flow: tx builder")
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(state)
                    .addCommand(txCommand)

            logger.info("Test flow: verify")
            txBuilder.verify(serviceHub)

            val signedTx = serviceHub.signInitialTransaction(txBuilder)

            @Suppress("TooGenericExceptionCaught") // this is fully intentional here, to allow twiddling with exceptions according to config
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
            onExitingCall()
            return state.linearId
        }
    }
}