package net.corda.core.internal

import net.corda.core.contracts.ContractState

@Suppress("unused")
object ContractStateClassCache {
    @Suppress("UNUSED_PARAMETER")
    fun contractClassName(key: Class<ContractState>): String? {
        return null
    }

    @Suppress("UNUSED_PARAMETER")
    fun cacheContractClassName(key: Class<ContractState>, contractClassName: String?): String? {
        return contractClassName
    }
}