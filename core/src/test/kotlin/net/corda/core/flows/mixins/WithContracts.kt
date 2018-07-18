package net.corda.core.flows.mixins

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UpgradedContract
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.ContractUpgradeFlow
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.node.internal.StartedNode
import net.corda.testing.contracts.DummyContract
import kotlin.reflect.KClass

/**
 * Mix this interface into a test class to get useful generator and operation functions for working with dummy contracts
 */
interface WithContracts : WithMockNet {

    val magicNumber: Int

    //region Generators
    fun createDummyContract(owner: PartyAndReference, vararg others: PartyAndReference) =
            DummyContract.generateInitial(
                    magicNumber,
                    mockNet.defaultNotaryIdentity,
                    owner,
                    *others)
    //region

    //region Operations
    fun StartedNode<*>.createConfidentialIdentity(party: Party) = database.transaction {
        services.keyManagementService.freshKeyAndCert(
                services.myInfo.legalIdentitiesAndCerts.single { it.name == party.name },
                false)
    }

    fun StartedNode<*>.verifyAndRegister(identity: PartyAndCertificate) = database.transaction {
        services.identityService.verifyAndRegisterIdentity(identity)
    }

    fun StartedNode<*>.signDummyContract(owner: PartyAndReference, vararg others: PartyAndReference) =
            services.signDummyContract(owner, *others).andRunNetwork()

    fun ServiceHub.signDummyContract(owner: PartyAndReference, vararg others: PartyAndReference) =
            signInitialTransaction(createDummyContract(owner, *others))

    fun StartedNode<*>.collectSignatures(ptx: SignedTransaction) =
            startFlowAndRunNetwork(CollectSignaturesFlow(ptx, emptySet()))

    fun StartedNode<*>.addSignatureTo(ptx: SignedTransaction) =
            services.addSignature(ptx).andRunNetwork()

    fun <T : UpgradedContract<*, *>>
            StartedNode<*>.initiateContractUpgrade(tx: SignedTransaction, toClass: KClass<T>) =
            initiateContractUpgrade(tx.tx.outRef(0), toClass)

    fun <S : ContractState, T : UpgradedContract<S, *>>
            StartedNode<*>.initiateContractUpgrade(stateAndRef: StateAndRef<S>, toClass: KClass<T>) =
            startFlowAndRunNetwork(ContractUpgradeFlow.Initiate(stateAndRef, toClass.java))

    fun <T : UpgradedContract<*, *>> StartedNode<*>.authoriseContractUpgrade(
        tx: SignedTransaction, toClass: KClass<T>) =
        startFlow(
            ContractUpgradeFlow.Authorise(tx.tx.outRef<ContractState>(0), toClass.java)
        )

    fun StartedNode<*>.deauthoriseContractUpgrade(tx: SignedTransaction) = startFlow(
        ContractUpgradeFlow.Deauthorise(tx.tx.outRef<ContractState>(0).ref)
    )

    // RPC versions of the above
    fun <S : ContractState, T : UpgradedContract<S, *>> CordaRPCOps.initiateContractUpgrade(
        tx: SignedTransaction, toClass: KClass<T>) =
        startFlow(
            { stateAndRef, upgrade -> ContractUpgradeFlow.Initiate(stateAndRef, upgrade) },
            tx.tx.outRef<S>(0),
            toClass.java)
        .andRunNetwork()

    fun <S : ContractState, T : UpgradedContract<S, *>> CordaRPCOps.authoriseContractUpgrade(
        tx: SignedTransaction, toClass: KClass<T>) =
        startFlow(
            { stateAndRef, upgrade -> ContractUpgradeFlow.Authorise(stateAndRef, upgrade) },
            tx.tx.outRef<S>(0),
            toClass.java)

    fun CordaRPCOps.deauthoriseContractUpgrade(tx: SignedTransaction) =
        startFlow(
            { stateRef -> ContractUpgradeFlow.Deauthorise(stateRef) },
            tx.tx.outRef<ContractState>(0).ref)
    //region
}