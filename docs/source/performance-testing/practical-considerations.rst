========================
Practical Considerations
========================

This page is listing some practical considerations and tips that might be useful when performance testing Corda Enterprise.

Resetting a Node
================

The Corda Enterprise test cluster at R3 uses an automated set-up to deploy Corda to the test machines, and in the process
completely wipes the database - so resetting the state of the Corda test network is a matter of running a fresh installation
and waiting a handful of minutes. This is the recommended set-up when running performance testing regularly, but might be too
involved for occasional performance tests that can be run on manually deployed clusters.

In these cases, the state of the installed node can be reset by doing the following:

- Stop the Corda node process.
- Delete the ``artemis`` folder in its working directory
- (Optionally) delete the ``logs`` folder
- Wipe the database for the node
- Restart the node. It should rebuild its database and behave like a freshly installed and registered Corda node.

It is important to not delete any other files/directories from the node directory, as these are required for the node to restart
successfully.
This requires that the database user of the node has permission to create the respective tables/and indices. This is
not recommended in a production setting but might be useful in a performance testing context.
Also note that ``runMigration = true`` needs to be set in the node's database configuration, thus allowing liquibase to run and rebuild
the schema.

Note that this will not reset the node registration, identity, keys or the network parameters - it only affects the states and transactions
it knows about. If you have a performance test cluster, it is advisable to reset the whole cluster at the same time.

Wiping the database
-------------------

How to wipe the node database depends on the database being used:

H2
++

In H2, wiping the database simply consists of deleting the files ``persistence.mv.db`` and ``persistence.trace.db`` from the node's working
directory

Microsoft SQL Server
++++++++++++++++++++

To wipe the node's database in SQL server, all the tables in the node's schema need to be dropped - this is easiest to do in a
database administration tool. Note that it is fairly likely that not all tables can be deleted in one go as they have foreign key
indices depending on other tables, and SQL server does not allow dropping a table that other entities depend on. You can either drop
the indices separately or just keep retrying deleting all tables until they are all gone (deleting a table will drop external indices
as well, so theoretically dropping the tables in the right order should work).

Then, the ``hibernate_sequence`` and ``v_pkey_hash_ex_id_map`` view have to be dropped as well - make sure to substitute the correct
schema name for your node::

    DROP SEQUENCE <SCHEMA NAME>.hibernate_sequence
    DROP VIEW <SCHEMA NAME>.v_pkey_hash_ex_id_map

Postgres/Oracle
+++++++++++++++

In PostgreSQL and Oracle, it is possible to just drop the schema for the node - this will wipe all tables/sequences/indices/views.

