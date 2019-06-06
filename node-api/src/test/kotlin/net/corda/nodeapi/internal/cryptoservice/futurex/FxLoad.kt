package net.corda.nodeapi.internal.cryptoservice.futurex

import fx.security.pkcs11.SunPKCS11
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.ECDSA_SECP256R1_SHA256
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

fun main(args: Array<String>) {
    val provider = SunPKCS11()
    val keyStore = KeyStore.getInstance(FutureXCryptoService.KEYSTORE_TYPE, provider)
    val config = FutureXCryptoService.FutureXConfiguration("username;password")
    val cs = FutureXCryptoService(keyStore, provider) { config }
    val counter = AtomicInteger(0)
    val elapsedTotal = AtomicLong(0)
    val alias = "fxtest"
    val key = cs.generateKeyPair(alias, ECDSA_SECP256R1_SHA256)

    val data = "arrrrrr3".repeat(1024 * 8).toByteArray()

    provider.logout()
    val starting = System.currentTimeMillis()
    println("starting ...")
    val service = Executors.newFixedThreadPool(160)
    (1..10000).map {
        service.submit {
            val start = System.currentTimeMillis()
            val signed = cs.sign(alias, data)
            val end = System.currentTimeMillis()
            println(counter.getAndIncrement())
            elapsedTotal.addAndGet(end - start)
            println(Crypto.doVerify(key, signed, data))
        }
    }.forEach { it.get() }
    service.shutdown()

    println("ops/s: ${counter.get() / (((System.currentTimeMillis() - starting) / 1000))}")
    println("total ops: ${counter.get()}")
    println("elapsed: ${elapsedTotal.get()/ 1000}")
    println("elapsed/ops: ${elapsedTotal.get() / counter.get()}")
}
