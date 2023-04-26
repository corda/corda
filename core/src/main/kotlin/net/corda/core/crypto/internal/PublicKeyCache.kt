package net.corda.core.crypto.internal

import net.corda.core.utilities.ByteSequence
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

object PublicKeyCache {
    private val collectedWeakPubKeys = ReferenceQueue<PublicKey>()

    private class WeakPubKey(key: PublicKey, val bytes: ByteSequence? = null) : WeakReference<PublicKey>(key, collectedWeakPubKeys) {
        private val hashCode = key.hashCode()

        override fun hashCode(): Int = hashCode
        override fun equals(other: Any?): Boolean {
            if(this === other) return true
            if(other !is WeakPubKey) return false
            if(this.hashCode != other.hashCode) return false
            val thisGet = this.get()
            val otherGet = other.get()
            if(thisGet == null || otherGet == null) return false
            return thisGet == otherGet
        }
    }

    private val pubKeyToBytes = ConcurrentHashMap<WeakPubKey, ByteSequence>()
    private val bytesToPubKey = ConcurrentHashMap<ByteSequence, WeakPubKey>()

    private fun reapCollectedWeakPubKeys() {
        while(true) {
            val weakPubKey = (collectedWeakPubKeys.poll() as? WeakPubKey) ?: break
            pubKeyToBytes.remove(weakPubKey)
            bytesToPubKey.remove(weakPubKey.bytes!!)
        }
    }

    fun bytesForCachedPublicKey(key: PublicKey): ByteSequence? {
        val weakPubKey = WeakPubKey(key)
        return pubKeyToBytes[weakPubKey]
    }

    fun publicKeyForCachedBytes(bytes: ByteSequence): PublicKey? {
        return bytesToPubKey[bytes]?.get()
    }

    fun cachePublicKey(key: PublicKey): PublicKey {
        reapCollectedWeakPubKeys()
        val weakPubKey = WeakPubKey(key, ByteSequence.of(key.encoded))
        pubKeyToBytes.putIfAbsent(weakPubKey, weakPubKey.bytes!!)
        bytesToPubKey.putIfAbsent(weakPubKey.bytes, weakPubKey)
        return key
    }
}