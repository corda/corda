Node database
=============

Default in-memory database
--------------------------
By default, nodes store their data in an H2 database.
The database (a file persistence.mv.db) is created at the first node startup with the administrator user 'sa' and a blank password.
The user name and password can be changed in node configuration:

.. sourcecode:: groovy

    dataSourceProperties = {
        dataSource.user = [USER]
        dataSource.password = [PASSWORD]
    }

Note, changing user/password for the existing node in node.conf will not update them in the H2 database,
you need to login to the database first to create new user or change the user password.
The database password is required only when the H2 database is exposed on non-localhost address (which is disabled by default).
The node requires the user with administrator permissions in order to creates tables upon the first startup
or after deplying new CorDapps with own tables.

You can connect directly to a running node's database to see its
stored states, transactions and attachments as follows:

* Enable the H2 database access in the node configuration using the following syntax:

  .. sourcecode:: groovy

    h2Settings {
        address: "localhost:0"
    }

* Download the **last stable** `h2 platform-independent zip <http://www.h2database.com/html/download.html>`_, unzip the zip, and
  navigate in a terminal window to the unzipped folder
* Change directories to the bin folder: ``cd h2/bin``

* Run the following command to open the h2 web console in a web browser tab:

  * Unix: ``sh h2.sh``
  * Windows: ``h2.bat``

* Find the node's JDBC connection string. Each node outputs its connection string in the terminal
  window as it starts up. In a terminal window where a node is running, look for the following string:

  ``Database connection URL is              : jdbc:h2:tcp://10.18.0.150:56736/node``

* Paste this string into the JDBC URL field and click ``Connect``, using the default username (``sa``) and no password.

You will be presented with a web interface that shows the contents of your node's storage and vault, and provides an
interface for you to query them using SQL.

The default behaviour is to expose the H2 database on localhost. This can be overridden in the
node configuration using ``h2Settings.address`` and specifying the address of the network interface to listen on,
or simply using ``0.0.0.0:0`` to listen on all interfaces. The node requires a database password to be set when
the database is exposed on the network interface to listen on.

.. _standalone_database_config_examples_ref:

Standalone database
-------------------

Running a node against a standalone database requires the following setup steps:

* A node with the embedded H2 database creates the full database schema including users, permissions and tables definitions.
  For a standalone database a database administrator needs to create database users/logins, an empty schema and permissions on the custom database.
  Tables and sequences may be created by a database administrator, however a node can create the tables/sequences at startup if the ``database.runMigration`` ìs set to ``true``.
* Add node JDBC connection properties to the `dataSourceProperties` entry and Hibernate properties to the `database` entry - see :ref:`Node configuration <database_properties_ref>`.
  Each node needs to use a separate database schema which requires a separate database user/login with a default schema set.
  Properties can be generated with the :ref:`deployNodes Cordform task <testing_cordform_ref>`.
* The Corda distribution does not include any JDBC drivers with the exception of the H2 driver used by samples.
  It is the responsibility of the node administrator to download the appropriate JDBC drivers and configure the database settings.
  Corda will search for valid JDBC drivers under the ``./drivers`` subdirectory of the node base directory.
  Corda distributed via published artifacts (e.g. added as Gradle dependency) will also search for the paths specified by the ``jarDirs`` field of the node configuration.
  The ``jarDirs`` property is a list of paths, separated by commas and wrapped in single quotes e.g. `jarDirs = [ '/lib/jdbc/driver' ]`.
* When a node reuses an existing database (e.g. frequent tests when developing a Cordapp), the data is not deleted by the node at startup.
  E.g. ``Cordform`` Gradle task always delete existing H2 database data file, while a remote database is not altered.
  Ensure that in such cases the database rows have been deleted or all tables and sequences were dropped.

Example configuration for supported standalone databases are shown below.
In each configuration replace placeholders `[USER]`, `[PASSWORD]` and `[SCHEMA]`.

.. note::
   SQL database schema setup scripts are suitable for development purposes only.

SQL Azure and SQL Server
````````````````````````
Corda has been tested with SQL Server 2017 (14.0.3006.16) and Azure SQL (12.0.2000.8), using Microsoft JDBC Driver 6.2.

To set up a database schema, use the following SQL:

