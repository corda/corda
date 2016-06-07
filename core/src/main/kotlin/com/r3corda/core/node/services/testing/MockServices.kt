package com.r3corda.core.node.services.testing

import com.r3corda.core.contracts.Attachment
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.crypto.sha256
import com.r3corda.core.node.services.*
import com.r3corda.core.serialization.SingletonSerializeAsToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*
import java.util.jar.JarInputStream
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
class MockIdentityService(val identities: List<Party>) : IdentityService, SingletonSerializeAsToken() {
    private val keyToParties: Map<PublicKey, Party>
        get() = synchronized(identities) { identities.associateBy { it.owningKey } }
    private val nameToParties: Map<String, Party>
        get() = synchronized(identities) { identities.associateBy { it.name } }

    override fun registerIdentity(party: Party) { throw UnsupportedOperationException() }
    override fun partyFromKey(key: PublicKey): Party? = keyToParties[key]
    override fun partyFromName(name: String): Party? = nameToParties[name]
}


class MockKeyManagementService(vararg initialKeys: KeyPair) : SingletonSerializeAsToken(), KeyManagementService {
    override val keys: MutableMap<PublicKey, PrivateKey>

    init {
        keys = initialKeys.map { it.public to it.private }.toMap(HashMap())
    }

    val nextKeys = LinkedList<KeyPair>()

    override fun freshKey(): KeyPair {
        val k = nextKeys.poll() ?: generateKeyPair()
        keys[k.public] = k.private
        return k
    }
}

class MockAttachmentStorage : AttachmentStorage {
    val files = HashMap<SecureHash, ByteArray>()

    override fun openAttachment(id: SecureHash): Attachment? {
        val f = files[id] ?: return null
        return object : Attachment {
            override fun open(): InputStream = ByteArrayInputStream(f)
            override val id: SecureHash = id
        }
    }

    override fun importAttachment(jar: InputStream): SecureHash {
        // JIS makes read()/readBytes() return bytes of the current file, but we want to hash the entire container here.
        require(jar !is JarInputStream)

        val bytes = run {
            val s = ByteArrayOutputStream()
            jar.copyTo(s)
            s.close()
            s.toByteArray()
        }
        val sha256 = bytes.sha256()
        if (files.containsKey(sha256))
            throw FileAlreadyExistsException(File("!! MOCK FILE NAME"))
        files[sha256] = bytes
        return sha256
    }
}

class MockTransactionStorage : TransactionStorage {
    private val txns = HashMap<SecureHash, SignedTransaction>()
    override fun addTransaction(transaction: SignedTransaction) {
        txns[transaction.id] = transaction
    }
    override fun getTransaction(id: SecureHash): SignedTransaction? = txns[id]
}

@ThreadSafe
class MockStorageService(override val attachments: AttachmentStorage = MockAttachmentStorage(),
                         override val validatedTransactions: TransactionStorage = MockTransactionStorage(),
                         override val myLegalIdentityKey: KeyPair = generateKeyPair(),
                         override val myLegalIdentity: Party = Party("Unit test party", myLegalIdentityKey.public))
: SingletonSerializeAsToken(), StorageService
