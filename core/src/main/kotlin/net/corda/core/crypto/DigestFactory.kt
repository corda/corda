package net.corda.core.crypto

import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

sealed class Algorithm(val userFriendyName: String, val kClass: KClass<*>) {
    class SHA256d : Algorithm("SHA-256d", SHA256dService::class)
    class SHA256 : Algorithm("SHA-256", SHA256Service::class)
    class BLAKE2b256 : Algorithm("BLAKE2b256", BLAKE2b256Service::class)
    class BLAKE2s256 : Algorithm("BLAKE2s", BLAKE2sService::class)
}

interface DigestServiceFactory {
    fun getService(algorithm: Algorithm): DigestService
}

object DefaultDigestServiceFactory : DigestServiceFactory {
    private val servicesCache = mutableMapOf<Algorithm, DigestService>()

    override fun getService(algorithm: Algorithm): DigestService {
        return servicesCache.getOrPut(algorithm) { algorithm.kClass.createInstance() as DigestService }
    }
}