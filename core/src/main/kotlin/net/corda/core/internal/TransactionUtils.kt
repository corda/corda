package net.corda.core.internal

import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.Party
import net.corda.core.serialization.serialize
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import java.io.ByteArrayOutputStream

/** Constructs a [NotaryChangeWireTransaction]. */
class NotaryChangeTransactionBuilder(val inputs: List<StateRef>,
                                     val notary: Party,
                                     val newNotary: Party) {
    fun build(): NotaryChangeWireTransaction {
        val components = listOf(inputs, notary, newNotary).map { it.serialize() }
        return NotaryChangeWireTransaction(components)
    }
}

/** Constructs a [ContractUpgradeWireTransaction]. */
class ContractUpgradeTransactionBuilder(
        val inputs: List<StateRef>,
        val notary: Party,
        val legacyContractAttachmentId: SecureHash,
        val upgradedContractClassName: ContractClassName,
        val upgradedContractAttachmentId: SecureHash,
        val privacySalt: PrivacySalt = PrivacySalt()) {
    fun build(): ContractUpgradeWireTransaction {
        val components = listOf(inputs, notary, legacyContractAttachmentId, upgradedContractClassName, upgradedContractAttachmentId).map { it.serialize() }
        return ContractUpgradeWireTransaction(components, privacySalt)
    }
}

/** Concatenates the hash components into a single [ByteArray] and returns its hash. */
fun combinedHash(components: Iterable<SecureHash>): SecureHash {
    val stream = ByteArrayOutputStream()
    components.forEach {
        stream.write(it.bytes)
    }
    return stream.toByteArray().sha256()
}