.. sourcecode:: sql

    --for Azure SQL, a login needs to be created on the master database and not on a user database
    CREATE LOGIN [LOGIN] WITH PASSWORD = [PASSWORD];
    CREATE SCHEMA [SCHEMA];
    CREATE USER [USER] FOR LOGIN [SCHEMA] WITH DEFAULT_SCHEMA = [SCHEMA];
    GRANT ALTER, DELETE, EXECUTE, INSERT, REFERENCES, SELECT, UPDATE, VIEW DEFINITION ON SCHEMA::[SCHEMA] TO [USER];
    GRANT CREATE TABLE, CREATE PROCEDURE, CREATE FUNCTION, CREATE VIEW TO [USER];

Example node configuration for SQL Azure:

.. sourcecode:: none

    dataSourceProperties = {
        dataSourceClassName = "com.microsoft.sqlserver.jdbc.SQLServerDataSource"
        dataSource.url = "jdbc:sqlserver://[DATABASE_SERVER].database.windows.net:1433;databaseName=[DATABASE];
            encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30"
        dataSource.user = [USER]
        dataSource.password = [PASSWORD]
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = [SCHEMA]
    }

Note that:

* The ``database.schema`` property is optional
* The minimum transaction isolation level ``database.transactionIsolationLevel`` is `READ_COMMITTED`
* Ensure that the Microsoft JDBC driver JAR is copied to the ``./drivers`` subdirectory or if applicable specify a path in the ``jarDirs`` property,
  the driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=55539>`_,
  extract the archive and copy the single file ``mssql-jdbc-6.2.2.jre8.jar`` as the archive comes with two JAR versions

To delete existing data from the database, run the following SQL:

.. sourcecode:: sql

    DROP TABLE IF EXISTS [SCHEMA].cash_state_participants;
    DROP TABLE IF EXISTS [SCHEMA].cash_states_v2_participants;
    DROP TABLE IF EXISTS [SCHEMA].cp_states_v2_participants;
    DROP TABLE IF EXISTS [SCHEMA].dummy_linear_state_parts;
    DROP TABLE IF EXISTS [SCHEMA].dummy_linear_states_v2_parts;
    DROP TABLE IF EXISTS [SCHEMA].dummy_deal_states_parts;
    DROP TABLE IF EXISTS [SCHEMA].node_attachments_contracts;
    DROP TABLE IF EXISTS [SCHEMA].node_attachments;
    DROP TABLE IF EXISTS [SCHEMA].node_checkpoints;
    DROP TABLE IF EXISTS [SCHEMA].node_transactions;
    DROP TABLE IF EXISTS [SCHEMA].node_message_retry;
    DROP TABLE IF EXISTS [SCHEMA].node_message_ids;
    DROP TABLE IF EXISTS [SCHEMA].vault_states;
    DROP TABLE IF EXISTS [SCHEMA].node_our_key_pairs;
    DROP TABLE IF EXISTS [SCHEMA].node_scheduled_states;
    DROP TABLE IF EXISTS [SCHEMA].node_network_map_nodes;
    DROP TABLE IF EXISTS [SCHEMA].node_network_map_subscribers;
    DROP TABLE IF EXISTS [SCHEMA].node_notary_committed_states;
    DROP TABLE IF EXISTS [SCHEMA].node_notary_request_log;
    DROP TABLE IF EXISTS [SCHEMA].node_transaction_mappings;
    DROP TABLE IF EXISTS [SCHEMA].vault_fungible_states_parts;
    DROP TABLE IF EXISTS [SCHEMA].vault_linear_states_parts;
    DROP TABLE IF EXISTS [SCHEMA].vault_fungible_states;
    DROP TABLE IF EXISTS [SCHEMA].vault_linear_states;
    DROP TABLE IF EXISTS [SCHEMA].node_bft_committed_states;
    DROP TABLE IF EXISTS [SCHEMA].node_raft_committed_states;
    DROP TABLE IF EXISTS [SCHEMA].vault_transaction_notes;
    DROP TABLE IF EXISTS [SCHEMA].link_nodeinfo_party;
    DROP TABLE IF EXISTS [SCHEMA].node_link_nodeinfo_party;
    DROP TABLE IF EXISTS [SCHEMA].node_info_party_cert;
    DROP TABLE IF EXISTS [SCHEMA].node_info_hosts;
    DROP TABLE IF EXISTS [SCHEMA].node_infos;
    DROP TABLE IF EXISTS [SCHEMA].cp_states;
    DROP TABLE IF EXISTS [SCHEMA].node_contract_upgrades;
    DROP TABLE IF EXISTS [SCHEMA].node_identities;
    DROP TABLE IF EXISTS [SCHEMA].node_named_identities;
    DROP TABLE IF EXISTS [SCHEMA].node_properties;
    DROP TABLE IF EXISTS [SCHEMA].children;
    DROP TABLE IF EXISTS [SCHEMA].parents;
    DROP TABLE IF EXISTS [SCHEMA].contract_cash_states;
    DROP TABLE IF EXISTS [SCHEMA].contract_cash_states_v1;
    DROP TABLE IF EXISTS [SCHEMA].messages;
    DROP TABLE IF EXISTS [SCHEMA].state_participants;
    DROP TABLE IF EXISTS [SCHEMA].cash_states_v2;
    DROP TABLE IF EXISTS [SCHEMA].cash_states_v3;
    DROP TABLE IF EXISTS [SCHEMA].cp_states_v1;
    DROP TABLE IF EXISTS [SCHEMA].cp_states_v2;
    DROP TABLE IF EXISTS [SCHEMA].dummy_deal_states;
    DROP TABLE IF EXISTS [SCHEMA].dummy_linear_states;
    DROP TABLE IF EXISTS [SCHEMA].dummy_linear_states_v2;
    DROP TABLE IF EXISTS [SCHEMA].dummy_test_states_parts;
    DROP TABLE IF EXISTS [SCHEMA].dummy_test_states;
    DROP TABLE IF EXISTS [SCHEMA].node_mutual_exclusion;
    DROP TABLE IF EXISTS [SCHEMA].DATABASECHANGELOG;
    DROP TABLE IF EXISTS [SCHEMA].DATABASECHANGELOGLOCK;
    DROP TABLE IF EXISTS [SCHEMA].cert_revocation_request_AUD;
    DROP TABLE IF EXISTS [SCHEMA].cert_signing_request_AUD;
    DROP TABLE IF EXISTS [SCHEMA].network_map_AUD;
    DROP TABLE IF EXISTS [SCHEMA].REVINFO;
    DROP TABLE IF EXISTS [SCHEMA].cert_revocation_request;
    DROP TABLE IF EXISTS [SCHEMA].cert_data;
    DROP TABLE IF EXISTS [SCHEMA].cert_revocation_list;
    DROP TABLE IF EXISTS [SCHEMA].node_info;
    DROP TABLE IF EXISTS [SCHEMA].cert_signing_request;
    DROP TABLE IF EXISTS [SCHEMA].network_map;
    DROP TABLE IF EXISTS [SCHEMA].parameters_update;
    DROP TABLE IF EXISTS [SCHEMA].network_parameters;
    DROP TABLE IF EXISTS [SCHEMA].private_network;
    DROP SEQUENCE [SCHEMA].hibernate_sequence;

Oracle
``````
Corda supports Oracle 11g RC2 (with ojdbc6.jar) and Oracle 12c (ojdbc8.jar).

To set up a database schema, use the following SQL:

.. sourcecode:: sql

    CREATE USER [USER] IDENTIFIED BY [PASSWORD];
    GRANT UNLIMITED TABLESPACE TO [USER];
    GRANT CREATE SESSION TO [USER];
    GRANT CREATE TABLE TO [USER];
    GRANT CREATE SEQUENCE TO [USER];
    GRANT ALL PRIVILEGES TO [USER] IDENTIFIED BY [PASSWORD];

Example node configuration for Oracle:

.. sourcecode:: none

    dataSourceProperties = {
        dataSourceClassName = "oracle.jdbc.pool.OracleDataSource"
        dataSource.url = "jdbc:oracle:thin:@[IP]:[PORT]:xe"
        dataSource.user = [USER]
        dataSource.password = [PASSWORD]
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = [SCHEMA]
    }

Note that:

* The ``database.schema`` property is optional
* The minimum transaction isolation level ``database.transactionIsolationLevel`` is `READ_COMMITTED`
* Ensure that the Oracle JDBC driver JAR is copied to the ``./drivers`` subdirectory or if applicable specify path in the ``jarDirs`` property

To delete existing data from the database, run the following SQL:

.. sourcecode:: sql

    DROP TABLE [USER].cash_state_participants CASCADE CONSTRAINTS;
    DROP TABLE [USER].cash_states_v2_participants CASCADE CONSTRAINTS;
    DROP TABLE [USER].cp_states_v2_participants CASCADE CONSTRAINTS;
    DROP TABLE [USER].dummy_linear_state_parts CASCADE CONSTRAINTS;
    DROP TABLE [USER].dummy_linear_states_v2_parts CASCADE CONSTRAINTS;
    DROP TABLE [USER].dummy_deal_states_parts CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_attchments_contracts CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_attachments CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_checkpoints CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_transactions CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_message_retry CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_message_ids CASCADE CONSTRAINTS;
    DROP TABLE [USER].vault_states CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_our_key_pairs CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_scheduled_states CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_network_map_nodes CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_network_map_subscribers CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_notary_committed_states CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_notary_request_log CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_transaction_mappings CASCADE CONSTRAINTS;
    DROP TABLE [USER].vault_fungible_states_parts CASCADE CONSTRAINTS;
    DROP TABLE [USER].vault_linear_states_parts CASCADE CONSTRAINTS;
    DROP TABLE [USER].vault_fungible_states CASCADE CONSTRAINTS;
    DROP TABLE [USER].vault_linear_states CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_bft_committed_states CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_raft_committed_states CASCADE CONSTRAINTS;
    DROP TABLE [USER].vault_transaction_notes CASCADE CONSTRAINTS;
    DROP TABLE [USER].link_nodeinfo_party CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_link_nodeinfo_party CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_info_party_cert CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_info_hosts CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_infos CASCADE CONSTRAINTS;
    DROP TABLE [USER].cp_states CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_contract_upgrades CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_identities CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_named_identities CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_properties CASCADE CONSTRAINTS;
    DROP TABLE [USER].children CASCADE CONSTRAINTS;
    DROP TABLE [USER].parents CASCADE CONSTRAINTS;
    DROP TABLE [USER].contract_cash_states CASCADE CONSTRAINTS;
    DROP TABLE [USER].contract_cash_states_v1 CASCADE CONSTRAINTS;
    DROP TABLE [USER].messages CASCADE CONSTRAINTS;
    DROP TABLE [USER].state_participants CASCADE CONSTRAINTS;
    DROP TABLE [USER].cash_states_v2 CASCADE CONSTRAINTS;
    DROP TABLE [USER].cash_states_v3 CASCADE CONSTRAINTS;
    DROP TABLE [USER].cp_states_v1 CASCADE CONSTRAINTS;
    DROP TABLE [USER].cp_states_v2 CASCADE CONSTRAINTS;
    DROP TABLE [USER].dummy_deal_states CASCADE CONSTRAINTS;
    DROP TABLE [USER].dummy_linear_states CASCADE CONSTRAINTS;
    DROP TABLE [USER].dummy_linear_states_v2 CASCADE CONSTRAINTS;
    DROP TABLE [USER].dummy_test_states_parts CASCADE CONSTRAINTS;
    DROP TABLE [USER].dummy_test_states CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_mutual_exclusion CASCADE CONSTRAINTS;
    DROP TABLE [USER].DATABASECHANGELOG CASCADE CONSTRAINTS;
    DROP TABLE [USER].DATABASECHANGELOGLOCK CASCADE CONSTRAINTS;
    DROP TABLE [USER].cert_revocation_request_AUD CASCADE CONSTRAINTS;
    DROP TABLE [USER].cert_signing_request_AUD CASCADE CONSTRAINTS;
    DROP TABLE [USER].network_map_AUD CASCADE CONSTRAINTS;
    DROP TABLE [USER].REVINFO CASCADE CONSTRAINTS;
    DROP TABLE [USER].cert_revocation_request CASCADE CONSTRAINTS;
    DROP TABLE [USER].cert_data CASCADE CONSTRAINTS;
    DROP TABLE [USER].cert_revocation_list CASCADE CONSTRAINTS;
    DROP TABLE [USER].node_info CASCADE CONSTRAINTS;
    DROP TABLE [USER].cert_signing_request CASCADE CONSTRAINTS;
    DROP TABLE [USER].network_map CASCADE CONSTRAINTS;
    DROP TABLE [USER].parameters_update CASCADE CONSTRAINTS;
    DROP TABLE [USER].network_parameters CASCADE CONSTRAINTS;
    DROP TABLE [USER].private_network CASCADE CONSTRAINTS;
    DROP SEQUENCE [USER].hibernate_sequence;

.. _postgres_ref:

PostgreSQL
``````````
Corda has been tested on PostgreSQL 9.6 database, using PostgreSQL JDBC Driver 42.1.4.

To set up a database schema, use the following SQL:

.. sourcecode:: sql

    CREATE USER "[USER]" WITH LOGIN password '[PASSWORD]';
    CREATE SCHEMA "[SCHEMA]";
    GRANT ALL ON SCHEMA "[SCHEMA]" TO "[USER]";
    GRANT ALL ON ALL tables IN SCHEMA "[SCHEMA]" TO "[USER]";
    ALTER DEFAULT privileges IN SCHEMA "[SCHEMA]" GRANT ALL ON tables TO "[USER]";
    GRANT ALL ON ALL sequences IN SCHEMA "[SCHEMA]" TO "[USER]";
    ALTER DEFAULT privileges IN SCHEMA "[SCHEMA]" GRANT ALL ON sequences TO "[USER]";
    ALTER ROLE "[USER]" SET search_path = "[SCHEMA]";

Example node configuration for PostgreSQL:

.. sourcecode:: none

    dataSourceProperties = {
        dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
        dataSource.url = "jdbc:postgresql://[HOST]:[PORT]/postgres"
        dataSource.user = [USER]
        dataSource.password = [PASSWORD]
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = [SCHEMA]
    }

Note that:

* The ``database.schema`` property is optional
* If you provide a custom ``database.schema``, its value must either match the ``dataSource.user`` value to end up
  on the standard schema search path according to the
  `PostgreSQL documentation <https://www.postgresql.org/docs/9.3/static/ddl-schemas.html#DDL-SCHEMAS-PATH>`_, or
  the schema search path must be set explicitly via the ``ALTER ROLE "[USER]" SET search_path = "[SCHEMA]"`` statement.
