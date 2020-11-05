package net.corda.core.crypto.internal

import net.corda.core.crypto.DigestAlgorithm
import java.lang.reflect.Constructor
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

sealed class DigestAlgorithmFactory {
    abstract fun create(): DigestAlgorithm
    abstract val algorithm: String

    private class MessageDigestFactory(override val algorithm: String) : DigestAlgorithmFactory() {
        override fun create(): DigestAlgorithm {
            try {
                val messageDigest = MessageDigest.getInstance(algorithm)
                return MessageDigestWrapper(messageDigest, algorithm)
            } catch (e: NoSuchAlgorithmException) {
                throw IllegalArgumentException("Unknown hash algorithm $algorithm")
            }
        }

        private class MessageDigestWrapper(val messageDigest: MessageDigest, override val algorithm: String) : DigestAlgorithm {
            override val digestLength = messageDigest.digestLength
            override fun digest(bytes: ByteArray): ByteArray = messageDigest.digest(bytes)
        }
    }

    private class CustomAlgorithmFactory(className: String) : DigestAlgorithmFactory() {
        val constructor: Constructor<out DigestAlgorithm> = javaClass
                .classLoader
                .loadClass(className)
                .asSubclass(DigestAlgorithm::class.java)
                .getConstructor()
        override val algorithm: String = constructor.newInstance().algorithm

        override fun create(): DigestAlgorithm {
            return constructor.newInstance()
        }
    }

    companion object {
        private const val SHA2_256 = "SHA-256"
        private val BANNED: Set<String> = Collections.unmodifiableSet(setOf("MD5", "MD2", "SHA-1"))
        private val sha256Factory = MessageDigestFactory(SHA2_256)
        private val factories = ConcurrentHashMap<String, DigestAlgorithmFactory>()

        private fun check(algorithm: String) {
            require(algorithm.toUpperCase() == algorithm) { "Hash algorithm name $this must be in the upper case" }
            require(algorithm !in BANNED) { "$algorithm is forbidden!" }
        }

        fun registerClass(className: String): String {
            val factory = CustomAlgorithmFactory(className)
            check(factory.algorithm)
            require(factory.algorithm != SHA2_256) { "Standard algorithm name is not allowed in $className" }
            factories.putIfAbsent(factory.algorithm, factory)
            return factory.algorithm
        }

        fun create(algorithm: String): DigestAlgorithm {
            check(algorithm)
            return when (algorithm) {
                SHA2_256 -> sha256Factory.create()
                else -> factories[algorithm]?.create()?: MessageDigestFactory(algorithm).create()
            }
        }
    }
}
