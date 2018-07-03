Setting up a notary service
---------------------------

Corda comes with several notary implementations built-in:

1. **Single-node**: a simple notary service that persists notarisation requests in the node's database. It is easy to configure
   and can be used for testing, or networks that do not have strict availability requirements.
2. **Crash fault-tolerant**: a highly available notary service operated by a single party.
3. **Byzantine fault-tolerant** *(experimental)*: a decentralised highly available notary service operated by a group of parties.

Single-node
===========

To have a regular Corda node provide a notary service you simply need to set appropriate ``notary`` configuration values
before starting it:

.. parsed-literal::

    notary : { validating : false }

For a validating notary service specify:

.. parsed-literal::

    notary : { validating : true }


See :ref:`key_concepts_notaries_validation` for more details about validating versus non-validating notaries.

For clients to be able to use the notary service, its identity must be added to the network parameters. This will be
done automatically when creating the network, if using :doc:`network-bootstrapper`. See :doc:`setting-up-a-corda-network`
for more details.

Crash fault-tolerant
====================

Corda Enterprise provides a highly available notary service implementation backed by a replicated Percona XtraDB cluster.
This is the recommended implementation for production networks. See :doc:`running-a-notary-cluster/toctree` for detailed
setup steps.

Byzantine fault-tolerant (experimental)
=======================================

A prototype BFT notary implementation based on `BFT-Smart <https://github.com/bft-smart/library>`_ is available. You can
try it out on our `notary demo <https://github.com/corda/corda/tree/release-V3.1/samples/notary-demo>`_ page. Note that it
is still experimental and there is active work ongoing for a production ready solution.

We do not recommend using it in any long-running test or production deployments.
