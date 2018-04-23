/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.api

import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.VaultService
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.WireTransaction

interface VaultServiceInternal : VaultService {
    /**
     * Splits the provided [txns] into batches of [WireTransaction] and [NotaryChangeWireTransaction].
     * This is required because the batches get aggregated into single updates, and we want to be able to
     * indicate whether an update consists entirely of regular or notary change transactions, which may require
     * different processing logic.
     */
    fun notifyAll(statesToRecord: StatesToRecord, txns: Iterable<CoreTransaction>)

    /** Same as notifyAll but with a single transaction. */
    fun notify(statesToRecord: StatesToRecord, tx: CoreTransaction) = notifyAll(statesToRecord, listOf(tx))
}
