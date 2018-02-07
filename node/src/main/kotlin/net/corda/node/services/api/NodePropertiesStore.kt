package net.corda.node.services.api

interface NodePropertiesStore {
    fun setFlowsDrainingModeEnabled(enabled: Boolean)

    fun isFlowsDrainingModeEnabled(): Boolean
}