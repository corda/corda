package net.corda.deterministic.verifier

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.PublicKey

@CordaSerializable
class MockContractAttachment(
        override val id: SecureHash = SecureHash.zeroHash,
        val contract: ContractClassName,
        override val signerKeys: List<PublicKey> = emptyList(),
        override val signers: List<Party> = emptyList()
) : Attachment {
    override fun open(): InputStream = ByteArrayInputStream(id.bytes)
    override val size = id.size
}
