Setting up a notary service
---------------------------

Corda Enterprise comes with two notary implementations built-in:

1. **Single-node**: a simple notary service that persists notarisation requests in the node's database. It is easy to configure
   and can be used for testing, or networks that do not have strict availability requirements.
2. **Highly available**: a clustered notary service operated by a single party, able to tolerate crash faults.

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
done automatically when creating the network, if using :doc:`network-bootstrapper`. See :doc:`corda-networks-index`
for more details.

Highly available
================

Corda Enterprise provides a highly available notary service implementation backed by a replicated Percona XtraDB cluster.
This is the recommended implementation for production networks. See :doc:`running-a-notary-cluster/toctree` for detailed
setup steps.

.. warning::
    Upgrading an existing single-node notary to be highly available is currently unsupported.