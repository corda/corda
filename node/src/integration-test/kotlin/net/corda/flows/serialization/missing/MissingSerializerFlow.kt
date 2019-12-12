package net.corda.flows.serialization.missing

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.serialization.missing.contract.CustomData
import net.corda.contracts.serialization.missing.contract.MissingSerializerContract.CustomDataState
import net.corda.contracts.serialization.missing.contract.MissingSerializerContract.Operate
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.createComponentGroups
import net.corda.core.internal.requiredContractClassName
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction

@InitiatingFlow
@StartableByRPC
class MissingSerializerFlow(private val value: Long) : FlowLogic<SecureHash>() {
    @Suspendable
    override fun call(): SecureHash {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val legalIdentityKey = serviceHub.myInfo.legalIdentitiesAndCerts.first().owningKey

        val customDataState = CustomDataState(ourIdentity, CustomData(value))
        val wtx = WireTransaction(createComponentGroups(
            inputs = emptyList(),
            outputs = listOf(TransactionState(
                data = customDataState,
                notary = notary,
                constraint = AlwaysAcceptAttachmentConstraint
            )),
            notary = notary,
            commands = listOf(Command(Operate(), ourIdentity.owningKey)),
            attachments = serviceHub.attachments.getLatestContractAttachments(customDataState.requiredContractClassName!!),
            timeWindow = null,
            references = emptyList(),
            networkParametersHash = null
        ))
        val signatureMetadata = SignatureMetadata(
            platformVersion = serviceHub.myInfo.platformVersion,
            schemeNumberID = Crypto.findSignatureScheme(legalIdentityKey).schemeNumberID
        )
        val signableData = SignableData(wtx.id, signatureMetadata)
        val sig = serviceHub.keyManagementService.sign(signableData, legalIdentityKey)
        return with(SignedTransaction(wtx, listOf(sig))) {
            verify(serviceHub, checkSufficientSignatures = false)
            id
        }
    }
}
