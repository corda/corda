package com.r3corda.node.services.persistence

import com.r3corda.core.crypto.Party
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.AttachmentStorage
import com.r3corda.core.node.services.StorageService
import com.r3corda.core.utilities.RecordingMap
import org.slf4j.LoggerFactory
import java.security.KeyPair
import java.util.*

open class StorageServiceImpl(override val attachments: AttachmentStorage,
                              override val myLegalIdentityKey: KeyPair,
                              override val myLegalIdentity: Party = Party("Unit test party", myLegalIdentityKey.public),
                              // This parameter is for unit tests that want to observe operation details.
                              val recordingAs: (String) -> String = { tableName -> "" })
: StorageService {
    protected val tables = HashMap<String, MutableMap<*, *>>()

    private fun <K, V> getMapOriginal(tableName: String): MutableMap<K, V> {
        synchronized(tables) {
            @Suppress("UNCHECKED_CAST")
            return tables.getOrPut(tableName) {
                recorderWrap(Collections.synchronizedMap(HashMap<K, V>()), tableName)
            } as MutableMap<K, V>
        }
    }

    private fun <K, V> recorderWrap(map: MutableMap<K, V>, tableName: String): MutableMap<K, V> {
        if (recordingAs(tableName) != "")
            return RecordingMap(map, LoggerFactory.getLogger("recordingmap.${recordingAs(tableName)}"))
        else
            return map
    }

    override val validatedTransactions: MutableMap<SecureHash, SignedTransaction>
        get() = getMapOriginal("validated-transactions")

}