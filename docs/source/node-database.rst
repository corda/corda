Node database
=============

Default in-memory database
--------------------------
By default, nodes store their data in an H2 database. See :doc:`node-database-access-h2`.

.. _standalone_database_config_examples_ref:

Standalone database
-------------------

Running a node against a standalone database requires the following setup steps:

* A database administrator needs to create database users/logins, an empty schema and permissions on the custom database.
  Database user may be set with different permissions:

  * Administrative permissions used for initial database setup (e.g. to create tables) - more flexible as allows the node
    to create all tables during initial startup and it follows node behavior when using in-memory H2 database.
  * Restricted permission for normal node operation to select/insert/delete data. It requires a database administrator
    to create tables/sequences using :ref:`Database management tool <migration-tool>`.

  The example DDL scripts (shown below) contain both variants of database user setup.
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
   SQL database schema setup scripts doesn't use grouping roles and doesn't contain database physical settings e.g. max disk space quota for a user.

SQL Azure and SQL Server
````````````````````````
Corda has been tested with SQL Server 2017 (14.0.3006.16) and Azure SQL (12.0.2000.8), using Microsoft JDBC Driver 6.2.

To set up a database schema with administrative permissions, run the following SQL:

.. sourcecode:: sql

    --for Azure SQL, a login needs to be created on the master database and not on a user database
    CREATE LOGIN [LOGIN] WITH PASSWORD = [PASSWORD];
    CREATE SCHEMA [SCHEMA];
    CREATE USER [USER] FOR LOGIN [LOGIN] WITH DEFAULT_SCHEMA = [SCHEMA];
    GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, ALTER, REFERENCES ON SCHEMA::[SCHEMA] TO [USER];
    GRANT CREATE TABLE TO [USER];
    GRANT CREATE VIEW TO [USER];

To set up a database schema with normal operation permissions, run the following SQL:

.. sourcecode:: sql

    --for Azure SQL, a login needs to be created on the master database and not on a user database
    CREATE LOGIN [LOGIN] WITH PASSWORD = '[PASSWORD]';
    CREATE SCHEMA [SCHEMA];
    CREATE USER [USER] FOR LOGIN [LOGIN] WITH DEFAULT_SCHEMA = [SCHEMA];
    GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, REFERENCES ON SCHEMA::[SCHEMA] TO [USER];

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
        runMigration = [true|false]
    }

Note that:

* The ``runMigration`` is `false` or may be omitted for node setup with normal operation permissions
* The ``database.schema`` property is optional
* The minimum transaction isolation level ``database.transactionIsolationLevel`` is `READ_COMMITTED`
* Ensure that the Microsoft JDBC driver JAR is copied to the ``./drivers`` subdirectory or if applicable specify a path in the ``jarDirs`` property,
  the driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=55539>`_,
  extract the archive and copy the single file ``mssql-jdbc-6.2.2.jre8.jar`` as the archive comes with two JAR versions

Example dataSource.url for SQL Server:

.. sourcecode:: none

    dataSource.url = "jdbc:sqlserver://[HOST]:[PORT];databaseName=[DATABASE_NAME]"

Note that:

* By default the connection to the database is not SSL, for securing JDBC connection refer to
  `Securing JDBC Driver Application <https://docs.microsoft.com/en-us/sql/connect/jdbc/securing-jdbc-driver-applications?view=sql-server-2017>`_,
* Ensure JDBC connection properties match the SQL Server setup, especially when trying to reuse JDBC URL format valid for Azure SQL,
  as misconfiguration may prevent Corda node to start with supposedly unrelated error message e.g.:
  `Caused by: org.hibernate.HibernateException: Access to DialectResolutionInfo cannot be null when 'hibernate.dialect' not set`

To delete existing data from the database, run the following SQL:

