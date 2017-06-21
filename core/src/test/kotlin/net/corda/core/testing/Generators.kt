package net.corda.core.testing

import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.generator.java.util.ArrayListGenerator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.serialization.OpaqueBytes
import net.corda.testing.getTestX509Name
import org.bouncycastle.asn1.x500.X500Name
import java.nio.ByteBuffer
import java.nio.charset.Charset
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

class PrivateKeyGenerator : Generator<PrivateKey>(PrivateKey::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): PrivateKey {
        return entropyToKeyPair(random.nextBigInteger(32)).private
    }
}

// TODO add CompositeKeyGenerator that actually does something useful.
class PublicKeyGenerator : Generator<PublicKey>(PublicKey::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): PublicKey {
        return entropyToKeyPair(random.nextBigInteger(32)).public
    }
}

class AnonymousPartyGenerator : Generator<AnonymousParty>(AnonymousParty::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): AnonymousParty {
        return AnonymousParty(PublicKeyGenerator().generate(random, status))
    }
}

class PartyGenerator : Generator<Party>(Party::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Party {
        return Party(X500NameGenerator().generate(random, status), PublicKeyGenerator().generate(random, status))
    }
}

class PartyAndReferenceGenerator : Generator<PartyAndReference>(PartyAndReference::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): PartyAndReference {
        return PartyAndReference(AnonymousPartyGenerator().generate(random, status), OpaqueBytes(random.nextBytes(16)))
    }
}

class SecureHashGenerator : Generator<SecureHash>(SecureHash::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): SecureHash {
        return SecureHash.sha256(random.nextBytes(16))
    }
}

class StateRefGenerator : Generator<StateRef>(StateRef::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): StateRef {
        return StateRef(SecureHash.sha256(random.nextBytes(16)), random.nextInt(0, 10))
    }
}

@Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST")
class TransactionStateGenerator<T : ContractState>(val stateGenerator: Generator<T>) : Generator<TransactionState<T>>(TransactionState::class.java as Class<TransactionState<T>>) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): TransactionState<T> {
        return TransactionState(stateGenerator.generate(random, status), PartyGenerator().generate(random, status))
    }
}

@Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST")
class IssuedGenerator<T : Any>(val productGenerator: Generator<T>) : Generator<Issued<T>>(Issued::class.java as Class<Issued<T>>) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Issued<T> {
        return Issued(PartyAndReferenceGenerator().generate(random, status), productGenerator.generate(random, status))
    }
}

@Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST")
class AmountGenerator<T : Any>(val tokenGenerator: Generator<T>) : Generator<Amount<T>>(Amount::class.java as Class<Amount<T>>) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Amount<T> {
        return Amount(random.nextLong(0, 1000000), tokenGenerator.generate(random, status))
    }
}

class CurrencyGenerator : Generator<Currency>(Currency::class.java) {
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

class TimeWindowGenerator : Generator<TimeWindow>(TimeWindow::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): TimeWindow {
        return TimeWindow.withTolerance(InstantGenerator().generate(random, status), DurationGenerator().generate(random, status))
    }
}

class X500NameGenerator : Generator<X500Name>(X500Name::class.java) {
    companion object {
        private val charset = Charset.forName("US-ASCII")
        private val asciiA = charset.encode("A")[0]
        private val asciia = charset.encode("a")[0]
    }

    /**
     * Append something that looks a bit like a proper noun to the string builder.
     */
    private fun appendProperNoun(builder: StringBuilder, random: SourceOfRandomness) : StringBuilder {
        val length = random.nextByte(1, 8)
        val encoded = ByteBuffer.allocate(length.toInt())
        encoded.put((random.nextByte(0, 25) + asciiA).toByte())
        for (charIdx in 1..length - 1) {
            encoded.put((random.nextByte(0, 25) + asciia).toByte())
        }
        return builder.append(charset.decode(encoded))
    }

    override fun generate(random: SourceOfRandomness, status: GenerationStatus): X500Name {
        val wordCount = random.nextByte(1, 3)
        val cn = StringBuilder()
        for (word in 0..wordCount) {
            appendProperNoun(cn, random).append(" ")
        }
        return getTestX509Name(cn.trim().toString())
    }
}
