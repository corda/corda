Deploying CorDapps on a node
============================

To deploy a new CorDapp on a node:

* Stop a Corda node

* Make any database changes required to any custom vault tables for the upgraded CorDapp. Depending on the Corda node setup,
  you should follow a procedure for database upgrade in :ref:`production system <corrdapp_deploymnet_database_setup_ref>` shown below,
  or for development/test environment described in :doc:`node-development-cordapp-deployment`
  which contains a simplified databased upgrade process.

* Copy CorDapp JARs into ``cordapps`` directory of the node.

* Restart the node

.. _cordapp_deploymnet_database_setup_ref:

Database update
---------------

For a Corda node connecting to a database with **restricted permissions**, any tables need to be created manually with the
help of the Corda Database Management Tool. This requires that a custom table used by a CorDapp
is created before the CorDapp is deployed.

A CorDapp that stores data in a custom table contains an embedded Liquibase database migration script.
This follows the `Liquibase <http://www.liquibase.org>`_ functionality used by Corda for the database schema management.

Creating a new database table requires a similar procedure to creating a Corda database schema using Corda Database Management Tool.
Because of that, most of the steps are similar to those described in :doc:`node-database-admin`.

1. Check if the CorDapp requires custom tables
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Refer to the CorDapp documentation or consult a CorDapp provider if the CorDapp requires custom backing tables.
You can verify a CorDapp JAR manually to check the presence of script files inside *migration* director, e.g. for Linux:

  .. sourcecode:: bash

    jar -tf <cordapp.jar> | grep -E 'migration.*\.(xml|yml|sql)'

If the CorDapps don't contain any migration scripts, then they don't require custom tables and you may skip this step.

 .. note:: It is possible that a CorDapp is shipped without a database migration script when it should contain one.
    If a CorDapp has been tested on a node running against a non-default database (H2),
    this would have already been detected in your test environment.


2. Configure Database Management Tool
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Corda Database Management Tool needs access to a running database.
The tool is configured in a similar manner to the Corda node.
A base directory needs to be provided with he following content: a ``node.conf`` file with database connection settings, a
``drivers`` directory to place the JDBC driver in, and a ``cordapps`` directory containing the CorDapps that are being deployed.

Copy CorDapps to the *cordapps* subdirectory. This is required to collect and run any database migration scripts for CorDapps.
Create ``node.conf`` with properties for your database. Copy the respective driver into the ``drivers`` directory.
The ``node.conf`` templates for each database vendor are shown below:

* :ref:`Azure SQL <db_setup_configure_db_tool_azure_ref>`

* :ref:`SQL Server <db_setup_configure_db_tool_sqlserver_ref>`

* :ref:`Oracle <db_setup_configure_db_tool_oracle_ref>`

* :ref:`PostgreSQL <db_setup_configure_db_tool_postgresql_ref>`

.. _db_setup_configure_db_tool_azure_ref:

Azure SQL
'''''''''
Database Management Tool settings in configuration file ``node.conf`` for Azure SQL:

  .. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "com.microsoft.sqlserver.jdbc.SQLServerDataSource"
        dataSource.url = "jdbc:sqlserver://<database_server>.database.windows.net:1433;databaseName=<my_database>;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30"
        dataSource.user = my_admin_login
        dataSource.password = "my_password"
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = my_schema
    }

Replace placeholders *<database_server>* and *<my_database>* with appropriate values (*<my_database>* is a user database).
The ``database.schema`` is the database schema name assigned to both administrative and restrictive users.

Microsoft SQL JDBC driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=55539>`_.
Extract the archive, and copy the single file *mssql-jdbc-6.2.2.jre8.jar* into the ``drivers`` directory.

.. _db_setup_configure_db_tool_sqlserver_ref:

SQL Server
''''''''''
Database Management Tool settings in configuration file ``node.conf`` for SQL Server:

  .. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "com.microsoft.sqlserver.jdbc.SQLServerDataSource"
        dataSource.url = "jdbc:sqlserver://<host>:<port>;databaseName=my_database"
        dataSource.user = my_admin_login
        dataSource.password = "my_password"
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = my_schema
    }

