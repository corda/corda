package com.r3corda.core.testing

import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator
import com.pholser.junit.quickcheck.generator.java.util.ArrayListGenerator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.ed25519Curve
import com.r3corda.core.serialization.OpaqueBytes
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
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

class PrivateKeyGenerator: Generator<EdDSAPrivateKey>(EdDSAPrivateKey::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): EdDSAPrivateKey {
        val seed = random.nextBytes(32)
        val privateKeySpec = EdDSAPrivateKeySpec(seed, ed25519Curve)
        return EdDSAPrivateKey(privateKeySpec)
    }
}

class PublicKeyGenerator: Generator<EdDSAPublicKey>(EdDSAPublicKey::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): EdDSAPublicKey {
        val privateKey = PrivateKeyGenerator().generate(random, status)
        return EdDSAPublicKey(EdDSAPublicKeySpec(privateKey.a, ed25519Curve))
    }
}

class PartyGenerator: Generator<Party>(Party::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Party {
        return Party(StringGenerator().generate(random, status), PublicKeyGenerator().generate(random, status))
    }
}

class PartyAndReferenceGenerator: Generator<PartyAndReference>(PartyAndReference::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): PartyAndReference {
        return PartyAndReference(PartyGenerator().generate(random, status), OpaqueBytes(random.nextBytes(16)))
    }
}

class SecureHashGenerator: Generator<SecureHash>(SecureHash::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): SecureHash {
        return SecureHash.Companion.sha256(random.nextBytes(16))
    }
}

class StateRefGenerator: Generator<StateRef>(StateRef::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): StateRef {
        return StateRef(SecureHash.Companion.sha256(random.nextBytes(16)), random.nextInt(0, 10))
    }
}

class TransactionStateGenerator<T : ContractState>(val stateGenerator: Generator<T>) : Generator<TransactionState<T>>(TransactionState::class.java as Class<TransactionState<T>>) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): TransactionState<T> {
        return TransactionState(stateGenerator.generate(random, status), PartyGenerator().generate(random, status))
    }
}

class IssuedGenerator<T>(val productGenerator: Generator<T>) : Generator<Issued<T>>(Issued::class.java as Class<Issued<T>>) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Issued<T> {
        return Issued(PartyAndReferenceGenerator().generate(random, status), productGenerator.generate(random, status))
    }
}

class AmountGenerator<T>(val tokenGenerator: Generator<T>) : Generator<Amount<T>>(Amount::class.java as Class<Amount<T>>) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Amount<T> {
        return Amount(random.nextLong(0, 1000000), tokenGenerator.generate(random, status))
    }
}

class CurrencyGenerator() : Generator<Currency>(Currency::class.java) {
    companion object {
        val currencies = Currency.getAvailableCurrencies().toList()
    }
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Currency {
        return currencies[random.nextInt(0, currencies.size - 1)]
    }
}

class InstantGenerator : Generator<Instant>(Instant::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Instant {
        return Instant.ofEpochMilli(random.nextLong(0, 1000000))
    }
}

class DurationGenerator : Generator<Duration>(Duration::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Duration {
        return Duration.ofMillis(random.nextLong(0, 1000000))
    }
}

class TimestampGenerator : Generator<Timestamp>(Timestamp::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Timestamp {
        return Timestamp(InstantGenerator().generate(random, status), DurationGenerator().generate(random, status))
    }
}

