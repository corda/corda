package net.corda.node.services.api

import rx.Observable

interface NodePropertiesStore {

    val flowsDrainingMode: FlowsDrainingModeOperations

    interface FlowsDrainingModeOperations {

        fun setEnabled(enabled: Boolean)

        fun isEnabled(): Boolean

        val values: Observable<Pair<Boolean, Boolean>>
    }
}