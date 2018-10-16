package net.corda.configsample

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class GetStringConfigFlow(private val configKey: String) : FlowLogic<String>() {
    object READING : ProgressTracker.Step("Reading config")
    override val progressTracker = ProgressTracker(READING)

    @Suspendable
    override fun call(): String {
        progressTracker.currentStep = READING
        val config = serviceHub.getAppContext().config
        return config.getString(configKey)
    }
}



@CordaSerializable
data class GetStringTestState(val responses: List<String>, val issuer: AbstractParty) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(issuer)

}


@CordaSerializable
object GetStringTestCommand : CommandData


class GetStringTestContract : Contract {
    override fun verify(tx: LedgerTransaction) {
    }
}