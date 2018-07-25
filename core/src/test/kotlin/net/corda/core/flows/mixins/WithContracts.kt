package net.corda.core.flows.mixins

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UpgradedContract
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.ContractUpgradeFlow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.node.internal.StartedNode
import net.corda.testing.contracts.DummyContract
import net.corda.testing.node.internal.TestStartedNode
import kotlin.reflect.KClass

/**
 * Mix this interface into a test class to get useful generator and operation functions for working with dummy contracts
 */
interface WithContracts : WithMockNet {

    //region Generators
    fun createDummyContract(owner: PartyAndReference, magicNumber: Int = 0, vararg others: PartyAndReference) =
            DummyContract.generateInitial(
                    magicNumber,
                    mockNet.defaultNotaryIdentity,
                    owner,
                    *others)
    //region

    //region Operations
    fun TestStartedNode.signDummyContract(owner: PartyAndReference, magicNumber: Int = 0, vararg others: PartyAndReference) =
            services.signDummyContract(owner, magicNumber, *others).andRunNetwork()

    fun ServiceHub.signDummyContract(owner: PartyAndReference, magicNumber: Int = 0, vararg others: PartyAndReference) =
            signInitialTransaction(createDummyContract(owner, magicNumber, *others))

    fun TestStartedNode.collectSignatures(ptx: SignedTransaction) =
            startFlowAndRunNetwork(CollectSignaturesFlow(ptx, emptySet()))

    fun TestStartedNode.addSignatureTo(ptx: SignedTransaction) =
            services.addSignature(ptx).andRunNetwork()

    fun <T : UpgradedContract<*, *>>
            TestStartedNode.initiateContractUpgrade(tx: SignedTransaction, toClass: KClass<T>) =
            initiateContractUpgrade(tx.tx.outRef(0), toClass)

    fun <S : ContractState, T : UpgradedContract<S, *>>
            TestStartedNode.initiateContractUpgrade(stateAndRef: StateAndRef<S>, toClass: KClass<T>) =
            startFlowAndRunNetwork(ContractUpgradeFlow.Initiate(stateAndRef, toClass.java))

    fun <T : UpgradedContract<*, *>> TestStartedNode.authoriseContractUpgrade(
        tx: SignedTransaction, toClass: KClass<T>) =
        startFlow(
            ContractUpgradeFlow.Authorise(tx.tx.outRef<ContractState>(0), toClass.java)
        )

    fun TestStartedNode.deauthoriseContractUpgrade(tx: SignedTransaction) = startFlow(
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