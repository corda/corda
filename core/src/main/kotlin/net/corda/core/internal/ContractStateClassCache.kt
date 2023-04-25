package net.corda.core.internal

import com.google.common.collect.MapMaker
import net.corda.core.contracts.ContractState

object ContractStateClassCache {
    private val classToString = MapMaker().weakKeys().makeMap<Class<*>, String>()

    fun contractClassName(key: Class<ContractState>): String? {
        return classToString[key]
    }

    fun cacheContractClassName(key: Class<ContractState>, contractClassName: String?): String? {
        if (contractClassName == null) return null
        classToString.putIfAbsent(key, contractClassName)
        return contractClassName
    }
}