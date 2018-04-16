package net.corda.configsample

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
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
