.. _key-concepts-label:

Key concepts
============

This section describes the key concepts and features of the Corda platform. It is intended for readers who are new to
Corda, and want to understand its architecture. It does not contain any code, and is suitable for non-developers.

.. note:: 
   The pages in this section should be read in order.

.. toctree::
   :maxdepth: 1
   :hidden:
   
   The Network <key-concepts-ecosystem>
   The Ledger <key-concepts-ledger>
   States <key-concepts-states>
   Transactions <key-concepts-transactions>
   Contracts <key-concepts-contracts>
   Flows <key-concepts-flows>
   Consensus <key-concepts-consensus>
   Notary <key-concepts-notaries>
   Vault <key-concepts-vault>
   Time Windows <key-concepts-time-windows>
   Oracles <key-concepts-oracles>
   Nodes <key-concepts-node>
   Transaction Tear-Offs <key-concepts-tearoffs>
   Trade Offs <key-concepts-tradeoffs>
   Deterministic JVM <key-concepts-djvm>
   

The first topics in this section provide an **overview** of the Corda Distributed Ledger:
 
 * :doc:`key-concepts-ecosystem` - The ecosystem that Corda exists in
 * :doc:`key-concepts-ledger` - The ledger, and how facts on the ledger are shared between nodes
  
The second set of topics describe the core **CorDapp Concepts**: 

 * :doc:`key-concepts-states` - The states represent shared facts on the ledger
 * :doc:`key-concepts-transactions` - The transactions update the ledger states
 * :doc:`key-concepts-contracts` - The contracts govern the ways in which states can evolve over time
 * :doc:`key-concepts-flows` - The flows describe the interactions that must occur between parties to achieve consensus (to satisfy some business requirement)
   
.. note:: 
   When you build a custom CorDapp, your CorDapp will have state, transaction, contract and flow classes.
   
The following **Adavnced Corda Concepts** describe important conceptual information:

 * :doc:`key-concepts-consensus` - How parties on the network reach consensus about shared facts on the ledger
 * :doc:`key-concepts-notaries` - The component that assures uniqueness consensus (prevents double spends)
 * :doc:`key-concepts-vault` - The component that stores on-ledger shared facts for a node

Finally, some concepts that expand on other areas: 

 * :doc:`key-concepts-time-windows` - Transactions can be validated as having fallen after, before or within a particular time window
 * :doc:`key-concepts-oracles` - Transactions can include off-ledger facts retrieved using Oracles
 * :doc:`key-concepts-node` - Each node contains an instance of Corda, one or more CorDapps, and so on
 * :doc:`key-concepts-tearoffs` - Transactions can be signed by parties who have access to only a limited view of the transaction parts
 * :doc:`key-concepts-tradeoffs` - Trade-offs that have been made in designing Corda and CorDapps
 * :doc:`key-concepts-djvm` - Information about the importance and details of the deterministic JVM

The detailed thinking and rationale behind these concepts are presented in two white papers:

    * `Corda: An Introduction`_
    * `Corda: A Distributed Ledger`_ (A.K.A. the Technical White Paper)

Explanations of the key concepts are also available as `videos <https://vimeo.com/album/4555732/>`_.

.. _`Corda: An Introduction`: _static/corda-introductory-whitepaper.pdf
.. _`Corda: A Distributed Ledger`: _static/corda-technical-whitepaper.pdf
