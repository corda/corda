package net.corda.deterministic.transactions

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.transactions.TransactionWithSignatures
import org.junit.Test
import java.security.PublicKey

class TransactionWithSignaturesTest {
    @Test
    fun txWithSigs() {
        val tx = object : TransactionWithSignatures {
            override val id: SecureHash
                get() = SecureHash.zeroHash
            override val requiredSigningKeys: Set<PublicKey>
                get() = emptySet()
            override val sigs: List<TransactionSignature>
                get() = emptyList()

            override fun getKeyDescriptions(keys: Set<PublicKey>): List<String> {
                return emptyList()
            }
        }
        tx.verifyRequiredSignatures()
        tx.checkSignaturesAreValid()
        tx.getMissingSigners()
        tx.verifySignaturesExcept()
        tx.verifySignaturesExcept(emptySet())
    }
}