.. sourcecode:: sql

    DROP TABLE [SCHEMA].DATABASECHANGELOG;
    DROP TABLE [SCHEMA].DATABASECHANGELOGLOCK;
    DROP TABLE [SCHEMA].NODE_ATTACHMENTS_SIGNERS;
    DROP TABLE [SCHEMA].NODE_ATTACHMENTS_CONTRACTS;
    DROP TABLE [SCHEMA].NODE_ATTACHMENTS;z
    DROP TABLE [SCHEMA].NODE_CHECKPOINTS;
    DROP TABLE [SCHEMA].NODE_TRANSACTIONS;
    DROP TABLE [SCHEMA].NODE_MESSAGE_IDS;
    DROP TABLE [SCHEMA].VAULT_STATES;
    DROP TABLE [SCHEMA].NODE_OUR_KEY_PAIRS;
    DROP TABLE [SCHEMA].NODE_SCHEDULED_STATES;
    DROP TABLE [SCHEMA].VAULT_FUNGIBLE_STATES_PARTS;
    DROP TABLE [SCHEMA].VAULT_LINEAR_STATES_PARTS;
    DROP TABLE [SCHEMA].VAULT_FUNGIBLE_STATES;
    DROP TABLE [SCHEMA].VAULT_LINEAR_STATES;
    DROP TABLE [SCHEMA].VAULT_TRANSACTION_NOTES;
    DROP TABLE [SCHEMA].NODE_LINK_NODEINFO_PARTY;
    DROP TABLE [SCHEMA].NODE_INFO_PARTY_CERT;
    DROP TABLE [SCHEMA].NODE_INFO_HOSTS;
    DROP TABLE [SCHEMA].NODE_INFOS;
    DROP TABLE [SCHEMA].CP_STATES;
    DROP TABLE [SCHEMA].NODE_CONTRACT_UPGRADES;
    DROP TABLE [SCHEMA].NODE_IDENTITIES;
    DROP TABLE [SCHEMA].NODE_NAMED_IDENTITIES;
    DROP TABLE [SCHEMA].NODE_PROPERTIES;
    DROP TABLE [SCHEMA].CONTRACT_CASH_STATES;
    DROP TABLE [SCHEMA].NODE_MUTUAL_EXCLUSION;
    DROP TABLE [SCHEMA].PK_HASH_TO_EXT_ID_MAP;
    DROP TABLE [SCHEMA].STATE_PARTY;
    DROP VIEW [SCHEMA].V_PKEY_HASH_EX_ID_MAP;
    DROP SEQUENCE [SCHEMA].HIBERNATE_SEQUENCE;
    -- additional tables for Notary node
    DROP TABLE IF EXISTS [SCHEMA].NODE_BFT_COMMITTED_STATES;
    DROP TABLE IF EXISTS [SCHEMA].NODE_RAFT_COMMITTED_STATES;
    DROP TABLE IF EXISTS [SCHEMA].NODE_NOTARY_REQUEST_LOG;
    DROP TABLE IF EXISTS [SCHEMA].NODE_NOTARY_COMMITTED_STATES;

Oracle
``````
Corda supports Oracle 11g RC2 (with ojdbc6.jar) and Oracle 12c (ojdbc8.jar).

To set up a database schema with administrative permissions, run the following SQL:

.. sourcecode:: sql

    CREATE USER [USER] IDENTIFIED BY [PASSWORD] QUOTA [SIZE] ON USERS;
    GRANT CREATE SESSION TO [USER];
    GRANT CREATE TABLE TO [USER];
    GRANT CREATE VIEW TO [USER];
    GRANT CREATE SEQUENCE TO [USER];

To set up a database schema with normal operation permissions:

The design of Oracle is that a schema is essentially a user account. So the user has full control over that schema.
In order to restrict the permissions to the database, two users need to be created,
one with administrative permissions (`USER` in the SQL script) and the other with read only permissions (`RESTRICTED_USER` in the SQL script).
A database administrator can create schema objects (tables/sequences) via a user with administrative permissions.
Corda node accesses the schema created by the administrator via a user with readonly permissions allowing to select/insert/delete data.

