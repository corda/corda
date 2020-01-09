package net.corda.node.services.api

import rx.Observable

interface NodePropertiesStore {

    val flowsDrainingMode: FlowsDrainingModeOperations

    interface FlowsDrainingModeOperations {

        fun setEnabled(enabled: Boolean, propagateChange: Boolean = true)

        fun isEnabled(): Boolean

        val values: Observable<Pair<Boolean, Boolean>>
    }
}