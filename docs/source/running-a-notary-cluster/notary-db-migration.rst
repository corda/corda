Notary database migration
=========================

With the JPA notary being introduced as the second highly available notary, and CockroachDB now being a supported
highly available database, some Corda Enterprise users may consider moving from their current database to another 
database.


When to migrate
---------------

Migrating from one notary implementation to another is a complex procedure and should not be attempted without 
significant benefit from the migration. Possible reasons for migration may include:

* Higher performance is required from a simple notary solution, but the user wishes to use their existing database.
* A highly available solution is required and the current database is not highly available.
* Greater scalability or performance is required from a highly available Percona installation.

.. note::
  Simple notary refers to running the notary using the built-in database connection of the Corda node. This notary 
  implementation can connect to any database supported by Corda, however, the JPA notary implementation is more 
  performant. Note that the simple notary and JPA notary use different database schemas and thus data migration is 
  still required if switching between the two.
  

Migration steps
---------------

The recommended migration path would be to migrate the data stored in the source database to the target database. The data 
would then be restored to a new database and the notary's configuration changed to reflect the new database. Thus, the identity of 
the notary would not change and transactions would still target it. However, the notary will have to be shutdown for a short 
period of time during the migration.

The migration of data from one database to another, or from one database schema to another, requires a third party specialized tool. 
Your organisation may need to purchase a licence to use the tool. Please contact R3 for further advice.

Considerations
~~~~~~~~~~~~~~

* A specialized tool is required.
* The JPA notary uses a different database schema to the Simple or Percona notaries, and thus a transformation must be applied.
* The notary must be shut down during the final phase of migration.
* Depending on the number of states notarized by the notary, the amount of time taken to transfer the data could be significant.


Schema differences - Simple notary to JPA notary
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

+--------------------------+-----------------------------+-------------------+------------------------------------------+
| Table                    | Source Column(s)            | Target Column(s)  | Expression                               |
+==========================+=============================+===================+==========================================+
| notary_committed_states  | transaction_id              | state_ref         | state_ref = transaction_id + ':' +       |
|                          | output_index                |                   | output_index                             |
+--------------------------+-----------------------------+-------------------+------------------------------------------+

Schema differences - Percona to JPA notary
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

+--------------------------+-----------------------------+-------------------+------------------------------------------+
| Table                    | Source Column(s)            | Target Column(s)  | Expression                               |
+==========================+=============================+===================+==========================================+
| notary_committed_states  | issue_transaction_id        | state_ref         | state_ref = issue_transaction_id + ':' + |
|                          | issue_transaction_output_id |                   | issue_transaction_output_id              |
+--------------------------+-----------------------------+-------------------+------------------------------------------+
| notary_request_log       | request_date                | request_timestamp | request_timestamp = request_date         |
+--------------------------+-----------------------------+-------------------+------------------------------------------+


Procedure
~~~~~~~~~

1.  Prepare the target database.
    Use the :ref:`Corda Database Management Tool <database-management-tool-ref>` to prepare the schema in the target database.
2.  Obtain the latest backup of the source database.
    Depending on the tool used, it may be advisable to perform a manual dump of the database so that the format can be read by the tool.
3.  Extract, transform and load the data into the target database, taking into account the schema difference  
    Use the selected specialized tool of your database vendor to prepare a data transformation package. Load this transformed data into 
    the target database. Depending on the size of the source database, loading the data into the target database could take some time.
4.  The target database now contains an older copy of the data present in the source database. This reduces the time taken for the final 
    step.
5.  Shutdown the notary, and when the queue is drained, disconnect the source database to prevent any new data being written to it.
6.  Perform a diff backup in order to retrieve the data that has been written to the database since the backup restored in Step 3.
7.  Use the same transformation in order to load the diff backup into the target database.
8.  Verify that the target database contains all of the data present in the source database. It is advisable to be thorough in the
    verification.
9.  Reconfigure the notary to use the JPA notary connected to the target database.
10. Restart the notary.
11. Verify that the notary operates normally.