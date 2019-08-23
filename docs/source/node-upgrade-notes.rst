Upgrading your node to Corda 4
==============================

Corda releases strive to be backwards compatible, so upgrading a node is fairly straightforward and should not require changes to
applications. It consists of the following steps:

1. Drain the node.
2. Make a backup of your node directories and/or database.
3. Update the database.
4. Replace the ``corda.jar`` file with the new version.
5. Start up the node. (This step may incur a delay while any necessary database migrations are applied.)
6. Undrain the node. (This step re-enables processing of new inbound flows.)

Note: The protocol is designed to tolerate node outages. During the upgrade process, peers on the network will wait for your node to come back.

Step 1. Drain the node
----------------------

Before a node, or an application on a node, can be upgraded, the node must be put in :ref:`draining-mode`. This brings the currently running
:doc:`key-concepts-flows` to a smooth halt (existing work is finished, and new work is queued rather than being processed).

Draining flows is a key task for node administrators to perform. It exists to simplify applications by ensuring apps don't have to be
able to migrate workflows from any arbitrary point to other arbitrary points, a task that would rapidly become unfeasible as workflow
and protocol complexity increases.

To drain the node, run the ``gracefulShutdown`` command. This will wait for the node to drain and then shut it down once the drain
is complete.

.. warning:: The length of time a node takes to drain depends both on how your applications are designed, and whether any apps are currently
   talking to network peers that are offline or slow to respond. It is, therefore, difficult to give guidance on how long a drain should take. In
   an environment with well written apps and in which your counterparties are online, it is possible that drains may only need a few seconds.

Step 2. Make a backup of your node directories and database
--------------------------------------------------------------

It's always a good idea to back up your data before upgrading any server. This will make it easy to roll back if there's a problem.
You can simply make a copy of the node's data directory to enable this. If you use an external non-H2 database, consult your database
user guide to learn how to make backups.

For a detailed explanation of Corda backup and recovery guarantees, see :ref:`Backup recommendations <backup-recommendations>` .

.. _node_upgrade_notes_update_database_ref:

Step 3. Update database
-----------------------
.. |jar_name| replace:: corda-tools-database-manager-|version|.jar

This step should be performed for production systems.

If you are updating a Corda node that is currently using the default H2 database (which should be used for development purposes),
then skip this step. In this situation, a Corda node will auto-update its database on startup.

You can also skip the manual database update and allow a Corda node to auto-update its database on startup when:

- a database setup is for testing/development purposes and a Corda node connects with *administrative permissions*
  (it can modify database schema)

- you are upgrading a production system, however your policy allows a Corda node to auto-update its database
  and a Corda node connects with *administrative permissions*

In both cases ensure that a node configuration ``node.conf`` file contains:

.. sourcecode:: groovy

    database = {
        runMigration = true
        #other properties
    }


3.1. Configure the Database Management Tool
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The Corda Database Management Tool needs access to a running database.
The tool is configured in a similar manner to the Corda node.
A base directory needs to be provided with the following content:

* a ``node.conf`` file with database connection settings

* a ``drivers`` directory (where the JDBC driver will be placed)

Create a ``node.conf`` with the properties for your database.
``node.conf`` templates for each database vendor are shown below.

Azure SQL
'''''''''

The required ``node.conf`` settings for the Database Management Tool using Azure SQL:

  .. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "com.microsoft.sqlserver.jdbc.SQLServerDataSource"
        dataSource.url = "jdbc:sqlserver://<server>.database.windows.net:1433;databaseName=<database>;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30"
        dataSource.user = <login>
        dataSource.password = <password>
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = <schema>
    }
    myLegalName = <node_legal_name>

Replace placeholders *<server>*, *<login>*, *<password>*, and *<database>* with appropriate values.
*<database>* should be a user database and *<schema>* a schema namespace.
Ensure *<login>* and *<password>* are for a database user with visibility of the *<schema>*.
The ``myLegalName`` field is obligatory, however, it is used in Step 3.4 only
(the tool doesn't understand the context of the run and always requires the field to be present).
For this step you can use any valid dummy name e.g. *O=Dummy,L=London,C=GB* for *<node_legal_name>*.

The Microsoft SQL JDBC driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=55539>`_.
Extract the archive, and copy the single file *mssql-jdbc-6.2.2.jre8.jar* into the ``drivers`` directory.

SQL Server
''''''''''

