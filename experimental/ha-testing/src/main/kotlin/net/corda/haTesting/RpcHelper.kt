package net.corda.haTesting

import net.corda.client.rpc.RPCException
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration


private val logger: Logger = LoggerFactory.getLogger("RpcHelper")

inline fun <T, A, B, C, reified R : FlowLogic<T>> CordaRPCOps.startFlowWithRetryAndGet(
        @Suppress("UNUSED_PARAMETER") crossinline
        flowConstructor: (A, B, C) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        retryInterval: Duration = 5.seconds,
        giveUpInterval: Duration = 5.minutes
): T {

    return arithmeticBackoff(retryInterval, giveUpInterval, "startFlowWithRetryAndGet") {
        this.startFlow(flowConstructor, arg0, arg1, arg2).returnValue.getOrThrow()
    }

}

inline fun <T, A, B, C, D, E, reified R : FlowLogic<T>> CordaRPCOps.startFlowWithRetryAndGet(
        @Suppress("UNUSED_PARAMETER") crossinline
        flowConstructor: (A, B, C, D, E) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        arg3: D,
        arg4: E,
        retryInterval: Duration = 5.seconds,
        giveUpInterval: Duration = 5.minutes
): T {

    return arithmeticBackoff(retryInterval, giveUpInterval, "startFlowWithRetryAndGet") {
        this.startFlow(flowConstructor, arg0, arg1, arg2, arg3, arg4).returnValue.getOrThrow()
    }

}

fun <T> arithmeticBackoff(retryInterval: Duration, giveUpInterval: Duration, meaningfulDescription: String, op: () -> T): T {
    val start = System.currentTimeMillis()
    var iterCount = 0

    do {
        try {
            iterCount++
            return op()
        } catch (ex: RPCException) {
            logger.warn("Exception $meaningfulDescription, iteration #$iterCount", ex)
            Thread.sleep(iterCount * retryInterval.toMillis())
        }
    } while ((System.currentTimeMillis() - start) < giveUpInterval.toMillis())

    throw IllegalStateException("$meaningfulDescription - failed, total number of times tried: $iterCount")
}

inline fun <reified T : ContractState> CordaRPCOps.vaultQueryByWithRetry(criteria: QueryCriteria = QueryCriteria.VaultQueryCriteria(),
                                                                         paging: PageSpecification = PageSpecification(),
                                                                         sorting: Sort = Sort(emptySet()),
                                                                         retryInterval: Duration = 5.seconds,
                                                                         giveUpInterval: Duration = 5.minutes): Vault.Page<T> {
    return arithmeticBackoff(retryInterval, giveUpInterval, "vaultQueryByWithRetry") {
        this.vaultQueryBy(criteria, paging, sorting, T::class.java)
    }
}