Replace placeholders *<host>* and *<port>* with appropriate values. The default SQL Server port is 1433.

Microsoft JDBC 6.2 driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=55539>`_.
Extract the archive, and copy the single file *mssql-jdbc-6.2.2.jre8.jar* into the ``drivers`` directory.

.. _db_setup_configure_db_tool_oracle_ref:

Oracle
''''''
Database Management Tool settings in the configuration file ``node.conf`` for Oracle:

  .. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "oracle.jdbc.pool.OracleDataSource"
        dataSource.url = "jdbc:oracle:thin:@<host>:<port>:<sid>"
        dataSource.user = my_admin_user
        dataSource.password = "my_password"
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = my_admin_user
    }

Replace the placeholder values *<host>*, *<port>* and *<sid>* with appropriate values.
For a basic Oracle installation, the default *<sid>* value is *xe*.
If the user was created with *administrative* permissions the schema name ``database.schema`` equal to the user name (*my_user*).

Copy Oracle JDBC driver *ojdbc6.jar* for 11g RC2 or *ojdbc8.jar* for Oracle 12c into the ``drivers`` directory.

.. _db_setup_configure_db_tool_postgresql_ref:

PostgreSQL
''''''''''
Database Management Tool settings in configuration file ``node.conf`` for PostgreSQL:

  .. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
        dataSource.url = "jdbc:postgresql://<host>:<port>/<database>"
        dataSource.user = my_user
        dataSource.password = "my_password"
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = my_schema
    }

Replace placeholders *<host>*, *<port>* and *<database>* with appropriate values.
The ``database.schema`` is the database schema name assigned to the user.
The value of ``database.schema`` is automatically wrapped in double quotes to preserve case-sensitivity.

Copy PostgreSQL JDBC Driver *42.1.4* version *JDBC 4.2* into the ``drivers`` directory.

3. Extract DDL script using Database Management Tool
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To run the tool, use the following command:

  .. sourcecode:: shell

    java -jar tools-database-manager-|release|.jar create-migration-sql-for-cordapp -b path_to_configuration_directory

The option ``-b`` points to the base directory with a ``node.conf`` file and *drivers* and *cordapps* subdirectories.

A generated script named *migration/\*.sql* will be present in the base directory.
This script contains all statements to create data structures (e.g. tables/indexes) for CorDapps
and inserts to the Liquibase management table *DATABASECHANGELOG*.
The command doesn't alter any tables.
Refer to :ref:`Corda Database Management Tool <database-management-tool-ref>` manual for a description of the options.

4. Apply DDL scripts on a database
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The generated DDL script can be applied by the database administrator using their tooling of choice.
The script needs to be executed by a database user with *administrative* permissions,
with a *<schema>* set as the default schema for that user and matching the schema used by a Corda node.
(e.g. for Azure SQL or SQL Server you should not use the default database administrator account).

.. note:: You may connect as a different user to the one used by a Corda node (e.g. when a node connects via
    a user with *restricted permissions*), so long as the user has the same default schema as the node
    (the generated DDL script may not add schema prefix to all the statements).

The whole script needs to be run. Running the script partially will cause the database schema content to have inconsistent versions.

.. warning:: The DDL scripts don't contain any check preventing running them twice.
   An accidental re-run of the scripts will fail (as the tables are already there) but may leave some old, orphan tables.


.. _db_setup_step_2_oracle_extra_step_ref:

5. Add permission to use tables
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

For some databases, the permission to use tables can only be assigned after the tables are created.
This step is required for the Oracle database only.

Oracle
''''''

Connect to the database as administrator
and grand *SELECT*, *INSERT*, *UPDATE*, *DELETE* permissions to *my_user* for all CorDapps custom tables:

  .. sourcecode:: sql

     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.<cordapp_table> TO my_user;

Change *<cordapp_table>*  to a cordap table name.

