/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.crypto

import net.corda.core.serialization.CordaSerializable

/**
 * A [SignableData] object is the packet actually signed.
 * It works as a wrapper over transaction id and signature metadata.
 * Note that when multi-transaction signing (signing a block of transactions) is used, the root of the Merkle tree
 * (having transaction IDs as leaves) is actually signed and thus [txId] refers to this root and not a specific transaction.
 *
 * @param txId transaction's id or root of multi-transaction Merkle tree in case of multi-transaction signing.
 * @param signatureMetadata meta data required.
 */
@CordaSerializable
data class SignableData(val txId: SecureHash, val signatureMetadata: SignatureMetadata)
