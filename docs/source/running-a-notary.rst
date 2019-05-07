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

Metrics
-------

Corda Enterprise captures different metrics for each of its notary implementations. These metrics can be 
used to analyse the performance and stability of the notary service, and are exposed to third party interfaces.

Common Metrics
==============

All notary implementations record the following metrics:

.. _common_notary_metrics

commitTimer
  Measures the time taken for a single transaction commit and provides a transactions per second (TPS) measurement.

inputStatesMeter
  Measures input states per second (IPS).

conflictCounter
  Tracks double spend attempts. Note that this will also include notarisation retries.

inputStateHistogram
  Tracks distribution of the number of input states.

requestProcessingETA
  Tracks the measured estimated time of request processing. A notary service that is aware of its own throughput can 
  return an estimate of how long requests will be queued for before they can be processed.  Note that a default ETA 
  is returned if there are no transactions currently in the queue.

requestQueueCount
  Tracks the number of requests in the queue at time of insert.
  
requestQueueStateCount
  Tracks the number of states in the queue at time of insert.
  
uniqueTxHashCount
  Tracks the distribution of the number of unique transactions that contributed states to the current transaction.
  
Specific Metrics
================

Some notary implementations record metrics that are unique to them:

.. _mysql_notary_metrics

MySQL Notary Metrics
~~~~~~~~~~~~~~~~~~~

  rollbackCounter
    Tracks the number of rollbacks that occur. When writing to multiple masters with Galera, transaction rollbacks may happen 
    due to high contention.

  connectionExceptionCounter
    Tracks the number of times that the notary service is unable to obtain a database connection.
    