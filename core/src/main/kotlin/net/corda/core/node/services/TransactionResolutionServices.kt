package net.corda.core.node.services

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.componentHash
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import java.lang.IllegalStateException
import java.security.DigestInputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.X509Certificate

interface TransactionResolutionServices {
    val resolveIdentity: (PublicKey) -> Party?
    val resolveAttachment: (SecureHash) -> Attachment?
    val resolveStateRefAsSerialized: (StateRef) -> SerializedBytes<TransactionState<ContractState>>?
    val resolveNetworkParameters: (SecureHash?) -> NetworkParameters?
}