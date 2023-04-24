package net.corda.core.internal

import net.corda.core.contracts.ContractState
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

object ContractStateClassCache {
    private val collectedWeakClassKeys = ReferenceQueue<Class<*>>()

    private class WeakClassKey(key: Class<*>) : WeakReference<Class<*>>(key, collectedWeakClassKeys) {
        private val hashCode = key.hashCode()

        override fun hashCode(): Int = hashCode
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WeakClassKey) return false
            if (this.hashCode != other.hashCode) return false
            val thisGet = this.get()
            val otherGet = other.get()
            if (thisGet == null || otherGet == null) return false
            return thisGet == otherGet
        }
    }

    private val classToString = ConcurrentHashMap<WeakClassKey, String>()

    private fun reapCollectedWeakPubKeys() {
        while (true) {
            val weakClassKey = (collectedWeakClassKeys.poll() as? WeakClassKey) ?: break
            classToString.remove(weakClassKey)
        }
    }

    fun contractClassName(key: Class<ContractState>): String? {
        val weakClassKey = WeakClassKey(key)
        return classToString[weakClassKey]
    }

    fun cacheContractClassName(key: Class<ContractState>, contractClassName: String?): String? {
        if (contractClassName == null) return null
        reapCollectedWeakPubKeys()
        val weakClassKey = WeakClassKey(key)
        classToString.putIfAbsent(weakClassKey, contractClassName)
        return contractClassName
    }
}