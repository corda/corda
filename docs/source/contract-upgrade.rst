.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Upgrading contracts
===================

While every care is taken in development of contract code, 
inevitably upgrades will be required to fix bugs (in either design or implementation). 
Upgrades can involve a substitution of one version of the contract code for another or changing 
to a different contract that understands how to migrate the existing state objects. State objects 
refer to the contract code (by hash) they are intended for, and even where state objects can be used 
with different contract versions, changing this value requires issuing a new state object.

Workflow
--------

Here's the workflow for contract upgrades:

1. Two banks, A and B negotiate a trade, off-platform

2. Banks A and B execute a protocol to construct a state object representing the trade, using contract X, and include it in a transaction (which is then signed and sent to the Uniqueness Service).

3. Time passes.

4. The developer of contract X discovers a bug in the contract code, and releases a new version, contract Y. 
And notify the users (e.g. via a mailing list or CorDapp store).
At this point of time all nodes should stop issuing states of contract X.

5. Banks A and B review the new contract via standard change control processes and identify the contract states they agreed to upgrade, they can decide not to upgrade some contract states as they might be needed for other obligation contract.

6. Banks A and B instruct their Corda nodes (via RPC) to be willing to upgrade state objects of contract X, to state objects for contract Y using agreed upgrade path.

7. One of the parties ``Instigator`` initiates an upgrade of state objects referring to contract X, to a new state object referring to contract Y.

8. A proposed transaction ``Proposal``, taking in the old state and outputting the reissued version, is created and signed with the node's private key.

9. The node ``Instigator`` sends the proposed transaction, along with details of the new contract upgrade path it's proposing, to all participants of the state object.

10. Each counterparty ``Acceptor`` verifies the proposal, signs or rejects the state reissuance accordingly, and sends a signature or rejection notification back to the initiating node.

11. If signatures are received from all parties, the initiating node assembles the complete signed transaction and sends it to the consensus service.


Authorising upgrade
-------------------

Each of the participants in the upgrading contract will have to instruct their node that they are willing to upgrade the state object before the upgrade.
Currently the vault service is used to manage the authorisation records. The administrator can use RPC to perform such instructions.

.. container:: codeset

   .. sourcecode:: kotlin
   
      /**
       * Authorise a contract state upgrade.
       * This will store the upgrade authorisation in the vault, and will be queried by [ContractUpgradeFlow] during contract upgrade process.
       * Invoking this method indicate the node is willing to upgrade the [state] using the [upgradedContractClass].
       * This method will NOT initiate the upgrade process.
       */
      fun authoriseContractUpgrade(state: StateAndRef<*>, upgradedContractClass: Class<UpgradedContract<*, *>>)   
      /**
       * Authorise a contract state upgrade.
       * This will remove the upgrade authorisation from the vault.
       */
      fun deauthoriseContractUpgrade(state: StateAndRef<*>)



Proposing an upgrade
--------------------

After all parties have registered the intention of upgrading the contract state, one of the contract participant can initiate the upgrade process by running the contract upgrade flow.
The Instigator will create a new state and sent to each participant for signatures, each of the participants (Acceptor) will verify and sign the proposal and returns to the instigator.
The transaction will be notarised and persisted once every participant verified and signed the upgrade proposal.

Examples
--------

Lets assume Bank A has entered into an agreement with Bank B, and the contract is translated into contract code ``DummyContract`` with state object ``DummyContractState``.

Few days after the exchange of contracts, the developer of the contract code discovered a bug/misrepresentation in the contract code. 
Bank A and Bank B decided to upgrade the contract to ``DummyContractV2``

1. Developer will create a new contract extending the ``UpgradedContract`` class, and a new state object ``DummyContractV2.State`` referencing the new contract.

.. literalinclude:: /../../test-utils/src/main/kotlin/net/corda/testing/contracts/DummyContractV2.kt
    :language: kotlin
    :start-after: DOCSTART 1
    :end-before: DOCEND 1

2. Bank A will instruct its node to accept the contract upgrade to ``DummyContractV2`` for the contract state.

.. container:: codeset

   .. sourcecode:: kotlin
   
      val rpcClient : CordaRPCClient = << Bank A's Corda RPC Client >>
      val rpcA = rpcClient.proxy()
      rpcA.authoriseContractUpgrade(<<StateAndRef of the contract state>>, DummyContractV2::class.java)

3. Bank B now initiate the upgrade Flow, this will send a upgrade proposal to all contract participants.
Each of the participants of the contract state will sign and return the contract state upgrade proposal once they have validated and agreed with the upgrade.
The upgraded transaction state will be recorded in every participant's node at the end of the flow.

.. container:: codeset

   .. sourcecode:: kotlin
      
      val rpcClient : CordaRPCClient = << Bank B's Corda RPC Client >>
      val rpcB = rpcClient.proxy()
      rpcB.startFlow({ stateAndRef, upgrade -> ContractUpgradeFlow(stateAndRef, upgrade) },
          <<StateAndRef of the contract state>>,
          DummyContractV2::class.java)
          
.. note:: See ``ContractUpgradeFlowTest.2 parties contract upgrade using RPC`` for more detailed code example.



