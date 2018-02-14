package net.corda.configsample

import net.corda.core.flows.FlowLogic

class ConfigSampleFlow : FlowLogic<String>() {
    override fun call(): String {
        val config = serviceHub.getAppContext().config
        return config.getString("someStringValue")
    }
}