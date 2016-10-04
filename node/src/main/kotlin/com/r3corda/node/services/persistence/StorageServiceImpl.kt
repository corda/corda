package com.r3corda.node.services.persistence

import com.r3corda.core.node.services.AttachmentStorage
import com.r3corda.core.node.services.StateMachineRecordedTransactionMappingStorage
import com.r3corda.core.node.services.TransactionStorage
import com.r3corda.core.node.services.TxWritableStorageService
import com.r3corda.core.serialization.SingletonSerializeAsToken

open class StorageServiceImpl(override val attachments: AttachmentStorage,
                              override val validatedTransactions: TransactionStorage,
                              override val stateMachineRecordedTransactionMapping: StateMachineRecordedTransactionMappingStorage)
: SingletonSerializeAsToken(), TxWritableStorageService
