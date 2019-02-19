package net.corda.deterministic.verifier

import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.AbstractAttachment
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

// A valid zip file with 1 entry.
val simpleZip = byteArrayOf(80, 75, 3, 4, 20, 0, 8, 8, 8, 0, 15, 113, 79, 78, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 4, 0, 47, 97, -2, -54, 0, 0, 75, 4, 0, 80, 75, 7, 8, 67, -66, -73, -24, 3, 0, 0, 0, 1, 0, 0, 0, 80, 75, 1, 2, 20, 0, 20, 0, 8, 8, 8, 0, 15, 113, 79, 78, 67, -66, -73, -24, 3, 0, 0, 0, 1, 0, 0, 0, 2, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 47, 97, -2, -54, 0, 0, 80, 75, 5, 6, 0, 0, 0, 0, 1, 0, 1, 0, 52, 0, 0, 0, 55, 0, 0, 0, 0, 0)

@CordaSerializable
class MockContractAttachment(
        override val id: SecureHash = SecureHash.zeroHash,
        val contract: ContractClassName,
        override val signerKeys: List<PublicKey> = emptyList(),
        override val signers: List<Party> = emptyList()
) : AbstractAttachment({ simpleZip }, "app")