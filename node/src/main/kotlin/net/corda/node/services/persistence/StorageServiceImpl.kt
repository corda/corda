package net.corda.node.services.persistence

import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.StateMachineRecordedTransactionMappingStorage
import net.corda.core.node.services.TransactionStorage
import net.corda.core.node.services.TxWritableStorageService
import net.corda.core.serialization.SingletonSerializeAsToken

open class StorageServiceImpl(override val attachments: AttachmentStorage,
                              override val validatedTransactions: TransactionStorage,
                              override val stateMachineRecordedTransactionMapping: StateMachineRecordedTransactionMappingStorage)
    : SingletonSerializeAsToken(), TxWritableStorageService
