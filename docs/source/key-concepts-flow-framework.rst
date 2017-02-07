
Flow framework
--------------
In Corda all communication takes the form of structured sequences of messages passed between parties which we call flows.

Flows enable complex multi-step, multi-party business interactions to be modelled as blocking code without a central controller.
The code is transformed into an asynchronous state machine, with checkpoints written to the node’s backing database when messages are sent and received.
A node may potentially have millions of flows active at once and they may last days, across node restarts and even upgrades.

A flow library is provided to enable developers to re-use common flow types such as notarisation, membership broadcast,
transaction resolution and recording, and so on.

APIs are provided to send and receive object graphs to and from other identities on the network, embed sub-flows,
report progress information to observers and even interact with people (for manual resolution of exceptional scenarios)

Flows are embedded within CorDapps and deployed to a participant's node for execution.

.. note:: We will be implementing the concept of a flow hospital to provide a means for a node administrator to decide
          whether a paused flow should be killed or repaired. Flows enter this state if they throw exceptions or explicitly request human assistance.

Section 4 of the `Technical white paper`_ provides further detail of the above features.

The following diagram illustrates a sample multi-party business flow:

.. image:: resources/flowFramework.png

Note the following:

* there are 3 participants in this workflow including the notary
* the Buyer and Seller flows (depicted in green) are custom written by developers and deployed within a CorDapp
* the custom written flows invoke both financial library flows such as ``TwoPartyTradeFlow`` (depicted in orange) and core
  library flows such as ``ResolveTransactionsFlow`` and ``NotaryFlow`` (depicted in yellow)
* each side of the flow illustrates the stage of execution with a progress tracker notification
* activities within a flow directly or indirectly interact with its node's ledger (eg. to record a signed, notarised transaction) and vault (eg. to perform a spend of some fungible asset)
* flows interact across parties using send, receive and sendReceive messaging semantics (by implementing the ``FlowLogic`` interface)

.. _`Technical white paper`: _static/corda-technical-whitepaper.pdf