.. sourcecode:: sql

    CREATE USER [USER] IDENTIFIED BY [PASSWORD] QUOTA [SIZE] ON [TABLESPACE];
    GRANT CREATE SESSION TO [USER];
    GRANT CREATE TABLE TO [USER];
    GRANT CREATE VIEW TO [USER];
    GRANT CREATE SEQUENCE TO [USER];

    CREATE USER [RESTRICTED_USER] identified by [PASSWORD];
    GRANT CREATE SESSION TO [RESTRICTED_USER];
    -- permissions SELECT, INSERT, UPDATE, DELETE need to be granted for each table or sequence, below the list of Corda Node tables and sequences
    GRANT SELECT ON [USER].DATABASECHANGELOG TO [RESTRICTED_USER];
    GRANT SELECT ON [USER].DATABASECHANGELOGLOCK TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_ATTACHMENTS TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_ATTACHMENTS_SIGNERS TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_ATTACHMENTS_CONTRACTS TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_CHECKPOINTS TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_CONTRACT_UPGRADES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_IDENTITIES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_INFOS TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_INFO_HOSTS TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_INFO_PARTY_CERT TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_LINK_NODEINFO_PARTY TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_MESSAGE_IDS TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_NAMED_IDENTITIES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_OUR_KEY_PAIRS TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_PROPERTIES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_SCHEDULED_STATES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_TRANSACTIONS TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].VAULT_FUNGIBLE_STATES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].VAULT_FUNGIBLE_STATES_PARTS TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].VAULT_LINEAR_STATES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].VAULT_LINEAR_STATES_PARTS TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].VAULT_STATES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].VAULT_TRANSACTION_NOTES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_MUTUAL_EXCLUSION TO [RESTRICTED_USER];
    GRANT SELECT SEQUENCE ON [USER].HIBERNATE_SEQUENCE TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].CONTRACT_CASH_STATES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].CP_STATES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].PK_HASH_TO_EXT_ID_MAP TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].STATE_PARTY TO [RESTRICTED_USER];
    GRANT SELECT ON [SCHEMA].V_PKEY_HASH_EX_ID_MAP TO [RESTRICTED_USER];
    -- additional tables for Notary node
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_BFT_COMMITTED_STATES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_RAFT_COMMITTED_STATES TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_NOTARY_REQUEST_LOG TO [RESTRICTED_USER];
    GRANT SELECT, INSERT, UPDATE, DELETE ON [USER].NODE_NOTARY_COMMITTED_STATES TO [RESTRICTED_USER];

When connecting via database user with normal operation permissions, all queries needs to be prefixed with the other schema name.
Corda node doesn't guarantee to prefix each SQL query with a schema namespace.
Additional node configuration entry allows to set current schema to ADMIN_USER while connecting to the database:

.. sourcecode:: none

    dataSourceProperties {
        [...]
        connectionInitSql="alter session set current_schema=[ADMIN_USER]"
    }

To allow VARCHAR2 and NVARCHAR2 column types to store more than 2000 characters ensure the database instance is configured to use
extended data types, e.g. for Oracle 12.1 refer to `MAX_STRING_SIZE <https://docs.oracle.com/database/121/REFRN/GUID-D424D23B-0933-425F-BC69-9C0E6724693C.htm#REFRN10321>`_.

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
        runMigration = [true|false]
    }

Note that:

* SCHEMA name equals to USER name if the schema was setup with administrative permissions (see the first DDL snippet for Oracle)
* SIZE the value (e.g. 100M, 1 GB) depends on your nodes sizing requirements, it can be also set to `UNLIMITED`
* TABLESPACE the tablespace name, if no specific tablespace was created (also depends on your nodes sizing requirements) then use `USERS` tablespace as this one is predefined in the Oracle database
* The ``runMigration`` is `false` or may be omitted for node setup with normal operation permissions
* The ``database.schema`` property is optional
* The minimum transaction isolation level ``database.transactionIsolationLevel`` is `READ_COMMITTED`
* Ensure that the Oracle JDBC driver JAR is copied to the ``./drivers`` subdirectory or if applicable specify path in the ``jarDirs`` property

To delete existing data from the database, run the following SQL:

.. sourcecode:: sql

    DROP TABLE [USER].DATABASECHANGELOG CASCADE CONSTRAINTS;
    DROP TABLE [USER].DATABASECHANGELOGLOCK CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_ATTACHMENTS_SIGNERS CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_ATTACHMENTS_CONTRACTS CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_ATTACHMENTS CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_CHECKPOINTS CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_TRANSACTIONS CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_MESSAGE_IDS CASCADE CONSTRAINTS;
    DROP TABLE [USER].VAULT_STATES CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_OUR_KEY_PAIRS CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_SCHEDULED_STATES CASCADE CONSTRAINTS;
    DROP TABLE [USER].VAULT_FUNGIBLE_STATES_PARTS CASCADE CONSTRAINTS;
    DROP TABLE [USER].VAULT_LINEAR_STATES_PARTS CASCADE CONSTRAINTS;
    DROP TABLE [USER].VAULT_FUNGIBLE_STATES CASCADE CONSTRAINTS;
    DROP TABLE [USER].VAULT_LINEAR_STATES CASCADE CONSTRAINTS;
    DROP TABLE [USER].VAULT_TRANSACTION_NOTES CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_LINK_NODEINFO_PARTY CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_INFO_PARTY_CERT CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_INFO_HOSTS CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_INFOS CASCADE CONSTRAINTS;
    DROP TABLE [USER].CP_STATES CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_CONTRACT_UPGRADES CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_IDENTITIES CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_NAMED_IDENTITIES CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_PROPERTIES CASCADE CONSTRAINTS;
    DROP TABLE [USER].CONTRACT_CASH_STATES CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_MUTUAL_EXCLUSION CASCADE CONSTRAINTS;
    DROP TABLE [SCHEMA].PK_HASH_TO_EXT_ID_MAP;
    DROP TABLE [SCHEMA].STATE_PARTY;
    DROP VIEW [SCHEMA].V_PKEY_HASH_EX_ID_MAP;
    DROP SEQUENCE [USER].HIBERNATE_SEQUENCE;
    -- additional tables for Notary node
    DROP TABLE [USER].NODE_BFT_COMMITTED_STATES CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_RAFT_COMMITTED_STATES CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_NOTARY_COMMITTED_STATES CASCADE CONSTRAINTS;
    DROP TABLE [USER].NODE_NOTARY_REQUEST_LOG CASCADE CONSTRAINTS;
.. _postgres_ref:

PostgreSQL
``````````
Corda has been tested on PostgreSQL 9.6 database, using PostgreSQL JDBC Driver 42.1.4.

To set up a database schema with administration permissions:

.. sourcecode:: sql

    CREATE USER "[USER]" WITH LOGIN password '[PASSWORD]';
    CREATE SCHEMA "[SCHEMA]";
    GRANT USAGE, CREATE ON SCHEMA "[SCHEMA]" TO "[USER]";
    GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "[SCHEMA]" TO "[USER]";
    ALTER DEFAULT privileges IN SCHEMA "[SCHEMA]" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "[USER]";
    GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "[SCHEMA]" TO "[USER]";
    ALTER DEFAULT privileges IN SCHEMA "[SCHEMA]" GRANT USAGE, SELECT ON sequences TO "[USER]";
    ALTER ROLE "[USER]" SET search_path = "[SCHEMA]";

To set up a database schema with normal operation permissions:
The setup differs with admin access by lack of schema permission of CREATE.

.. sourcecode:: sql

    CREATE USER "[USER]" WITH LOGIN password '[PASSWORD]';
    CREATE SCHEMA "[SCHEMA]";
    GRANT USAGE ON SCHEMA "[SCHEMA]" TO "[USER]";
    GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "[SCHEMA]" TO "[USER]";
    ALTER DEFAULT privileges IN SCHEMA "[SCHEMA]" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "[USER]";
    GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "[SCHEMA]" TO "[USER]";
    ALTER DEFAULT privileges IN SCHEMA "[SCHEMA]" GRANT USAGE, SELECT ON sequences TO "[USER]";
    ALTER ROLE "[USER]" SET search_path = "[SCHEMA]";


Example node configuration for PostgreSQL:

.. sourcecode:: none

    dataSourceProperties = {
        dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
        dataSource.url = "jdbc:postgresql://[HOST]:[PORT]/[DATABASE]"
        dataSource.user = [USER]
        dataSource.password = [PASSWORD]
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = [SCHEMA]
        runMigration = [true|false]
    }

Note that:

* The ``runMigration`` is `false` or may be omitted for node setup with normal operation permissions
* The ``database.schema`` property is optional
* If you provide a custom ``database.schema``, its value must either match the ``dataSource.user`` value to end up
  on the standard schema search path according to the
  `PostgreSQL documentation <https://www.postgresql.org/docs/9.3/static/ddl-schemas.html#DDL-SCHEMAS-PATH>`_, or
  the schema search path must be set explicitly via the ``ALTER ROLE "[USER]" SET search_path = "[SCHEMA]"`` statement.
* The value of ``database.schema`` is automatically wrapped in double quotes to preserve case-sensitivity
  (e.g. `AliceCorp` becomes `"AliceCorp"`, without quotes PostgresSQL would treat the value as `alicecorp`),
  this behaviour differs from Corda Open Source where the value is not wrapped in double quotes
* Ensure that the PostgreSQL JDBC driver JAR is copied to the ``./drivers`` subdirectory or if applicable specify path in the ``jarDirs`` property

To delete existing data from the database, drop the existing schema and recreate it using the relevant setup script:

.. sourcecode:: sql

    DROP SCHEMA IF EXISTS "[SCHEMA]" CASCADE;

Node database tables
^^^^^^^^^^^^^^^^^^^^

By default, the node database has the following tables:

+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| Table name                  | Columns                                                                                                                                                                                                  |
+=============================+==========================================================================================================================================================================================================+
| DATABASECHANGELOG           | ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID                                                              |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| DATABASECHANGELOGLOCK       | ID, LOCKED, LOCKGRANTED, LOCKEDBY                                                                                                                                                                        |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_ATTACHMENTS            | ATT_ID, CONTENT, FILENAME, INSERTION_DATE, UPLOADER                                                                                                                                                      |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_ATTACHMENTS_CONTRACTS  | ATT_ID, CONTRACT_CLASS_NAME                                                                                                                                                                              |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_CHECKPOINTS            | CHECKPOINT_ID, CHECKPOINT_VALUE                                                                                                                                                                          |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_CONTRACT_UPGRADES      | STATE_REF, CONTRACT_CLASS_NAME                                                                                                                                                                           |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_IDENTITIES             | PK_HASH, IDENTITY_VALUE                                                                                                                                                                                  |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_INFOS                  | NODE_INFO_ID, NODE_INFO_HASH, PLATFORM_VERSION, SERIAL                                                                                                                                                   |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_INFO_HOSTS             | HOST_NAME, PORT, NODE_INFO_ID, HOSTS_ID                                                                                                                                                                  |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_INFO_PARTY_CERT        | PARTY_NAME, ISMAIN, OWNING_KEY_HASH, PARTY_CERT_BINARY                                                                                                                                                   |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_LINK_NODEINFO_PARTY    | NODE_INFO_ID, PARTY_NAME                                                                                                                                                                                 |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_MESSAGE_IDS            | MESSAGE_ID, INSERTION_TIME, SENDER, SEQUENCE_NUMBER                                                                                                                                                      |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_NAMES_IDENTITIES       | NAME, PK_HASH                                                                                                                                                                                            |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_OUR_KEY_PAIRS          | PUBLIC_KEY_HASH, PRIVATE_KEY, PUBLIC_KEY                                                                                                                                                                 |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_PROPERTIES             | PROPERTY_KEY, PROPERTY_VALUE                                                                                                                                                                             |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_SCHEDULED_STATES       | OUTPUT_INDEXTRANSACTION_IDSCHEDULED_AT                                                                                                                                                                   |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_TRANSACTIONS           | TX_ID, TRANSACTION_VALUE, STATE_MACHINE_RUN_ID                                                                                                                                                           |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| PK_HASH_TO_EXT_ID_MAP       | ID, EXTERNAL_ID, PUBLIC_KEY_HASH                                                                                                                                                                         |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| STATE_PARTY                 | OUTPUT_INDEX, TRANSACTION_ID, ID, PUBLIC_KEY_HASH, X500_NAME                                                                                                                                             |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| VAULT_FUNGIBLE_STATES       | OUTPUT_INDEX, TRANSACTION_ID, ISSUER_NAME, ISSUER_REF, OWNER_NAME, QUANTITY                                                                                                                              |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| VAULT_FUNGIBLE_STATES_PARTS | OUTPUT_INDEX, TRANSACTION_ID, PARTICIPANTS                                                                                                                                                               |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| VAULT_LINEAR_STATES         | OUTPUT_INDEX, TRANSACTION_ID, EXTERNAL_ID, UUID                                                                                                                                                          |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| VAULT_LINEAR_STATES_PARTS   | OUTPUT_INDEX, TRANSACTION_ID, PARTICIPANTS                                                                                                                                                               |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| VAULT_STATES                | OUTPUT_INDEX, TRANSACTION_ID, CONSUMED_TIMESTAMP, CONTRACT_STATE_CLASS_NAME, LOCK_ID, LOCK_TIMESTAMP, NOTARY_NAME, RECORDED_TIMESTAMP, STATE_STATUS, RELEVANCY_STATUS, CONSTRAINT_TYPE, CONSTRAINT_DATA  |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| VAULT_TRANSACTION_NOTES     | SEQ_NO, NOTE, TRANSACTION_ID                                                                                                                                                                             |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| V_PKEY_HASH_EX_ID_MAP       | ID, PUBLIC_KEY_HASH, TRANSACTION_ID, OUTPUT_INDEX, EXTERNAL_ID                                                                                                                                           |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+

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