The required ``node.conf`` settings for the Database Management Tool using SQL Server:

.. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "com.microsoft.sqlserver.jdbc.SQLServerDataSource"
        dataSource.url = "jdbc:sqlserver://<host>:1433;databaseName=<database>"
        dataSource.user = <login>
        dataSource.password = <password>
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = <schema>
    }
    myLegalName = <node_legal_name>

Replace placeholders *<server>*, *<login>*, *<password>*, *<database>* with appropriate values.
*<database>* is a database name and *<schema>* is a schema namespace.
Ensure *<login>* and *<password>* are for a database user with visibility of the *<schema>*.
The ``myLegalName`` field is obligatory however it is used in Step 3.4 only
(the tool doesn't understand the context of the run and always requires the field to be present).
For this step you can use any valid dummy name e.g. *O=Dummy,L=London,C=GB* for *<node_legal_name>*.

The Microsoft JDBC 6.2 driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=55539>`_.
Extract the archive, and copy the single file *mssql-jdbc-6.2.2.jre8.jar* into the ``drivers`` directory.

Oracle
''''''

The required ``node.conf`` settings for the Database Management Tool using Oracle:

.. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "oracle.jdbc.pool.OracleDataSource"
        dataSource.url = "jdbc:oracle:thin:@<host>:<port>:<sid>"
        dataSource.user = <user>
        dataSource.password = <password>
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = <schema>
    }
    myLegalName = <node_legal_name>

Replace placeholders *<host>*, *<port>* and *<sid>*, *<user>*, *<password>* and *<schema>* with appropriate values.
*<schema>* is a database schema namespace, for a basic setup the schema name equals *<user>*.
The ``myLegalName`` field is obligatory however it is used in Step 3.4 only
(the tool doesn't understand the context of the run and always requires the field to be present).
For this step you can use any valid dummy name e.g. *O=Dummy,L=London,C=GB* for *<node_legal_name>*.

Copy the Oracle JDBC driver *ojdbc6.jar* for 11g RC2 or *ojdbc8.jar* for Oracle 12c into the ``drivers`` directory.

PostgreSQL
''''''''''

The required ``node.conf`` settings for the Database Management Tool using PostgreSQL:

.. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
        dataSource.url = "jdbc:postgresql://<host>:<port>/<database>"
        dataSource.user = <user>
        dataSource.password = <password>
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = <schema>
    }
    myLegalName = <node_legal_name>


Replace placeholders *<host>*, *<port>*, *<database>*, *<user>*, *<password>* and *<schema>* with appropriate values.
*<schema>* is the database schema name assigned to the user,
the value of ``database.schema`` is automatically wrapped in double quotes to preserve case-sensitivity.
The ``myLegalName`` field is obligatory however it is used in Step 3.4 only
(the tool doesn't understand the context of the run and always requires the field to be present).
For this step you can use any valid dummy name e.g. *O=Dummy,L=London,C=GB* for *<node_legal_name>*.

Copy the PostgreSQL JDBC Driver *42.1.4* version *JDBC 4.2* into the ``drivers`` directory.

3.2. Extract DDL script using Database Management Tool
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To run the tool, use the following command:

.. sourcecode:: shell

    java -jar tools-database-manager-|release|.jar dry-run -b path_to_configuration_directory

The option ``-b`` points to the base directory (which contains a ``node.conf`` file, and *drivers* and *cordapps* subdirectories).

A script named *migration/\*.sql* will be generated in the base directory.
This script will contain all the statements required to modify and create data structures (e.g. tables/indexes),
and inserts the Liquibase management table *DATABASECHANGELOG*.
The command doesn't alter any tables itself.
For descriptions of the options, refer to the :ref:`Corda Database Management Tool <database-management-tool-ref>` manual.

3.3. Apply DDL scripts on a database
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The generated DDL script can be applied by the database administrator using their tooling of choice.
The script needs to be run by a database user with *administrative* permissions,
with a *<schema>* set as the default schema for that user and matching the schema used by a Corda node.
(e.g. for Azure SQL or SQL Server you should not use the default database administrator account).

.. note:: You may connect as a different user to the one used by a Corda node (e.g. when a node connects via
    a user with *restricted permissions*), as long as your user has the same default schema as the node has.
    (The generated DDL script adds the schema prefix to most of the statements, but not to all of them.)

The whole script needs to be run. Partially running the script causes the database schema content to be inconsistently versioned.

.. warning:: The DDL scripts don't contain any checks to prevent them from running twice.
   An accidental re-run of the scripts will fail (as the tables are already there), but may left some old, orphan tables.


3.4. Apply data updates on a database
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The schema structure changes in Corda 4.0 require data to be propagated to new tables and columns based on the existing rows
and specific node configuration (e.g. node legal name).
Such migrations cannot be expressed by the DDL script, so they need to be performed by the Database Management Tool (or a Corda node).

The Database Management Tool can execute the remaining data upgrade.
As the schema structure is already created in the 3rd step, the tool can connect with *restricted* database permissions.
The only activities in this step are inserts/upgrades data rows, and no alterations to the schema are applied.

You can reuse the tool configuration directory (with modifications) created in Step 3.1, or you can run the tool
accessing the base directory of a Corda node (for which the data update is being performed).
In the latter case no configuration modification is needed,
however the Database Migration Tool needs to be run from within the same machine as a Corda node runs.

If you are reusing the tool configuration directory:

  * ensure ``myLegalName`` setting in ``node.conf`` is set with a node name for which the data update will be run
    (e.g. while upgrading database schema used by a node *O=PartyA,L=London,C=GB*, assign the same value to ``myLegalName``).

    .. warning:: Any ``node.conf`` misconfiguration may cause data row migration to be wrongly applied. This may happen silently (without any error).
       The value of ``myLegalName`` must exactly match the node name that is used in the given database schema.

  * create ``cordapps`` subdirectory and copy the CorDapps used by the Corda node

  * change the database user to one with *restricted permissions*. This ensures no database alteration is performed by this step.

    To run the remaining data migration, run:

    .. sourcecode:: shell

        java -jar tools-database-manager-4.0-RC03.jar execute-migration -b .

   The option ``-b`` points to the base directory (with a ``node.conf`` file, and *drivers* and *cordapps* subdirectories).

Step 4. Replace ``corda.jar`` with the new version
--------------------------------------------------

Replace the ``corda.jar`` with the latest version of Corda.
Make sure it's available on your path, and that you've read the :doc:`release-notes`. Pay particular attention to which version of Java this
node requires.

.. important:: Corda 4 requires Java |java_version| or any higher Java 8 patchlevel. Java 9+ is not currently supported.

Step 5. Start up the node
-------------------------

Start the node in the usual manner you have selected. The node will perform any automatic data migrations required, which may take some
time. If the migration process is interrupted, it can be continued without harm simply by starting the node again.

Step 6. Undrain the node
------------------------

You may now do any checks that you wish to perform, read the logs, and so on. When you are ready, use this command at the shell:

``run setFlowsDrainingModeEnabled enabled: false``

Your upgrade is complete.

.. warning:: if upgrading from Corda Enterprise 3.x, please ensure your node has been upgraded to the latest point release of that
   distribution. See `Upgrade a Corda 3.X Enterprise Node <https://docs.corda.r3.com/releases/3.3/node-operations-upgrading.html#upgrading-a-corda-enterprise-node>`_
   for information on upgrading Corda 3.x versions.
