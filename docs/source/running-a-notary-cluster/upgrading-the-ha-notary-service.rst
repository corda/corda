Upgrading the notary to a new version of Corda Enterprise
=========================================================

Version 4.2
-----------

Since Corda Enterprise 4.2 the MySQL JDBC driver now needs to be installed manually for every worker node, otherwise nodes will fail to start.
See :ref:`notary installation page <mysql_driver>` for more information.

Version 4.0
-----------

In Corda Enterprise 4.0 an additional table ``notary_committed_transactions`` is being used by the HA notary to support the new reference state functionality.

.. note:: In order to enable reference state usage, the minimum platform version of the whole network has to be updated to version 4, which means
   both the client nodes and the notary service have to be upgraded to version 4.

Upgrade steps:

1) Backup your Percona XtraDB Cluster, see :doc:`operating percona <./operating-percona>`.
2) Test you can restore from backup.
3) Log in to any Percona XtraDB Cluster database server and create the ``notary_committed_transactions`` table. It will be replicated to all other database servers.

    .. code:: sql

        CREATE TABLE IF NOT EXISTS notary_committed_transactions (
            transaction_id BINARY(32) NOT NULL,
            CONSTRAINT tid PRIMARY KEY (transaction_id)
        );

4) In the unlikely event that the database gets corrupted, take all the notary worker nodes down and follow the "Repair" guide under :doc:`operating percona <./operating-percona>` to restore the database.
5) Perform a rolling upgrade on the notary worker nodes. Follow the :doc:`node upgrade guide<../node-upgrade-notes>` for each node, and make sure the node is running and is no longer in flow draining mode before moving on to the next one.
