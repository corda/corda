API
===

This section describes the APIs that are available for the development of CorDapps:

.. toctree::
   :maxdepth: 1

   api-states
   api-persistence
   api-contracts
   api-vault-query
   api-transactions
   api-flows
   api-core-types

Before reading this page, you should be familiar with the :doc:`key concepts of Corda <key-concepts>`.

Internal
--------

Code that falls into the following package namespaces are for internal use only and not public. In a future release the
node will not load any CorDapp which uses them.

* Any package in the ``net.corda`` namespace which contains ``.internal``
* ``net.corda.node``
