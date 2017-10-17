API: ServiceHub
===============
Within ``FlowLogic.call``, the flow developer has access to the node's ``ServiceHub``, which provides access to the
various services the node provides. The services offered by the ``ServiceHub`` are split into the following categories:

* ``ServiceHub.networkMapCache``
    * Provides information on other nodes on the network (e.g. notaries…)
* ``ServiceHub.identityService``
    * Allows you to resolve anonymous identities to well-known identities if you have the required certificates
* ``ServiceHub.attachments``
    * Gives you access to the node's attachments
* ``ServiceHub.validatedTransactions``
    * Gives you access to the transactions stored in the node
* ``ServiceHub.vaultService``
    * Stores the node’s current and historic states
* ``ServiceHub.keyManagementService``
    * Manages signing transactions and generating fresh public keys
* ``ServiceHub.myInfo``
    * Other information about the node
* ``ServiceHub.clock``
    * Provides access to the node’s internal time and date

Additional, ``ServiceHub`` exposes the following properties:

* ``ServiceHub.loadState`` and ``ServiceHub.toStateAndRef`` to resolve a ``StateRef`` into a ``TransactionState`` or
  a ``StateAndRef``
* ``ServiceHub.signInitialTransaction`` to sign a ``TransactionBuilder`` and convert it into a ``SignedTransaction``
* ``ServiceHub.createSignature`` and ``ServiceHub.addSignature`` to create and add signatures to a ``SignedTransaction``