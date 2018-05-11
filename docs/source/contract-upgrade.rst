.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Upgrading contracts
===================

While every care is taken in development of contract code, inevitably upgrades will be required to fix bugs (in either
design or implementation). Upgrades can involve a substitution of one version of the contract code for another or
changing to a different contract that understands how to migrate the existing state objects. When state objects are
added as outputs to transactions, they are linked to the contract code they are intended for via the
``StateAndContract`` type. Changing a state's contract only requires substituting one ``ContractClassName`` for another.

Workflow
--------
Here's the workflow for contract upgrades:

1. Banks A and B negotiate a trade, off-platform

2. Banks A and B execute a flow to construct a state object representing the trade, using contract X, and include it in
   a transaction (which is then signed and sent to the consensus service)

3. Time passes

4. The developer of contract X discovers a bug in the contract code, and releases a new version, contract Y. The
   developer will then notify all existing users (e.g. via a mailing list or CorDapp store) to stop their nodes from
   issuing further states with contract X

5. Banks A and B review the new contract via standard change control processes and identify the contract states they
   agree to upgrade (they may decide not to upgrade some contract states as these might be needed for some other
   obligation contract)

6. Banks A and B instruct their Corda nodes (via RPC) to be willing to upgrade state objects with contract X to state
   objects with contract Y using the agreed upgrade path

7. One of the parties (the ``Initiator``) initiates a flow to replace state objects referring to contract X with new
   state objects referring to contract Y

8. A proposed transaction (the ``Proposal``), with the old states as input and the reissued states as outputs, is
   created and signed with the node's private key

9. The ``Initiator`` node sends the proposed transaction, along with details of the new contract upgrade path that it
   is proposing, to all participants of the state object

10. Each counterparty (the ``Acceptor`` s) verifies the proposal, signs or rejects the state reissuance accordingly, and
    sends a signature or rejection notification back to the initiating node

11. If signatures are received from all parties, the ``Initiator`` assembles the complete signed transaction and sends
    it to the notary

Authorising an upgrade
----------------------
Each of the participants in the state for which the contract is being upgraded will have to instruct their node that
they agree to the upgrade before the upgrade can take place. The ``ContractUpgradeFlow`` is used to manage the
authorisation process. Each node administrator can use RPC to trigger either an ``Authorise`` or a ``Deauthorise`` flow
for the state in question.

.. literalinclude:: ../../core/src/main/kotlin/net/corda/core/flows/ContractUpgradeFlow.kt
    :language: kotlin
    :start-after: DOCSTART 1
    :end-before: DOCEND 1
    :dedent: 4

.. literalinclude:: ../../core/src/main/kotlin/net/corda/core/flows/ContractUpgradeFlow.kt
    :language: kotlin
    :start-after: DOCSTART 2
    :end-before: DOCEND 2
    :dedent: 4

Proposing an upgrade
--------------------
After all parties have authorised the contract upgrade for the state, one of the contract participants can initiate the
upgrade process by triggering the ``ContractUpgradeFlow.Initiate`` flow. ``Initiate`` creates a transaction including
the old state and the updated state, and sends it to each of the participants. Each participant will verify the
transaction, create a signature over it, and send the signature back to the initiator. Once all the signatures are
collected, the transaction will be notarised and persisted to every participant's vault.

Example
-------
Suppose Bank A has entered into an agreement with Bank B which is represented by the state object
``DummyContractState`` and governed by the contract code ``DummyContract``. A few days after the exchange of contracts,
the developer of the contract code discovers a bug in the contract code.

Bank A and Bank B decide to upgrade the contract to ``DummyContractV2``:

1. The developer creates a new contract ``DummyContractV2`` extending the ``UpgradedContract`` class, and a new state
   object ``DummyContractV2.State`` referencing the new contract.

.. literalinclude:: /../../testing/test-utils/src/main/kotlin/net/corda/testing/contracts/DummyContractV2.kt
    :language: kotlin
    :start-after: DOCSTART 1
    :end-before: DOCEND 1

2. Bank A instructs its node to accept the contract upgrade to ``DummyContractV2`` for the contract state.

.. container:: codeset

   .. sourcecode:: kotlin
   
      val rpcClient : CordaRPCClient = << Bank A's Corda RPC Client >>
      val rpcA = rpcClient.proxy()
      rpcA.startFlow(ContractUpgradeFlow.Authorise(<<StateAndRef of the contract state>>, DummyContractV2::class.java))

3. Bank B initiates the upgrade flow, which will send an upgrade proposal to all contract participants. Each of the
   participants of the contract state will sign and return the contract state upgrade proposal once they have validated
   and agreed with the upgrade. The upgraded transaction will be recorded in every participant's node by the flow.

.. container:: codeset

   .. sourcecode:: kotlin
      
      val rpcClient : CordaRPCClient = << Bank B's Corda RPC Client >>
      val rpcB = rpcClient.proxy()
      rpcB.startFlow({ stateAndRef, upgrade -> ContractUpgradeFlow(stateAndRef, upgrade) },
          <<StateAndRef of the contract state>>,
          DummyContractV2::class.java)
          
.. note:: See ``ContractUpgradeFlowTest`` for more detailed code examples.



