package net.corda.node.services.persistence

import net.corda.core.node.services.*
import net.corda.core.serialization.SingletonSerializeAsToken

open class StorageServiceImpl(override val attachments: AttachmentStorage,
                              override val validatedTransactions: TransactionStorage,
                              override val stateMachineRecordedTransactionMapping: StateMachineRecordedTransactionMappingStorage)
    : SingletonSerializeAsToken(), TxWritableStorageService {
    lateinit override var uploaders: List<FileUploader>

    fun initUploaders(uploadersList: List<FileUploader>) {
        uploaders = uploadersList
    }
}
