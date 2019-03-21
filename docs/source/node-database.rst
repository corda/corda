Node database
=============

Introduction
------------

Corda ships out of the box with an `H2 database <http://www.h2database.com>`_ which allows for rapid prototyping and ease of configuration at run-time (see :doc:`node-database-access-h2`).
Corda Enterprise supports a range of commercial 3rd party database vendors (Azure SQL, SQL Server, Oracle and PostgreSQL) for usage in production environments,
and this document provides instructions on how to configure and use these.

.. _common_configuration_steps_ref:

Common Configuration steps
--------------------------

The steps described in this section are common to all 3rd party database vendors supported by Corda Enterprise.

Setting up a Corda node to connect to a database requires:

1. Creating a database user with schema permissions.
2. Database schema creation (e.g. tables).
3. Corda node configuration changes.

Database vendor specific instructions are listed below in their own respective :ref:`sections <db_setup_vendors_ref>`.

.. _db_setup_step_1_ref:

1) Creating database user with schema permissions
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  A database administrator must create a database user and schema namespace with either of the following types of permissions:

  * **administrative permissions**

    This grants the database user full access to a Corda node, such that it can execute both DDL statements
    (to define data structures/schema content e.g. tables) and DML queries (to manipulate data itself e.g. select/delete rows).
    This permission set is more permissive and should be used with caution in production environments.

  * **restricted permissions**

    This grants the database user access to DML execution only (to manipulate data itself e.g. select/delete rows),
    This permission set is more restrictive and is recommended for Corda node :doc:`hot-cold-deployment` and production environments.

  Variants of Data Definition Language (DDL) scripts for both types of permissioned user are provided for each :ref:`supported database vendor <db_setup_vendors_ref>`.

  Depending on permissions being assigned in this step, the subsequent schema creation and upgrade in :ref:`step 2 <db_setup_step_2_ref>`
  may be performed by the database administrator or automatically by a Corda node upon startup.

  .. note:: The step refers to a schema as a namespace with a set of permissions, the next step refers to a schema as set of data structures (e.g. tables, indexes).

  The example permissions scripts do not use group roles nor specify physical database settings e.g. max disk space quota for a user.

  The scripts and node configuration snippets contain example values *my_login* for login, *my_user*/*my_admin_user* for user, *my_password* for password,
  and *my_schema* for schema name. These values are for illustration purpose only. Please substitute with actual values configured for your environment(s).

  .. warning:: Each Corda node needs to use a separate database user and schema where multiple nodes are hosted on the same database instance.

.. _db_setup_step_2_ref:

2) Database schema creation
^^^^^^^^^^^^^^^^^^^^^^^^^^^

   The database schema creation process is described in details in :doc:`node-operations-database-schema-setup` page.
   If you choose the first option as described below, you don't need to perform actions
   described on :doc:`node-operations-database-schema-setup` except configuring the node,
   as a Corda node will create database schema content by itself.

   Data structures (e.g. tables, indexes) can be created and updated by an administrator using the Corda :ref:`database-management-tool-ref` or auto-created
   upon node startup. This will depend on the type of database user permissions setup in the previous step:

   * When a Corda node connects to a database with **administrative permissions**, it will create all tables and other data structures upon startup using embedded scripts
     defined using :ref:`Liquibase <liquibase_ref>`, the database schema management tool adopted by Corda Enterprise.
     Additionally, the node will also execute any scripts to create/upgrade tables bundled with CorDapps (where these define and use custom persisted contract states).
     To allow the node to auto create/upgrade schema add ``runMigration`` option in ``node.conf``:

     .. sourcecode:: groovy

        database {
            runMigration = true
            # ...
        }

   * When a Corda node connects to a database with **restricted permissions**, all data structures (tables, indexes) must already be created.
     The database administrator should use the Corda :ref:`database-management-tool-ref` to connect to the database and create the data structures.
     This tool can also be used to create DDL scripts without executing them against the database (dry-run mode), giving an administrator the opportunity to preview before applying manually.
     The same activity needs to be performed before installing a new Corda release, or a new or upgraded corDapp.

     Refer to :doc:`node-operations-database-schema-setup` page how to perform database schema creation or update.

   .. note::  For developing and testing the node using the Gradle plugin ``Cordform`` ``deployNodes`` task you need to create the database user/schema manually (:ref:`the first Step <db_setup_step_1_ref>`)
      before running the task (deploying nodes).
      Also note that during re-deployment existing data in the database is retained. Remember to cleanup the database if this is required as part of the testing cycle.
      The above restrictions do not apply to the default H2 database as the relevant database data file is re-created during each ``deployNodes`` run.

.. _db_setup_step_3_ref:

3) Corda node configuration changes
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The following updates are required to a nodes filesystem configuration:

  * The Corda node configuration file ``node.conf`` needs to contain JDBC connection properties in the ``dataSourceProperties`` entry
    and other database properties (passed to nodes' JPA persistence provider or schema creation/upgrade flag) in the ``database`` entry.
    For development convenience the properties are specified in the :ref:`deployNodes Cordform task <testing_cordform_ref>` task.

    .. sourcecode:: none

     dataSourceProperties = {
        ...
        dataSourceClassName = <JDBC Data Source class name>
        dataSource.url = <JDBC database URL>
        dataSource.user = <Database user>
        dataSource.password = <Database password>
     }
     database = {
        transactionIsolationLevel = <Transaction isolation level>
        schema = <Database schema name>
     }

    See :ref:`Node configuration <database_properties_ref>` for a complete list of database specific properties, it contains more options useful in case of testing Corda with unsupported databases.

  * Depending on the database user permission created in :ref:`the first Step <db_setup_step_1_ref>`:

    - set ``database { runMigration = true }`` if a Corda node has administrative permissions and database schema creation/upgrade should be executed by the node at startup.
    - set ``database { runMigration = false }`` or remove the ``runMigration`` property if a Corda node has restricted permissions to the database or it expected to create the schema before the node startup (e.g. :doc:`hot-cold-deployment`).

  * The Corda distribution does not include any JDBC drivers with the exception of the H2 driver.
    It is the responsibility of the node administrator or a developer to download the appropriate JDBC driver.
    Corda will search for valid JDBC drivers under the ``./drivers`` subdirectory of the node base directory.
    Corda distributed via published artifacts (e.g. added as Gradle dependency) will also search for the paths specified by the ``jarDirs``
    field of the node configuration.
    The ``jarDirs`` property is a list of paths, separated by commas and wrapped in single quotes e.g. ``jarDirs = [ '/lib/jdbc/driver' ]``.

  * Corda uses `Hikari Pool <https://github.com/brettwooldridge/HikariCP>`_ for creating connection pools.
    To configure a connection pool, the following custom properties can be set in the ``dataSourceProperties`` section, e.g.:

    .. sourcecode:: groovy

     dataSourceProperties = {
        ...
        maximumPoolSize = 10
        connectionTimeout = 50000
     }

.. _db_setup_vendors_ref:

Database vendor specific configuration steps
--------------------------------------------

The sections below contain example DDL scripts to set user/schema permissions and node configuration for supported databases:

* :ref:`H2 Database <db_setup_h2_ref>`
* :ref:`Azure SQL <db_setup_azure_sql_ref>`
* :ref:`SQL Server <db_setup_sql_server_ref>`
* :ref:`Oracle <db_setup_oracle_ref>`
* :ref:`Postgre SQL <db_setup_postgres_ref>`

.. _db_setup_h2_ref:

H2 Database
^^^^^^^^^^^

By default, nodes store their data in an H2 database.
No database setup is needed. Optionally remote H2 access/port can be configured. See :doc:`node-database-access-h2`.

.. _db_setup_azure_sql_ref:

SQL Azure
^^^^^^^^^

Please read :ref:`Common Configuration Steps <common_configuration_steps_ref>` before proceeding with this section.

Permissions for database user and schema namespace
""""""""""""""""""""""""""""""""""""""""""""""""""

* Database schema setup with administrative permissions

  Connect to the master database as an administrator
  (e.g. *jdbc:sqlserver://<database_server>.database.windows.net:1433;databaseName=master;[...]*).
  Run the following script to create a user and a login:

  .. sourcecode:: sql

     CREATE LOGIN my_login WITH PASSWORD = 'my_password';
     CREATE USER my_user FOR LOGIN my_login;

  The password must contain characters from three of the following four sets: Uppercase letters, Lowercase letters, Digits, and Symbols.
  For example *C0rdaAP4ssword* is a correct password. Wrap password by single quotes.

  Connect to a user database as the administrator (replace *master* with a user database in the connection string).
  Run the following script to create a schema and user's permissions:

  .. sourcecode:: sql

     CREATE SCHEMA my_schema;

     CREATE USER my_user FOR LOGIN my_login WITH DEFAULT_SCHEMA = my_schema;
     GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, ALTER, REFERENCES ON SCHEMA::my_schema TO my_user;
     GRANT CREATE TABLE TO my_user;
     GRANT CREATE VIEW TO my_user;

* Database schema setup with restrictive permissions

  Two database users needed to be created, the first one with administrative permission to create schema objects,
  the latter one with restrictive permissions for a Corda node.
  You cannot create schema objects with a default database administrator as Corda Database Management Tool
  may not add a schema namespace prefix to each DDL statement and
  the default database administrator would default to a different schema namespace.
  In order to avoid this issue the, first user with administrative permission has a correct schema set as default.

  Connect to the master database as an administrator
  (e.g. *jdbc:sqlserver://<database_server>.database.windows.net:1433;databaseName=master;[...]*).
  Run the following script to create both users and their logins:

  .. sourcecode:: sql

    CREATE LOGIN my_admin_login WITH PASSWORD = 'my_password';
    CREATE USER my_admin_user FOR LOGIN my_admin_login;

    CREATE LOGIN my_login WITH PASSWORD = 'my_password';
    CREATE USER my_user FOR LOGIN my_login;

  Passwords must contain characters from three of the following four sets: Uppercase letters, Lowercase letters, Digits, and Symbols.
  For example *C0rdaAP4ssword* is a correct password. Wrap password by single quotes.
  Use different passwords for *my_admin_user* and *my_user*.

  Connect to a user database as the administrator (replace *master* with a user database in the connection string).
  Run the following script to create a schema and users' permissions:

  .. sourcecode:: sql

    CREATE SCHEMA my_schema;

    CREATE USER my_admin_user FOR LOGIN my_admin_login WITH DEFAULT_SCHEMA = my_schema;
    GRANT ALTER ON SCHEMA::my_schema TO my_admin_user;
    GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, REFERENCES ON SCHEMA::my_schema TO my_admin_user;
    GRANT CREATE TABLE TO my_admin_user;
    GRANT CREATE VIEW TO my_admin_user;

    CREATE USER my_user FOR LOGIN my_login WITH DEFAULT_SCHEMA = my_schema;
    GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, REFERENCES ON SCHEMA::my_schema TO my_user;

Node configuration
""""""""""""""""""

.. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "com.microsoft.sqlserver.jdbc.SQLServerDataSource"
        dataSource.url = "jdbc:sqlserver://<database_server>.database.windows.net:1433;databaseName=<my_database>;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30"
        dataSource.user = my_login
        dataSource.password = "my_password"
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = my_schema
        runMigration = true
    }

Replace placeholders *<database_server>* and *<my_database>* with appropriate values (*<my_database>* is a user database).
Do not change the default isolation for this database (*READ_COMMITTED*) as the Corda platform has been validated for functional correctness and performance using this level.
The ``database.schema`` is the database schema name assigned to the user.
``runMigration`` value should be set to *true* when using *administrative* permissions only, otherwise set the value to *false*.

Microsoft SQL JDBC driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=55539>`_,
extract the archive and copy the single file *mssql-jdbc-6.2.2.jre8.jar* as the archive comes with two JARs.
:ref:`Common Configuration Steps paragraph <db_setup_step_3_ref>` explains the correct location for the driver JAR in the node installation structure.

Schema cleanup
""""""""""""""

For development purpose, to remove node data run the following SQL script against a user database as *my_user*
if the user has administrative permissions or *my_admin_user* if a Corda node user has restrictive permissions:

.. sourcecode:: sql

    DROP TABLE my_schema.DATABASECHANGELOG;
    DROP TABLE my_schema.DATABASECHANGELOGLOCK;
    DROP TABLE my_schema.NODE_ATTACHMENTS_SIGNERS;
    DROP TABLE my_schema.NODE_ATTACHMENTS_CONTRACTS;
    DROP TABLE my_schema.NODE_ATTACHMENTS;
    DROP TABLE my_schema.NODE_CHECKPOINTS;
    DROP TABLE my_schema.NODE_TRANSACTIONS;
    DROP TABLE my_schema.NODE_MESSAGE_IDS;
    DROP TABLE my_schema.VAULT_STATES;
    DROP TABLE my_schema.NODE_OUR_KEY_PAIRS;
    DROP TABLE my_schema.NODE_SCHEDULED_STATES;
    DROP TABLE my_schema.VAULT_FUNGIBLE_STATES_PARTS;
    DROP TABLE my_schema.VAULT_LINEAR_STATES_PARTS;
    DROP TABLE my_schema.VAULT_FUNGIBLE_STATES;
    DROP TABLE my_schema.VAULT_LINEAR_STATES;
    DROP TABLE my_schema.VAULT_TRANSACTION_NOTES;
    DROP TABLE my_schema.NODE_LINK_NODEINFO_PARTY;
    DROP TABLE my_schema.NODE_INFO_PARTY_CERT;
    DROP TABLE my_schema.NODE_INFO_HOSTS;
    DROP TABLE my_schema.NODE_INFOS;
    DROP TABLE my_schema.CP_STATES;
    DROP TABLE my_schema.NODE_CONTRACT_UPGRADES;
    DROP TABLE my_schema.NODE_IDENTITIES;
    DROP TABLE my_schema.NODE_NAMED_IDENTITIES;
    DROP TABLE my_schema.NODE_NETWORK_PARAMETERS;
    DROP TABLE my_schema.NODE_PROPERTIES;
    DROP TABLE my_schema.CONTRACT_CASH_STATES;
    DROP TABLE my_schema.NODE_MUTUAL_EXCLUSION;
    DROP TABLE my_schema.PK_HASH_TO_EXT_ID_MAP;
    DROP TABLE my_schema.STATE_PARTY;
    DROP VIEW my_schema.V_PKEY_HASH_EX_ID_MAP;
    DROP SEQUENCE my_schema.HIBERNATE_SEQUENCE;
    -- additional tables for Notary node - (some of them are optional and may be not present)
    DROP TABLE IF EXISTS my_schema.NODE_NOTARY_REQUEST_LOG;
    DROP TABLE IF EXISTS my_schema.NODE_NOTARY_COMMITTED_STATES;
    DROP TABLE IF EXISTS my_schema.NODE_NOTARY_COMMITTED_TXS;
    DROP TABLE IF EXISTS my_schema.NODE_BFT_COMMITTED_STATES;
    DROP TABLE IF EXISTS my_schema.NODE_BFT_COMMITTED_TXS;
    DROP TABLE IF EXISTS my_schema.NODE_RAFT_COMMITTED_STATES;
    DROP TABLE IF EXISTS my_schema.NODE_RAFT_COMMITTED_TXS;

Also similarly delete Cordapps specific tables.

If you need to remove schema and the users, run the following script as a database administrator on a user database:

.. sourcecode:: sql

    DROP SCHEMA my_schema;
    DROP USER my_user;
    DROP USER IF EXISTS my_admin_user;

To remove users' logins, run the following script as a database administrator on the master database
(skip the second statement if you haven't create *my_admin_login* login):

.. sourcecode:: sql

    DROP LOGIN my_login;
    DROP LOGIN my_admin_login;

.. _db_setup_sql_server_ref:

SQL Server
^^^^^^^^^^

Corda support SQL Server 2017 (14.0.3006.16).

Please read :ref:`Common Configuration Steps <common_configuration_steps_ref>` before proceeding with this section.

The database collation should be *case insensitive*, refer to
`Server Configuration documentation <https://docs.microsoft.com/en-us/sql/sql-server/install/server-configuration-collation?view=sql-server-2014&viewFallbackFrom=sql-server-2017>`_.


Permissions for database user and schema namespace
""""""""""""""""""""""""""""""""""""""""""""""""""

* Database schema setup with administrative permissions

  Connect to the master database as an administrator (e.g. *jdbc:sqlserver://<host>:<port>;databaseName=master*).
  Run the following script to create a database, a user and a login:

  .. sourcecode:: sql

    CREATE DATABASE my_database;

    CREATE LOGIN my_login WITH PASSWORD = 'my_password', DEFAULT_DATABASE = my_database;
    CREATE USER my_user FOR LOGIN my_login;

  The password must contain characters from three of the following four sets: Uppercase letters, Lowercase letters, Digits, and Symbols.
  For example *C0rdaAP4ssword* is a correct password. Wrap password by single quotes.

  You can create schemas of all Corda nodes in the same database,
  in such case create database only once (*CREATE DATABASE my_database;*) and use it in DDL statements for each node.

  Connect to a user database as the administrator (replace *master* with *my_database* in the connection string).
  Run the following script to create a schema and user's permissions:

  .. sourcecode:: sql

    CREATE SCHEMA my_schema;

    CREATE USER my_user FOR LOGIN my_login WITH DEFAULT_SCHEMA = my_schema;
    GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, ALTER, REFERENCES ON SCHEMA::my_schema TO my_user;
    GRANT CREATE TABLE TO my_user;
    GRANT CREATE VIEW TO my_user;

* Database schema setup with restrictive permissions

  Two database users needed to be created, the first one with administrative permission to create schema objects,
  the latter one with restrictive permissions for a Corda node.
  You cannot create schema objects with the default database administrator as Corda Database Management Tool
  may not add a schema namespace prefix to each DDL statement and
  the default database administrator would default to a different schema namespace.
  In order to avoid this issue the, first user with administrative permission has a correct schema set as default.

  Connect to a master database as an administrator (e.g. *jdbc:sqlserver://<host>:<port>;databaseName=master*).
  Run the following script to create a database, a user and a login:

  .. sourcecode:: sql

    CREATE DATABASE my_database;

    CREATE LOGIN my_admin_login WITH PASSWORD = 'my_password', DEFAULT_DATABASE = my_database;
    CREATE USER my_admin_user FOR LOGIN my_admin_login;

    CREATE LOGIN my_login WITH PASSWORD = 'my_password', DEFAULT_DATABASE = my_database;
    CREATE USER my_user FOR LOGIN my_login;

  Passwords must contain characters from three of the following four sets: Uppercase letters, Lowercase letters, Digits, and Symbols.
  For example *C0rdaAP4ssword* is a correct password. Wrap password by single quotes.
  Use different passwords for *my_admin_user* and *my_user*.

  You can create schemas for each Corda node within the same database (*my_database*),
  in such case use the same my_database name and run the first DDL statement CREATE DATABASE my_database; only once.

  Connect to a user database as the administrator (replace *master* with *my_database* in the connection string).
  Run the following script to create a schema and users' permissions:

  .. sourcecode:: sql

    CREATE SCHEMA my_schema;

    CREATE USER my_admin_user FOR LOGIN my_admin_login WITH DEFAULT_SCHEMA = my_schema;
    GRANT ALTER ON SCHEMA::my_schema TO my_admin_user;
    GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, REFERENCES ON SCHEMA::my_schema TO my_admin_user;
    GRANT CREATE TABLE TO my_admin_user;
    GRANT CREATE VIEW TO my_admin_user;

    CREATE USER my_user FOR LOGIN my_login WITH DEFAULT_SCHEMA = my_schema;
    GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, REFERENCES ON SCHEMA::my_schema TO my_user;

Node configuration
""""""""""""""""""

.. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "com.microsoft.sqlserver.jdbc.SQLServerDataSource"
        dataSource.url = "jdbc:sqlserver://<host>:<port>;databaseName=my_database"
        dataSource.user = my_login
        dataSource.password = "my_password"
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = my_schema
        runMigration = true
    }

Replace placeholders *<host>*, *<port>* with appropriate values, the default SQL Server port is 1433.
By default the connection to the database is not SSL, for securing JDBC connection refer to
`Securing JDBC Driver Application <https://docs.microsoft.com/en-us/sql/connect/jdbc/securing-jdbc-driver-applications?view=sql-server-2017>`_.

Do not change the default isolation for this database (*READ_COMMITTED*) as the Corda platform has been validated for functional correctness and performance using this level.
``runMigration`` value should be set to *true* when using *administrative* permissions only, otherwise set the value to *false*.
The ``database.schema`` is the database schema name assigned to the user.

Microsoft JDBC 6.2 driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=55539>`_,
extract the archive and copy the single file ``mssql-jdbc-6.2.2.jre8.jar`` as the archive comes with two JARs.
:ref:`Common Configuration Steps paragraph <db_setup_step_3_ref>` explains the correct location for the driver JAR in the node installation structure.

Ensure JDBC connection properties match the SQL Server setup. Especially when trying to reuse Azure SQL JDBC URL
which is invalid for SQL Server.  This may lead to Corda node failing to start with message:
*Caused by: org.hibernate.HibernateException: Access to DialectResolutionInfo cannot be null when 'hibernate.dialect' not set*.

Schema cleanup
""""""""""""""

For development purpose, to remove node data run the following SQL script as for Azure SQL database.

.. _db_setup_oracle_ref:

Oracle
^^^^^^

Corda supports Oracle 11g RC2 and Oracle 12c.

Please read :ref:`Common Configuration Steps <common_configuration_steps_ref>` before proceeding with this section.

To allow *VARCHAR2* and *NVARCHAR2* column types to store more than 2000 characters ensure the database instance is configured to use
extended data types, e.g. for Oracle 12.1 refer to `MAX_STRING_SIZE <https://docs.oracle.com/database/121/REFRN/GUID-D424D23B-0933-425F-BC69-9C0E6724693C.htm#REFRN10321>`_.

Permissions for database user and schema namespace
""""""""""""""""""""""""""""""""""""""""""""""""""

The tablespace size is unlimited, set the value (e.g. 100M, 1 GB) depending on your nodes sizing requirements.
The script uses the default tablespace *users* with *unlimited* database space quota assigned to the user.
Revise these settings depending on your nodes sizing requirements.

* To set up a database schema with administrative permissions, run the following SQL:

  .. sourcecode:: sql

     CREATE USER my_user IDENTIFIED BY my_password DEFAULT TABLESPACE users QUOTA unlimited ON users;
     GRANT CREATE SESSION TO my_user;
     GRANT CREATE TABLE TO my_user;
     GRANT CREATE VIEW TO my_user;
     GRANT CREATE SEQUENCE TO my_user;

*  To set up a database schema with normal operation permissions:

  The design of Oracle is that a schema is essentially a user account. So the user has full control over that schema.
  In order to restrict the permissions to the database, two users need to be created,
  one with administrative permissions (*my_admin_user* in the SQL script) and the other with read only permissions (*my_user* in the SQL script).
  A database administrator can create schema objects (tables/sequences) via a user with administrative permissions.
  Corda node accesses the schema created by the administrator via a user with readonly permissions allowing to select/insert/delete data.
  Permissions *SELECT*, *INSERT*, *UPDATE*, *DELETE* need to be granted for each table or sequence, as presented in the DDL script.

  Run this script after the database schema content has been created in :ref:`step 2 <db_setup_step_2_ref>`:

  .. sourcecode:: sql

     CREATE USER my_admin_user IDENTIFIED BY my_password DEFAULT TABLESPACE users QUOTA unlimited ON users;
     GRANT CREATE SESSION TO my_admin_user;
     GRANT CREATE TABLE TO my_admin_user;
     GRANT CREATE VIEW TO my_admin_user;
     GRANT CREATE SEQUENCE TO my_admin_user;

     CREATE USER my_user identified by my_password;
     GRANT CREATE SESSION TO my_user;
     GRANT SELECT ON my_admin_user.DATABASECHANGELOG TO my_user;
     GRANT SELECT ON my_admin_user.DATABASECHANGELOGLOCK TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_ATTACHMENTS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_ATTACHMENTS_SIGNERS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_ATTACHMENTS_CONTRACTS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_CHECKPOINTS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_CONTRACT_UPGRADES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_IDENTITIES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_INFOS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_INFO_HOSTS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_INFO_PARTY_CERT TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_LINK_NODEINFO_PARTY TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_MESSAGE_IDS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_NAMED_IDENTITIES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_NETWORK_PARAMETERS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_OUR_KEY_PAIRS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_PROPERTIES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_SCHEDULED_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_TRANSACTIONS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.VAULT_FUNGIBLE_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.VAULT_FUNGIBLE_STATES_PARTS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.VAULT_LINEAR_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.VAULT_LINEAR_STATES_PARTS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.VAULT_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.VAULT_TRANSACTION_NOTES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_MUTUAL_EXCLUSION TO my_user;
     GRANT SELECT SEQUENCE ON my_admin_user.HIBERNATE_SEQUENCE TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.CONTRACT_CASH_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.CP_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.PK_HASH_TO_EXT_ID_MAP TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.STATE_PARTY TO my_user;
     GRANT SELECT ON my_admin_user.V_PKEY_HASH_EX_ID_MAP TO my_user;
     -- additional tables for Notary node - (some of them are optional and may be not present)
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_NOTARY_REQUEST_LOG TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_NOTARY_COMMITTED_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_NOTARY_COMMITTED_TXS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_BFT_COMMITTED_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_RAFT_COMMITTED_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_BFT_COMMITTED_TXS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_RAFT_COMMITTED_TXS TO my_user;


Node configuration
""""""""""""""""""

.. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "oracle.jdbc.pool.OracleDataSource"
        dataSource.url = "jdbc:oracle:thin:@<host>:<port>:<sid>"
        dataSource.user = my_user
        dataSource.password = "my_password"
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = my_user
        runMigration = true
    }

Replace placeholder *<host>*, *<port>* and *<sid>* with appropriate values, for a basic Oracle installation the default *<sid>* value is *xe*.
If the user was created with *administrative* permissions the schema name ``database.schema`` equal to the user name (*my_user*).

When connecting via database user with restricted permissions, all queries needs to be prefixed with the other schema name.
Set ``database.schema`` value to *my_admin_user*.
Corda node doesn't guarantee to prefix each SQL query with a schema namespace.
The additional configuration entry ``dataSourceProperties`` allows to set the current schema to the admin user (*my_user*) upon connection to the database:

  .. sourcecode:: groovy

    dataSourceProperties {
        //...
        connectionInitSql="alter session set current_schema=my_admin_user"
    }
    database = {
        schema = my_admin_user
    }

Do not change the default isolation for this database (*READ_COMMITTED*) as the Corda platform has been validated for functional correctness and performance using this level.
``runMigration`` value must be set to *true* when the database user has *administrative* permissions and set to *false* when using *restricted* permissions.

Place Oracle JDBC driver *ojdbc6.jar* for 11g RC2 or *ojdbc8.jar* for Oracle 12c in the node directory ``drivers`` described in :ref:`Common Configuration Steps <db_setup_step_3_ref>`.
Database schema name can be set in JDBC URL string e.g. currentSchema=my_schema.

.. _oracle_wallet_ref:

Oracle Wallet
"""""""""""""

You can also connect to an Oracle database using credentials stored in an Oracle Wallet, with the following changes.

Assuming you have an Oracle Wallet set up in ``~/wallet``, create an entry for the database in your ``tnsnames.ora``, with the
relevant ``<host-address>``, ``<host-port>`` and ``<service-name>``, e.g.:

.. sourcecode:: none

    my_database =
      (DEscriptTION =
        (ADDRESS = (PROTOCOL = TCP)(host = <host-address>)(port = <host-port>))
        (CONNECT_DATA =
          (SERVER = DEDICATED)
          (SERVICE_NAME = <service-name>)
        )
      )

Create a ``sqlnet.ora`` in the same directory with the configuration for the wallet, e.g.:

.. sourcecode:: none

    WALLET_LOCATION =
       (SOURCE =
         (METHOD = FILE)
         (METHOD_DATA =
           (DIRECTORY = ~/wallet)
         )
       )

    SQLNET.WALLET_OVERRIDE = TRUE
    SSL_CLIENT_AUTHENTICATION = FALSE
    SSL_VERSION = 0

Then, add the database credentials to your wallet using the following command (see `here <https://docs.oracle.com/middleware/1212/wls/JDBCA/oraclewallet.htm>`_ for more information on setting up Oracle Wallet):

.. sourcecode:: bash

    mkstore -wrl ~/wallet -createCredential my_database <db-username> <db-password>

You will be prompted for the wallet password in order to be able to update the wallet.

Then modify the connection string in your ``node.conf`` to reference your TNS name, and set the username and password to ``null`` (they are
required fields).

.. sourcecode:: none

    dataSourceProperties = {
        dataSourceClassName = "oracle.jdbc.pool.OracleDataSource"
        dataSource.url = "jdbc:oracle:thin:/@my_database"
        dataSource.user = null
        dataSource.password = null
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = my_schema
        runMigration = true
    }

Finally, start up the node with the following system properties set to the location of your wallet and the location of your ``tnsnames.ora``:

.. sourcecode:: bash

    java -Doracle.net.wallet_location=~/wallet -Doracle.net.tns_admin=<path-to-tnsnames> -jar corda.jar

Schema cleanup
""""""""""""""

For development purpose, to remove node data run the following SQL script, also similarly drop Cordapps specific tables:

.. sourcecode:: sql

    DROP TABLE my_user.DATABASECHANGELOG CASCADE CONSTRAINTS;
    DROP TABLE my_user.DATABASECHANGELOGLOCK CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_ATTACHMENTS_SIGNERS CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_ATTACHMENTS_CONTRACTS CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_ATTACHMENTS CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_CHECKPOINTS CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_TRANSACTIONS CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_MESSAGE_IDS CASCADE CONSTRAINTS;
    DROP TABLE my_user.VAULT_STATES CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_OUR_KEY_PAIRS CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_SCHEDULED_STATES CASCADE CONSTRAINTS;
    DROP TABLE my_user.VAULT_FUNGIBLE_STATES_PARTS CASCADE CONSTRAINTS;
    DROP TABLE my_user.VAULT_LINEAR_STATES_PARTS CASCADE CONSTRAINTS;
    DROP TABLE my_user.VAULT_FUNGIBLE_STATES CASCADE CONSTRAINTS;
    DROP TABLE my_user.VAULT_LINEAR_STATES CASCADE CONSTRAINTS;
    DROP TABLE my_user.VAULT_TRANSACTION_NOTES CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_LINK_NODEINFO_PARTY CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_INFO_PARTY_CERT CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_INFO_HOSTS CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_INFOS CASCADE CONSTRAINTS;
    DROP TABLE my_user.CP_STATES CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_CONTRACT_UPGRADES CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_IDENTITIES CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_NAMED_IDENTITIES CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_NETWORK_PARAMETERS CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_PROPERTIES CASCADE CONSTRAINTS;
    DROP TABLE my_user.CONTRACT_CASH_STATES CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_MUTUAL_EXCLUSION CASCADE CONSTRAINTS;
    DROP TABLE my_user.PK_HASH_TO_EXT_ID_MAP;
    DROP TABLE my_user.STATE_PARTY;
    DROP VIEW my_user.V_PKEY_HASH_EX_ID_MAP;
    DROP SEQUENCE my_user.HIBERNATE_SEQUENCE;
    -- additional tables for Notary node - (some of them are optional and may be not present)
    DROP TABLE my_user.NODE_NOTARY_REQUEST_LOG CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_NOTARY_COMMITTED_STATES CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_NOTARY_COMMITTED_TXS CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_BFT_COMMITTED_STATES CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_BFT_COMMITTED_TXS CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_RAFT_COMMITTED_STATES CASCADE CONSTRAINTS;
    DROP TABLE my_user.NODE_RAFT_COMMITTED_TXS CASCADE CONSTRAINTS;

.. _db_setup_postgres_ref:

PostgreSQL
^^^^^^^^^^

Corda has been tested on PostgreSQL 9.6 database.

Please read the :ref:`Prerequisites paragraph <common_configuration_steps_ref>` section containing common setup instructions before proceeding with this section.

Permissions for database user and schema namespace
""""""""""""""""""""""""""""""""""""""""""""""""""

* To set up a database schema with administration permissions:

  .. sourcecode:: sql

    CREATE USER "my_user" WITH LOGIN PASSWORD 'my_password';
    CREATE SCHEMA "my_schema";
    GRANT USAGE, CREATE ON SCHEMA "my_schema" TO "my_user";
    GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "my_schema" TO "my_user";
    ALTER DEFAULT privileges IN SCHEMA "my_schema" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "my_user";
    GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "my_schema" TO "my_user";
    ALTER DEFAULT privileges IN SCHEMA "my_schema" GRANT USAGE, SELECT ON sequences TO "my_user";
    ALTER ROLE "my_user" SET search_path = "my_schema";

* To set up a database schema with normal operation permissions:
  The setup differs with admin access by lack of schema permission of CREATE.

 .. sourcecode:: sql

    CREATE USER "my_user" WITH LOGIN PASSWORD 'my_password';
    CREATE SCHEMA "my_schema";
    GRANT USAGE ON SCHEMA "my_schema" TO "my_user";
    GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "my_schema" TO "my_user";
    ALTER DEFAULT privileges IN SCHEMA "my_schema" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "my_user";
    GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "my_schema" TO "my_user";
    ALTER DEFAULT privileges IN SCHEMA "my_schema" GRANT USAGE, SELECT ON sequences TO "my_user";
    ALTER ROLE "my_user" SET search_path = "my_schema";

If you provide a custom schema name different than user name, then the last statement setting search_path
prevents quering the different default schema search path (`default schema search path <https://www.postgresql.org/docs/9.3/static/ddl-schemas.html#DDL-SCHEMAS-PATH>`_).

Node configuration
""""""""""""""""""

.. sourcecode:: none

    dataSourceProperties = {
        dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
        dataSource.url = "jdbc:postgresql://<host>:<port>/<database>"
        dataSource.user = my_user
        dataSource.password = "my_password"
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = my_schema
        runMigration = true
    }

Replace placeholders *<host>*, *<port>* and *<database>* with appropriate values.
The ``database.schema`` is the database schema name assigned to the user.
The value of ``database.schema`` is automatically wrapped in double quotes to preserve case-sensitivity
(e.g. *AliceCorp* becomes *AliceCorp*, without quotes PostgresSQL would treat the value as *alicecorp*),
this behaviour differs from Corda Open Source where the value is not wrapped in double quotes.

Do not change the default isolation for this database (*READ_COMMITTED*) as the Corda platform has been validated for functional correctness and performance using this level.
``runMigration`` value should be set to *true* when using *administrative* permissions only, otherwise set the value to *false*.

Place PostgreSQL JDBC Driver *42.1.4* version *JDBC 4.2* in the node directory ``drivers`` described in :ref:`Common Configuration Steps <db_setup_step_3_ref>`.

Schema cleanup
""""""""""""""

For development purpose, to remove node and Cordpps specific data run the following SQL script:

.. sourcecode:: sql

    DROP SCHEMA IF EXISTS "my_schema" CASCADE;


Node database tables
^^^^^^^^^^^^^^^^^^^^

By default, the node database has the following tables:

+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| Table name                  | Columns                                                                                                                                                                                                  |
+=============================+==========================================================================================================================================================================================================+
| DATABASECHANGELOG           | ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID                                                             |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| DATABASECHANGELOGLOCK       | ID, LOCKED, LOCKGRANTED, LOCKEDBY                                                                                                                                                                        |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_ATTACHMENTS            | ATT_ID, CONTENT, FILENAME, INSERTION_DATE, UPLOADER                                                                                                                                                      |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_ATTACHMENTS_CONTRACTS  | ATT_ID, CONTRACT_CLASS_NAME                                                                                                                                                                              |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_ATTACHMENTS_SIGNERS    | ATT_ID, SIGNER                                                                                                                                                                                           |
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
| NODE_NAMED_IDENTITIES       | NAME, PK_HASH                                                                                                                                                                                            |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_NETWORK_PARAMETERS     | HASH, EPOCH, PARAMETERS_BYTES, SIGNATURE_BYTES, CERT, PARENT_CERT_PATH                                                                                                                                   |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_OUR_KEY_PAIRS          | PUBLIC_KEY_HASH, PRIVATE_KEY, PUBLIC_KEY                                                                                                                                                                 |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_PROPERTIES             | PROPERTY_KEY, PROPERTY_VALUE                                                                                                                                                                             |
+-----------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_SCHEDULED_STATES       | OUTPUT_INDEX, TRANSACTION_ID, SCHEDULED_AT                                                                                                                                                               |
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


The node database for a Simple Notary has additional tables:

+------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| Table name                   | Columns                                                                                                                                                                                                  |
+==============================+==========================================================================================================================================================================================================+
| NODE_NOTARY_COMMITTED_STATES | OUTPUT_INDEX, TRANSACTION_ID, CONSUMING_TRANSACTION_ID                                                                                                                                                   |
+------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_NOTARY_COMMITTED_TXS    | TRANSACTION_ID                                                                                                                                                                                           |
+------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| NODE_NOTARY_REQUEST_LOG      | ID, CONSUMING_TRANSACTION_ID, REQUESTING_PARTY_NAME, REQUEST_TIMESTAMP, REQUEST_SIGNATURE                                                                                                                |
+------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+

The tables for other experimental notary implementations are not described here.