* The value of ``database.schema`` is automatically wrapped in double quotes to preserve case-sensitivity
  (e.g. `AliceCorp` becomes `"AliceCorp"`, without quotes PostgresSQL would treat the value as `alicecorp`),
  this behaviour differs from Corda Open Source where the value is not wrapped in double quotes
* Ensure that the PostgreSQL JDBC driver JAR is copied to the ``./drivers`` subdirectory or if applicable specify path in the ``jarDirs`` property

To delete existing data from the database, run the following SQL:

.. sourcecode:: sql

    DROP SCHEMA IF EXISTS "[SCHEMA]" CASCADE;
    CREATE SCHEMA "[SCHEMA]";
    GRANT ALL ON SCHEMA "[SCHEMA]" TO "[USER]";
    GRANT ALL ON ALL tables IN SCHEMA "[SCHEMA]" TO "[USER]";
    ALTER DEFAULT privileges IN SCHEMA "[SCHEMA]" GRANT ALL ON tables TO "[USER]";
    GRANT ALL ON ALL sequences IN SCHEMA "[SCHEMA]" TO "[USER]";
    ALTER DEFAULT privileges IN SCHEMA "[SCHEMA]" GRANT ALL ON sequences TO "[USER]";

Guideline for adding support for other databases
````````````````````````````````````````````````

