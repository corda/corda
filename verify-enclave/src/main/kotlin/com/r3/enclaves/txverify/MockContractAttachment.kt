/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
    override val size = id.size
}