package com.r3corda.core.testing

import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator
import com.pholser.junit.quickcheck.generator.java.time.DurationGenerator
import com.pholser.junit.quickcheck.generator.java.time.InstantGenerator
import com.pholser.junit.quickcheck.generator.java.util.ArrayListGenerator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.*
import com.r3corda.core.serialization.OpaqueBytes
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Generators for quickcheck
 *
 * TODO Split this into several files
 */

fun <A> Generator<A>.generateList(random: SourceOfRandomness, status: GenerationStatus): List<A> {
    val arrayGenerator = ArrayListGenerator()
    arrayGenerator.addComponentGenerators(listOf(this))
    @Suppress("UNCHECKED_CAST")
    return arrayGenerator.generate(random, status) as List<A>
}


inline fun <reified B : Any> generator(
        crossinline generatorFunction: (SourceOfRandomness, GenerationStatus) -> B
): Generator<B> {
    return object : Generator<B>(B::class.java) {
        override fun generate(random: SourceOfRandomness, status: GenerationStatus) = generatorFunction(random, status)
    }
}

val privateKeyGenerator = generator { random, status ->
    entropyToKeyPair(random.nextBigInteger(32)).private
}
class PrivateKeyGenerator : Generator<PrivateKey>(PrivateKey::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus) = privateKeyGenerator.generate(random, status)
}

val publicKeyGenerator = generator { random, status ->
    entropyToKeyPair(random.nextBigInteger(32)).public
}
class PublicKeyGenerator : Generator<PublicKey>(PublicKey::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus) = publicKeyGenerator.generate(random, status)
}

val partyGenerator = generator { random, status ->
    Party(StringGenerator().generate(random, status), publicKeyGenerator.generate(random, status))
}

val partyAndReferenceGenerator = generator { random, status ->
    PartyAndReference(partyGenerator.generate(random, status), OpaqueBytes(random.nextBytes(16)))
}

val secureHashGenerator = generator { random, status ->
    SecureHash.Companion.sha256(random.nextBytes(16))
}

val stateRefGenerator = generator { random, status ->
    StateRef(SecureHash.Companion.sha256(random.nextBytes(16)), random.nextInt(0, 10))
}

fun <T : ContractState> transactionStateGenerator(stateGenerator: Generator<T>) = generator { random, status ->
    TransactionState(stateGenerator.generate(random, status), partyGenerator.generate(random, status))
}

fun <T> issuedGenerator(productGenerator: Generator<T>) = generator { random, status ->
    Issued(partyAndReferenceGenerator.generate(random, status), productGenerator.generate(random, status))
}

fun <T> amountGenerator(tokenGenerator: Generator<T>) = generator { random, status ->
    Amount(random.nextLong(0, 1000000), tokenGenerator.generate(random, status))
}

private val currencies = Currency.getAvailableCurrencies().toList()
val currencyGenerator = generator { random, status ->
    currencies[random.nextInt(0, currencies.size - 1)]
}

val instantGenerator = generator { random, status ->
    Instant.ofEpochMilli(random.nextLong(0, 1000000))
}

val durationGenerator = generator { random, status ->
    Duration.ofMillis(random.nextLong(0, 1000000))
}

val timestampGenerator = generator { random, status ->
    Timestamp(instantGenerator.generate(random, status), durationGenerator.generate(random, status))
}

