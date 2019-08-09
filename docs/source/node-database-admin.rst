Database schema setup
=====================

Corda Enterprise supports the commercial 3rd party databases: Azure SQL, SQL Server, Oracle, and PostgreSQL.
This document provides instructions describing how to create database schemas (user permissions, the Corda node's tables, and other database objects),
and how to configure Corda nodes to connect to a database with *restricted permissions* for production use.
If you just need a quick database setup for testing/development, please refer to :doc:`node-database-developer`.

Setting up a Corda node to connect to a database requires:

1. :ref:`Creating a database user with schema permissions <db_setup_step_1_ref>`
2. :ref:`Database schema creation <db_setup_step_2_ref>` with Corda Database Management Tool
3. :ref:`Corda node configuration changes <db_setup_step_3_ref>`
4. :ref:`Database configuration <db_setup_step_4_ref>`

.. _db_setup_step_1_ref:

1. Creating a database user with schema permissions
---------------------------------------------------

A database administrator must create a database user and a schema namespace with **restricted permissions**.
This grants the user access to DML execution only (to manipulate data itself e.g. select/delete rows).
This permission set is recommended for Corda nodes :doc:`hot-cold-deployment` and production environments.
The less restricted permission set for a database user with **administrative permissions** is described in :doc:`node-database-developer`
(this is recommended for development purposes only).

.. note:: This step refers to *schema* as a namespace with a set of permissions,
          the schema content (tables, indexes) is created in :ref:`the next step <db_setup_step_2_ref>`.

Variants of Data Definition Language (DDL) scripts are provided for each supported database vendor.
The example permissions scripts have no group roles and do not specify physical database settings (such as the max disk space quota for a user).
The scripts and node configuration snippets contain placeholder values; *my_login* for login, *my_user*/*my_admin_user* for users,
*my_password* for password, and *my_schema* for the schema name. These values are for illustrative purposes only,
please replace them with the actual values configured for your environment or environments.

.. warning:: Each Corda node needs to use a separate database user and schema where multiple nodes are hosted on the same database instance.

Creating database users with schema permissions for:

* :ref:`Azure SQL <db_setup_create_user_azure_ref>`

* :ref:`SQL Server <db_setup_create_user_sqlserver_ref>`

* :ref:`Oracle <db_setup_create_user_oracle_ref>`

* :ref:`PostgreSQL <db_setup_create_user_postgresql_ref>`

.. _db_setup_create_user_azure_ref:

Azure SQL
^^^^^^^^^

Two database users needed to be created; the first one with administrative permissions to create schema objects,
and the second with restrictive permissions for a Corda node.
The schema objects are created by a separate user rather than a default database administrator. This ensures the correct schema namespace
is used (the Corda Database Management Tool may not add a schema namespace prefix to each DDL statement).

Connect to the master database as an administrator
(e.g. *jdbc:sqlserver://<database_server>.database.windows.net:1433;databaseName=master;[...]*).
Run the following script to create both users and their logins:

.. sourcecode:: sql

  CREATE LOGIN my_admin_login WITH PASSWORD = 'my_password';
  CREATE USER my_admin_user FOR LOGIN my_admin_login;
  CREATE LOGIN my_login WITH PASSWORD = 'my_password';
  CREATE USER my_user FOR LOGIN my_login;

By default the password must contain characters from three of the following four sets: uppercase letters, lowercase letters, digits, and symbols,
e.g. *C0rdaAP4ssword* is a valid password. Passwords are delimited with single quotes.
Use different passwords for *my_admin_user* and *my_user*.

Connect to a user database as the administrator (replace *master* with a user database in the connection string).
Run the following script to create a schema and assign user permissions:

.. sourcecode:: sql

  CREATE SCHEMA my_schema;
  CREATE USER my_admin_user FOR LOGIN my_admin_login WITH DEFAULT_SCHEMA = my_schema;
  GRANT ALTER ON SCHEMA::my_schema TO my_admin_user;
  GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, REFERENCES ON SCHEMA::my_schema TO my_admin_user;
  GRANT CREATE TABLE TO my_admin_user;
  GRANT CREATE VIEW TO my_admin_user;
  CREATE USER my_user FOR LOGIN my_login WITH DEFAULT_SCHEMA = my_schema;
  GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, REFERENCES ON SCHEMA::my_schema TO my_user;

.. _db_setup_create_user_sqlserver_ref:

SQL Server
^^^^^^^^^^

Two database users need to be created; the first with administrative permissions to create schema objects,
the second with restrictive permissions for a Corda node.
The schema objects are created by a separate user rather than a default database administrator. This ensures the correct schema namespace
is used (the Corda Database Management Tool may not add a schema namespace prefix to each DDL statement).

Connect to a master database as an administrator (e.g. *jdbc:sqlserver://<host>:<port>;databaseName=master*).
Run the following script to create a database, a user and a login:

.. sourcecode:: sql

  CREATE DATABASE my_database;
  CREATE LOGIN my_admin_login WITH PASSWORD = 'my_password', DEFAULT_DATABASE = my_database;
  CREATE USER my_admin_user FOR LOGIN my_admin_login;
  CREATE LOGIN my_login WITH PASSWORD = 'my_password', DEFAULT_DATABASE = my_database;
  CREATE USER my_user FOR LOGIN my_login;

By default the password must contain characters from three of the following four sets: uppercase letters, lowercase letters, digits, and symbols,
e.g. *C0rdaAP4ssword* is a valid password. Passwords are delimited with single quotes.
Use different passwords for *my_admin_user* and *my_user*.

You can create schemas for several Corda nodes within the same database (*my_database*),
in which case run the first DDL statement (*CREATE DATABASE my_database;*) only once.

Connect to a user database as the administrator (replace *master* with *my_database* in the connection string).
Run the following script to create a schema and assign user permissions:

.. sourcecode:: sql

  CREATE SCHEMA my_schema;

  CREATE USER my_admin_user FOR LOGIN my_admin_login WITH DEFAULT_SCHEMA = my_schema;
  GRANT ALTER ON SCHEMA::my_schema TO my_admin_user;
  GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, REFERENCES ON SCHEMA::my_schema TO my_admin_user;
  GRANT CREATE TABLE TO my_admin_user;
  GRANT CREATE VIEW TO my_admin_user;
  CREATE USER my_user FOR LOGIN my_login WITH DEFAULT_SCHEMA = my_schema;
  GRANT SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION, REFERENCES ON SCHEMA::my_schema TO my_user;

.. _db_setup_create_user_oracle_ref:

Oracle
^^^^^^

The design of Oracle is that a schema is essentially a user account. So the user has full control over that schema.
In order to restrict the permissions to the database, two users need to be created,
one with administrative permissions (*my_admin_user* in the SQL script) and the other with read only permissions (*my_user* in the SQL script).
A database administrator can create schema objects (tables/sequences) via a user with administrative permissions.
The Corda node accesses the schema created by the administrator via a user with restricted permissions, allowing them to select/insert/delete data only.
For Oracle databases, those permissions (*SELECT*, *INSERT*, *UPDATE*, *DELETE*) need to be granted explicitly for each table.

The tablespace size is unlimited, set the value (e.g. 100M, 1 GB) depending on your nodes sizing requirements.
The script uses the default tablespace *users* with *unlimited* database space quota assigned to the user.
Revise these settings depending on your nodes sizing requirements.

Run this script as database administrator:

.. sourcecode:: sql

  CREATE USER my_admin_user IDENTIFIED BY my_password DEFAULT TABLESPACE users QUOTA unlimited ON users;
  GRANT CREATE SESSION TO my_admin_user;
  GRANT CREATE TABLE TO my_admin_user;
  GRANT CREATE VIEW TO my_admin_user;
  GRANT CREATE SEQUENCE TO my_admin_user;
  GRANT SELECT ON v_$parameter TO my_admin_user;

The permissions for the Corda node user to access database objects will be assigned in :ref:`the following step <db_setup_step_2_oracle_extra_step_ref>`
after the database objects are created.

The last permission for the *v_$parameter* view is needed when a database is running in
`Database Compatibility mode <https://docs.oracle.com/en/database/oracle/oracle-database/12.2/upgrd/what-is-oracle-database-compatibility.html>`_.
If the permission is not granted then :ref:`Corda Database Management Tool <database-management-tool-ref>` will output the message
*'Could not set check compatibility mode on OracleDatabase, assuming not running in any sort of compatibility mode ...'* in a log file,
the message can be ignored.

.. _db_setup_create_user_postgresql_ref:

PostgreSQL
^^^^^^^^^^

Connect to the database as an administrator and run the following script to create a node user:

.. sourcecode:: sql

  CREATE USER "my_user" WITH LOGIN PASSWORD 'my_password';
  CREATE SCHEMA "my_schema";
  GRANT USAGE ON SCHEMA "my_schema" TO "my_user";
  GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "my_schema" TO "my_user";
  ALTER DEFAULT privileges IN SCHEMA "my_schema" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "my_user";
  GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "my_schema" TO "my_user";
  ALTER DEFAULT privileges IN SCHEMA "my_schema" GRANT USAGE, SELECT ON sequences TO "my_user";
  ALTER ROLE "my_user" SET search_path = "my_schema";

If you provide a custom schema name (different to the user name), then the last statement, setting the search_path,
prevents querying the different default schema search path
(`default schema search path <https://www.postgresql.org/docs/9.3/static/ddl-schemas.html#DDL-SCHEMAS-PATH>`_).

.. _db_setup_step_2_ref:

2. Database schema creation
---------------------------

All data structures (tables, indexes) must be created before the Corda node connects to a database with **restricted permissions**.
Corda is released without a separate set of DDL scripts, instead a database administrator needs to use
the :ref:`Corda Database Management Tool <database-management-tool-ref>` to output the DDL scripts and run the scripts against a database.
Each Corda release version has the associated Corda Database Management Tool release which outputs a compatible set of DDL scripts.
The DDL scripts contain the history of a database evolution - series of table alterations leading to the current state, using the
functionality of `Liquibase <http://www.liquibase.org>`_ which is used by Corda for database schema management.

2.1. Create Liquibase management tables
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The Corda Database Management Tool needs to connect to a running database instance in order to output DDL script.
The presence of the *DATABASECHANGELOG* and *DATABASECHANGELOGLOCK* Liquibase management tables is required as well.
The database administrator needs to create these tables manually.
Replace the schema namespace *my_schema* with the schema used by the node.

DDL script for management tables for:

* :ref:`Azure SQL and SQL Server <db_setup_liquibase_management_tables_azure_ref>`

* :ref:`Oracle <db_setup_liquibase_management_tables_oracle_ref>`

* :ref:`PostgreSQL <db_setup_liquibase_management_tables_postgresql_ref>`

.. _db_setup_liquibase_management_tables_azure_ref:

Azure SQL and SQL Server
''''''''''''''''''''''''

DDL script to create Liquibase control tables for Azure SQL and SQL Server:

.. sourcecode:: sql

  CREATE TABLE my_schema.DATABASECHANGELOG (
  ID nvarchar(255) NOT NULL,
  AUTHOR nvarchar(255) NOT NULL,
  FILENAME nvarchar(255) NOT NULL,
  DATEEXECUTED datetime2(3) NOT NULL,
  ORDEREXECUTED int NOT NULL,
  EXECTYPE nvarchar(10) NOT NULL,
  MD5SUM nvarchar(35) NULL,
  DESCRIPTION nvarchar(255) NULL,
  COMMENTS nvarchar(255) NULL,
  TAG nvarchar(255) NULL,
  LIQUIBASE nvarchar(20) NULL,
  CONTEXTS nvarchar(255) NULL,
  LABELS nvarchar(255) NULL,
  DEPLOYMENT_ID nvarchar(10) NULL);

  CREATE TABLE my_schema.DATABASECHANGELOGLOCK (
  ID int NOT NULL,
  LOCKED bit NOT NULL,
  LOCKGRANTED datetime2(3) NULL,
  LOCKEDBY nvarchar(255) NULL,
  CONSTRAINT PK_DATABASECHANGELOGLOCK PRIMARY KEY (ID));

.. _db_setup_liquibase_management_tables_oracle_ref:

Oracle
''''''

DDL script to create Liquibase control tables for Oracle, change the *users* tablespace if necessary:

.. sourcecode:: sql

  CREATE TABLE my_user."DATABASECHANGELOG" (
  "ID" VARCHAR2(255) NOT NULL ENABLE,
  "AUTHOR" VARCHAR2(255) NOT NULL ENABLE,
  "FILENAME" VARCHAR2(255) NOT NULL ENABLE,
  "DATEEXECUTED" TIMESTAMP (6) NOT NULL ENABLE,
  "ORDEREXECUTED" NUMBER(*,0) NOT NULL ENABLE,
  "EXECTYPE" VARCHAR2(10) NOT NULL ENABLE,
  "MD5SUM" VARCHAR2(35),
  "DESCRIPTION" VARCHAR2(255),
  "COMMENTS" VARCHAR2(255),
  "TAG" VARCHAR2(255),
  "LIQUIBASE" VARCHAR2(20),
  "CONTEXTS" VARCHAR2(255),
  "LABELS" VARCHAR2(255),
  "DEPLOYMENT_ID" VARCHAR2(10)) TABLESPACE users;

  CREATE TABLE my_user."DATABASECHANGELOGLOCK" (
  "ID" NUMBER(*,0) NOT NULL ENABLE,
  "LOCKED" NUMBER(1,0) NOT NULL ENABLE,
  "LOCKGRANTED" TIMESTAMP (6),
  "LOCKEDBY" VARCHAR2(255),
  CONSTRAINT "PK_DATABASECHANGELOGLOCK" PRIMARY KEY ("ID")) TABLESPACE users;

.. _db_setup_liquibase_management_tables_postgresql_ref:

PostgreSQL
''''''''''

DDL script to create Liquibase control tables for PostgreSQL:

.. sourcecode:: sql

  CREATE TABLE "my_schema".databasechangelog (
  id varchar(255) NOT NULL,
  author varchar(255) NOT NULL,
  filename varchar(255) NOT NULL,
  dateexecuted timestamp NOT NULL,
  orderexecuted int4 NOT NULL,
  exectype varchar(10) NOT NULL,
  md5sum varchar(35) NULL,
  description varchar(255) NULL,
  comments varchar(255) NULL,
  tag varchar(255) NULL,
  liquibase varchar(20) NULL,
  contexts varchar(255) NULL,
  labels varchar(255) NULL,
  deployment_id varchar(10) NULL);

  CREATE TABLE "my_schema".databasechangeloglock (
  id int4 NOT NULL,
  locked bool NOT NULL,
  lockgranted timestamp NULL,
  lockedby varchar(255) NULL,
  CONSTRAINT pk_databasechangeloglock PRIMARY KEY (id));

2.2. Configure the Database Management Tool
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The Corda Database Management Tool needs access to a running database.
The tool is configured in a similar way to the Corda node.
A base directory needs to be provided with he following content:

* a ``node.conf`` file with database connection settings

* a ``drivers`` directory to place JDBC driver

* a ``cordapps`` directory with CorDapps requiring custom tables.

Copy CorDapps to the *cordapps* subdirectory, this is required to collect and run any custom database migration scripts.
Create a ``node.conf`` with properties for your database, and copy the respective driver into the ``drivers`` directory.
The ``node.conf`` templates for each database vendor are shown below:

* :ref:`Azure SQL <db_setup_configure_db_tool_azure_ref>`

* :ref:`SQL Server <db_setup_configure_db_tool_sqlserver_ref>`

* :ref:`Oracle <db_setup_configure_db_tool_oracle_ref>`

* :ref:`PostgreSQL <db_setup_configure_db_tool_postgresql_ref>`

.. _db_setup_configure_db_tool_azure_ref:

Azure SQL
'''''''''

The required ``node.conf`` settings for the Database Management Tool using Azure SQL:

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

Replace the placeholders *<database_server>* and *<my_database>* with appropriate values (*<my_database>* is a user database).
The ``database.schema`` is the database schema name assigned to both administrative and restrictive users.

The Microsoft SQL JDBC driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=56615>`_,
extract the archive and copy the single file *mssql-jdbc-6.4.0.jre8.jar* into the ``drivers`` directory.

.. _db_setup_configure_db_tool_sqlserver_ref:

SQL Server
''''''''''

The required ``node.conf`` settings for the Database Management Tool using SQL Server:

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

Replace placeholders *<host>*, *<port>* with appropriate values, the default SQL Server port is 1433.

The Microsoft JDBC 6.4 driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=56615>`_,
extract the archive and copy the single file *mssql-jdbc-6.4.0.jre8.jar* into the ``drivers`` directory.

.. _db_setup_configure_db_tool_oracle_ref:

Oracle
''''''

The required ``node.conf`` settings for the Database Management Tool using Oracle:

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

Replace the placeholders *<host>*, *<port>* and *<sid>* with appropriate values.
For a basic Oracle installation, the default *<sid>* value is *xe*.
If the user was created with *administrative* permissions, the schema name ``database.schema`` is equal to the user name (*my_user*).

Copy the Oracle JDBC driver *ojdbc6.jar* for 11g RC2 or *ojdbc8.jar* for Oracle 12c into the ``drivers`` directory.

.. _db_setup_configure_db_tool_postgresql_ref:

PostgreSQL
''''''''''

The required ``node.conf`` settings for the Database Management Tool using PostgreSQL:

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

Replace the placeholders *<host>*, *<port>* and *<database>* with appropriate values.
The ``database.schema`` is the database schema name assigned to the user.
The value of ``database.schema`` is automatically wrapped in double quotes to preserve case-sensitivity.

Copy PostgreSQL JDBC Driver *42.1.4* version *JDBC 4.2* into the ``drivers`` directory.

2.3. Extract the DDL script using Database Management Tool
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To run the tool use the following command:

  .. sourcecode:: shell

    java -jar tools-database-manager-|release|.jar dry-run -b path_to_configuration_directory

The option ``-b`` points to the base directory with a ``node.conf`` file and *drivers* and *cordapps* subdirectories.

A script will be generated named *migration/\*.sql* in the base directory.
This script contains all the statements to create/modify data structures (e.g. tables/indexes)
and inserts to the Liquibase management table *DATABASECHANGELOG*.
The command doesn't alter any tables.
Refer to :ref:`Corda Database Management Tool <database-management-tool-ref>` manual for more detail.

2.4. Apply DDL scripts on a database
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The generated DDL script can be applied by the database administrator using their tooling of choice.
The script needs to be executed by a database user with *administrative* permissions,
with a *<schema>* set as the default schema for that user, and matching the schema used by a Corda node.
(e.g. for Azure SQL or SQL Server you should not use the default database administrator account).

.. note:: You may connect as a different user than the one used by a Corda node (e.g. when a node connects via
    a user with *restricted permissions*), as long as the user has the same default schema as node has
    (the generated DDL script adds a schema prefix to most of the statements but not to all of them).

The whole script needs to be run. Partially running the script would cause the database schema content to be in an inconsistent version.

.. warning:: The DDL scripts don't contain any check preventing running them twice.
    An accidental re-run of the scripts will fail (as the tables are already there) but may leave some old, orphan tables.


.. _db_setup_step_2_oracle_extra_step_ref:

2.5. Add permission to use tables
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

For some databases the specific permissions can be assigned only after the tables are created.
This step is required for Oracle databases only.

Oracle
^^^^^^

Connect to the database as administrator and run the following DDL script:

  .. sourcecode:: sql

     CREATE USER my_user identified by my_password;
     GRANT CREATE SESSION TO my_user;
     GRANT SELECT ON v_$parameter TO my_user;
     GRANT SELECT ON my_admin_user.DATABASECHANGELOG TO my_user;
     GRANT SELECT ON my_admin_user.DATABASECHANGELOGLOCK TO my_user;
     GRANT SELECT SEQUENCE ON my_admin_user.HIBERNATE_SEQUENCE TO my_user;
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
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.CONTRACT_CASH_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.CP_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.PK_HASH_TO_EXT_ID_MAP TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.STATE_PARTY TO my_user;
     GRANT SELECT ON my_admin_user.V_PKEY_HASH_EX_ID_MAP TO my_user;

Additional table permissions are required for a notary node, some of them are optional:

  .. sourcecode:: sql

     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_NOTARY_REQUEST_LOG TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_NOTARY_COMMITTED_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_NOTARY_COMMITTED_TXS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_BFT_COMMITTED_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_RAFT_COMMITTED_STATES TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_BFT_COMMITTED_TXS TO my_user;
     GRANT SELECT, INSERT, UPDATE, DELETE ON my_admin_user.NODE_RAFT_COMMITTED_TXS TO my_user;

Grant *SELECT*, *INSERT*, *UPDATE*, *DELETE* permissions to *my_user* for all custom CorDapp tables.

.. _db_setup_step_3_ref:

3. Corda node configuration
---------------------------

The following updates are required to the filesystem of a node:

  * The Corda node configuration file ``node.conf`` needs to contain JDBC connection properties in the ``dataSourceProperties`` entry
    and other database properties in the ``database`` entry (passed to a node JPA persistence provider or schema creation/upgrade flag).

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
        runMigration = false
     }

    .. note:: `Node configuration <database_properties_ref>` contains a complete list of database specific properties.

  * The restricted node database user has no permissions to alter a database schema, so ``runMigration`` is set to ``false``.

  * The Corda distribution does not include any JDBC drivers with the exception of the H2 driver.
    It is the responsibility of the node administrator or a developer to install the appropriate JDBC driver.
    Corda will search for valid JDBC drivers under the ``./drivers`` subdirectory of the node base directory.
    Alternatively the path can be also specified by the ``jarDirs`` option in :ref:`the node configuration <corda_configuration_file_jar_dirs_ref>`.
    The ``jarDirs`` property is a list of paths, separated by commas and wrapped in single quotes e.g. ``jarDirs = [ '/lib/jdbc/driver' ]``.

  * Corda uses `Hikari Pool <https://github.com/brettwooldridge/HikariCP>`_ for creating connection pools.
    To configure a connection pool, the following custom properties can be set in the ``dataSourceProperties`` section, e.g.:

    .. sourcecode:: groovy

     dataSourceProperties = {
        ...
        maximumPoolSize = 10
        connectionTimeout = 50000
     }

Configuration templates for each database vendor are shown below:

* :ref:`Azure SQL <db_setup_configure_node_azure_ref>`

* :ref:`SQL Server <db_setup_configure_node_sqlserver_ref>`

* :ref:`Oracle <db_setup_configure_node_oracle_ref>`

* :ref:`PostgreSQL <db_setup_configure_node_postgresql_ref>`

.. _db_setup_configure_node_azure_ref:

Azure SQL
^^^^^^^^^

Example node configuration file for Azure SQL:

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
        runMigration = false
    }

Replace placeholders *<database_server>* and *<my_database>* with appropriate values (*<my_database>* is a user database).
Do not change the default isolation for this database (*READ_COMMITTED*) as the Corda platform has been validated for functional correctness
and performance using this level.
The ``database.schema`` is the database schema name assigned to the user.

The Microsoft SQL JDBC driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=56615>`_,
extract the archive and copy the single file *mssql-jdbc-6.4.0.jre8.jar* (the archive comes with two JARs).
:ref:`Common Configuration Steps paragraph <db_setup_step_3_ref>` explains the correct location for the driver JAR in the node installation structure.

.. _db_setup_configure_node_sqlserver_ref:

SQL Server
^^^^^^^^^^

Example node configuration file for  SQL Server:

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
        runMigration = false
    }

Replace placeholders *<host>* and *<port>* with appropriate values (the default SQL Server port is 1433).
By default the connection to the database is not SSL. To secure the JDBC connection, refer to
`Securing JDBC Driver Applications <https://docs.microsoft.com/en-us/sql/connect/jdbc/securing-jdbc-driver-applications?view=sql-server-2017>`_.

Do not change the default isolation for this database (*READ_COMMITTED*) as the Corda platform has been validated for functional correctness and performance using this level.
The ``database.schema`` is the database schema name assigned to the user.

The Microsoft JDBC 6.4 driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=56615>`_,
extract the archive and copy the single file ``mssql-jdbc-6.4.0.jre8.jar`` (the archive comes with two JARs).
:ref:`Common Configuration Steps <db_setup_step_3_ref>` explains the correct location for the driver JAR in the node installation structure.

Ensure JDBC connection properties match the SQL Server setup. Especially when trying to reuse Azure SQL JDBC URLs
which are invalid for SQL Server. This may lead to a Corda node failing to start with message:
*Caused by: org.hibernate.HibernateException: Access to DialectResolutionInfo cannot be null when 'hibernate.dialect' not set*.

.. _db_setup_configure_node_oracle_ref:

Oracle
^^^^^^

Example node configuration file for Oracle:

  .. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "oracle.jdbc.pool.OracleDataSource"
        dataSource.url = "jdbc:oracle:thin:@<host>:<port>:<sid>"
        dataSource.user = my_user
        dataSource.password = "my_password"
        connectionInitSql="alter session set current_schema=my_admin_user"
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = my_admin_user
        runMigration = false
    }

Replace the placeholders *<host>*, *<port>* and *<sid>* with appropriate values. (For a basic Oracle installation, the default *<sid>* value is *xe*.)
If the user was created with *administrative* permissions, the schema name ``database.schema`` equal to the user name (*my_user*).

When connecting with a database user with restricted permissions, all queries need to be prefixed with the other schema name.
Set the ``database.schema`` value to *my_admin_user*.
The Corda node doesn't guarantee to prefix all SQL queries with the schema namespace.
The additional configuration entry ``connectionInitSql`` sets the current schema to the admin user (*my_user*) on connection to the database.

Do not change the default isolation for this database (*READ_COMMITTED*), as the Corda platform has been validated for functional correctness and performance using this level.

Place the Oracle JDBC driver *ojdbc6.jar* for 11g RC2 or *ojdbc8.jar* for Oracle 12c in the node directory ``drivers`` described in :ref:`Common Configuration Steps <db_setup_step_3_ref>`.
Database schema name can be set in JDBC URL string e.g. currentSchema=my_schema.

.. _oracle_wallet_ref:

Oracle Wallet
"""""""""""""

A Corda node can also connect to an Oracle database using credentials stored in an Oracle Wallet, with the following changes:

Assuming you have an Oracle Wallet set up in ``~/wallet``, create an entry for the database in your ``tnsnames.ora``, with the
relevant ``<host-address>``, ``<host-port>``, and ``<service-name>``. For example:

  .. sourcecode:: none

    my_database =
      (DEscriptTION =
        (ADDRESS = (PROTOCOL = TCP)(host = <host-address>)(port = <host-port>))
        (CONNECT_DATA =
          (SERVER = DEDICATED)
          (SERVICE_NAME = <service-name>)
        )
      )

Create a ``sqlnet.ora`` in the same directory with the configuration for the wallet. For example:

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

You will be prompted for the wallet password in order to update the wallet.

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

.. _db_setup_configure_node_postgresql_ref:

PostgreSQL
^^^^^^^^^^

Example node configuration file for PostgreSQL:

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
        runMigration = false
    }

Replace the placeholders *<host>*, *<port>*, and *<database>* with appropriate values.
The ``database.schema`` is the database schema name assigned to the user.
The value of ``database.schema`` is automatically wrapped in double quotes to preserve case-sensitivity
(without quotes, PostgresSQL would treat *AliceCorp* as the value *alicecorp*).
This behaviour differs from Corda Open Source where the value is not wrapped in double quotes.

Do not change the default isolation for this database (*READ_COMMITTED*) as the Corda platform has been validated for functional correctness and performance using this level.

Place the PostgreSQL JDBC Driver *42.1.4* version *JDBC 4.2* in the node directory ``drivers`` described in :ref:`Common Configuration Steps <db_setup_step_3_ref>`.

.. _db_setup_step_4_ref:

4. Database configuration
-------------------------

Additional vendor specific database configuration.

SQL Server
^^^^^^^^^^

The database collation should be *case insensitive*, refer to
`Server Configuration documentation <https://docs.microsoft.com/en-us/sql/sql-server/install/server-configuration-collation?view=sql-server-2014&viewFallbackFrom=sql-server-2017>`_.

Oracle
^^^^^^

To allow *VARCHAR2* and *NVARCHAR2* column types to store more than 2000 characters, ensure the database instance is configured to use
extended data types. For example, for Oracle 12.1 refer to `MAX_STRING_SIZE <https://docs.oracle.com/database/121/REFRN/GUID-D424D23B-0933-425F-BC69-9C0E6724693C.htm#REFRN10321>`_.
