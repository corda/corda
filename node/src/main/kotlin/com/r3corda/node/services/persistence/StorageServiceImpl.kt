package com.r3corda.node.services.persistence

import com.r3corda.core.crypto.Party
import com.r3corda.core.node.services.AttachmentStorage
import com.r3corda.core.node.services.StorageService
import com.r3corda.core.node.services.TransactionStorage
import com.r3corda.core.node.services.TxWritableStorageService
import com.r3corda.core.serialization.SingletonSerializeAsToken
import java.security.KeyPair

open class StorageServiceImpl(override val attachments: AttachmentStorage,
                              override val validatedTransactions: TransactionStorage,
                              override val myLegalIdentityKey: KeyPair,
                              override val myLegalIdentity: Party = Party("Unit test party", myLegalIdentityKey.public))
: SingletonSerializeAsToken(), TxWritableStorageService
