What is a CorDapp?
==================

CorDapps (Corda Distributed Applications) are distributed applications that run on the Corda platform. The goal of a
CorDapp is to allow nodes to reach agreement on updates to the ledger. They achieve this goal by defining flows that
Corda node owners can invoke through RPC calls:

.. image:: resources/node-diagram.png

CorDapps are made up of the following key components:

* States, defining the facts over which agreement is reached (see :doc:`Key Concepts - States <key-concepts-states>`)
* Contracts, defining what constitutes a valid ledger update (see
  :doc:`Key Concepts - Contracts <key-concepts-contracts>`)
* Services, providing long-lived utilities within the node
* Serialisation whitelists, restricting what types your node will receive off the wire

Each CorDapp is installed at the level of the individual node, rather than on the network itself. For example, a node
owner may choose to install the Bond Trading CorDapp, with the following components:

* A ``BondState``, used to represent bonds as shared facts on the ledger
* A ``BondContract``, used to govern which ledger updates involving ``BondState`` states are valid
* Three flows:

    * An ``IssueBondFlow``, allowing new ``BondState`` states to be issued onto the ledger
    * A ``TradeBondFlow``, allowing existing ``BondState`` states to be bought and sold on the ledger
    * An ``ExitBondFlow``, allowing existing ``BondState`` states to be exited from the ledger

After installing this CorDapp, the node owner will be able to use the flows defined by the CorDapp to agree ledger
updates related to issuance, sale, purchase and exit of bonds.