The Corda distribution can be extended to support other databases without recompilation.
This assumes that all SQL queries run by Corda are compatible with the database and the JDBC driver doesn't require any custom serialization.
To add support for another database to a Corda node, the following JAR files must be provisioned:

* JDBC driver compatible with JDBC 4.2
* Hibernate dialect
* Liquibase extension for the database management (https://www.liquibase.org)
* Implementation of database specific Cash Selection SQL query.
  Class with SQL query needs to extend the ``net.corda.finance.contracts.asset.cash.selection.AbstractCashSelection`` class:

  .. sourcecode:: kotlin

      package net.corda.finance.contracts.asset.cash.selection
      //...
      class CashSelectionCustomDatabaseImpl : AbstractCashSelection() {
            //...
      }

  The ``corda-finance`` module contains ``AbstractCashSelection`` class, so it needs to be added to your project, e.g. when using Gradle:

  .. sourcecode:: groovy

      compile "com.r3.corda:corda-finance:$corda_version"

  The compiled JAR needs to contain a ``resources/META-INF/net.corda.finance.contracts.asset.cash.selection.AbstractCashSelection`` file
  with a class entry to inform the Corda node about the class at startup:

  .. sourcecode:: none

     net.corda.finance.contracts.asset.cash.selection.CashSelectionCustomDatabaseImpl

All additional JAR files need to be copy into ``./drivers`` subdirectory of the node.

.. note:: This is a general guideline. In some cases, it might not be feasible to add support for your desired database without recompiling the Corda source code.