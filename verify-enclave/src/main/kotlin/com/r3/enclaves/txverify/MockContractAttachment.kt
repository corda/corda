package com.r3.enclaves.txverify

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.io.ByteArrayInputStream

@CordaSerializable
class MockContractAttachment(override val id: SecureHash = SecureHash.zeroHash, val contract: ContractClassName, override val signers: List<Party> = ArrayList()) : Attachment {
    override fun open() = ByteArrayInputStream(id.bytes)
}