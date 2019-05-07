Node database
=============

The Corda platform, and the installed CorDapps store their data in a relational database (see :doc:`api-persistence`).

Corda Enterprise supports a range of commercial 3rd party databases: Azure SQL, SQL Server, Oracle, and PostgreSQL.
This document provides an overview of required database permissions, related ways to create database schema objects,
and explains how a Corda node verifies the correct database schema version.

.. _node_database_user_permissions_ref:

Database user permissions
-------------------------

A Corda node connects to a database using a single database user, and stores data within a single database schema (a schema namespace).
A database schema can not be shared between two different nodes (except for :doc:`hot-cold-deployment`).
Depending on how the schema objects are created, a Corda node can connect to the database with a different set of database permissions:

  * **restricted permissions**

    This grants the database user access to DML execution only (to manipulate data itself e.g. select/delete rows),
    and a database administrator needs to create database schema objects before running the Corda node.
    This permission set is recommended for a Corda node in a production environment (including :doc:`hot-cold-deployment`).

  * **administrative permissions**

    This grants the database user full access to a Corda node, such that it can execute both DDL statements
    (to define data structures/schema content e.g. tables) and DML queries (to manipulate data itself e.g. select/delete rows).
    This permission set is more permissive and should be used with caution in production environments.
    A Corda node with full control of the database schema can create or upgrade schema objects automatically upon node startup.
    This eases the operational maintenance for development and testing.

Database setup for production systems (with **restricted permissions**) is described in :doc:`node-database-admin`,
and the recommended setup for development/testing environments are described in :doc:`node-database-developer`.

Database schema objects management
----------------------------------

Database DDL scripts defining database tables (and other schema objects) are embedded inside the Corda distribution (``corda.jar`` file)
or within the CorDapp distributions (a JAR file). Therefore Corda, and custom CorDapps are shipped without separate DDL scripts for each database vendor.
Whenever a node operator or database administrator needs to obtain a DDL script to be run, they can use the Corda Database Management Tool.
The tool, among other functions, outputs the DDL script which is compatible with the Corda release
and the database which the tool was running against.
Depending on :ref:`database user permissions <node_database_user_permissions_ref>` a Corda node may be configured to create database tables
(and other schema objects) automatically upon startup (and subsequently update tables).

.. _liquibase_ref:

DDL scripts are defined in a cross database syntax and grouped in change sets.
When a Corda node starts, it compares the list of change sets recorded in the database with the list embedded inside the Corda node
and associated CorDapps. Depending on the outcome and the node configuration, it will stop and report any differences or will create/update
any missing database objects.
Internally, the Corda node and Corda Database Management Tool use `Liquibase library/tool <http://www.liquibase.org>`_
for versioning database schema changes.

Liquibase is a tool that implements an automated, version based database migration framework with support for a large number of databases.
It works by maintaining a list of applied changesets. A changeset can be something very simple like adding a new column to a table.
It stores each executed changeset with columns like id, author, timestamp, description, md5 hash, etc in a table called ``DATABASECHANGELOG``.
This changelog table will be read every time a migration command is run to determine
what change-sets need to be executed. It represents the "version" of the database (the sum of the executed change-sets at any point).
Change-sets are scripts written in a supported format (xml, yml, sql), and should never be modified once they have been executed.
Any necessary correction should be applied in a new change-set.
Understanding `how Liquibase works <https://www.thoughts-on-java.org/database-migration-with-liquibase-getting-started>`_
is highly recommended for understanding how database migrations work in Corda.

Default Corda node configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

By default, a node will *not* attempt to execute database migration scripts at startup (even when a new version has been deployed),
but will check the database "version" and halt if the database is not in sync with the node, to avoid data corruption.
To bring the database to the correct state we provide a :ref:`database-management-tool-ref`.
This setup/procedure is recommended for production systems.

Running the migration at startup automatically can be configured by specifying true in the ``database.runMigration``
node configuration setting (default behaviour is false).
We recommend enabling database schema auto-creation/upgrade for development or test purposes only.
It is safe to run at startup if you have implemented the usual best practices for database management
(e.g. running a backup before installing a new version).

.. _database-management-tool-ref:

Database Management Tool
^^^^^^^^^^^^^^^^^^^^^^^^

The database management tool is distributed as a standalone JAR file named ``tools-database-manager-${corda_version}.jar``.
It is intended to be used by Corda Enterprise node administrators.

The following sections document the available subcommands suitable for a node operator or database administrator.

Executing a dry run of the SQL migration scripts
""""""""""""""""""""""""""""""""""""""""""""""""

The ``dry-run`` subcommand can be used to output the database migration to the specified output file or to the console.
The output directory is the one specified by the ``--base-directory`` parameter.

Usage:

.. code-block:: shell

    database-manager dry-run [-hvV] [--doorman-jar-path=<doormanJarPath>]
                             [--logging-level=<loggingLevel>] [--mode=<mode>]
                             -b=<baseDirectory> [-f=<configFile>] [<outputFile>]

The ``outputFile`` parameter can be optionally specified determine what file to output the generated SQL to, or use
``CONSOLE`` to output to the console.

Additional options:

* ``--base-directory``, ``-b``: (Required) The node working directory where all the files are kept (default: ``.``).
* ``--config-file``, ``-f``: The path to the config file. Defaults to ``node.conf``.
* ``--mode``: The operating mode. Possible values: NODE, DOORMAN. Default: NODE.
* ``--doorman-jar-path=<doormanJarPath>``: The path to the doorman JAR.
* ``--verbose``, ``--log-to-console``, ``-v``: If set, prints logging to the console as well as to a file.
* ``--logging-level=<loggingLevel>``: Enable logging at this level and higher. Possible values: ERROR, WARN, INFO, DEBUG, TRACE. Default: INFO.
* ``--help``, ``-h``: Show this help message and exit.
* ``--version``, ``-V``: Print version information and exit.

Executing SQL migration scripts
"""""""""""""""""""""""""""""""

The ``execute-migration`` subcommand runs migration scripts on the node's database.

Usage:

.. code-block:: shell

    database-manager execute-migration [-hvV] [--doorman-jar-path=<doormanJarPath>]
                                       [--logging-level=<loggingLevel>]
                                       [--mode=<mode>] -b=<baseDirectory>
                                       [-f=<configFile>]

* ``--base-directory``, ``-b``: (Required) The node working directory where all the files are kept (default: ``.``).
* ``--config-file``, ``-f``: The path to the config file. Defaults to ``node.conf``.
* ``--mode``: The operating mode. Possible values: NODE, DOORMAN. Default: NODE.
* ``--doorman-jar-path=<doormanJarPath>``: The path to the doorman JAR.
* ``--verbose``, ``--log-to-console``, ``-v``: If set, prints logging to the console as well as to a file.
* ``--logging-level=<loggingLevel>``: Enable logging at this level and higher. Possible values: ERROR, WARN, INFO, DEBUG, TRACE. Default: INFO.
* ``--help``, ``-h``: Show this help message and exit.
* ``--version``, ``-V``: Print version information and exit.

Releasing database locks
""""""""""""""""""""""""

The ``release-lock`` subcommand forces the release of database locks. Sometimes, when a node or the database management
tool crashes while running migrations, Liquibase will not release the lock. This can happen during some long
database operations, or when an admin kills the process (this cannot happen during normal operation of a node,
only `during the migration process <http://www.liquibase.org/documentation/databasechangeloglock_table.html>`_.

Usage:

.. code-block:: shell

    database-manager release-lock [-hvV] [--doorman-jar-path=<doormanJarPath>]
                                  [--logging-level=<loggingLevel>] [--mode=<mode>]
                                  -b=<baseDirectory> [-f=<configFile>]

Additional options:

* ``--base-directory``, ``-b``: (Required) The node working directory where all the files are kept (default: ``.``).
* ``--config-file``, ``-f``: The path to the config file. Defaults to ``node.conf``.
* ``--mode``: The operating mode. Possible values: NODE, DOORMAN. Default: NODE.
* ``--doorman-jar-path=<doormanJarPath>``: The path to the doorman JAR.
* ``--verbose``, ``--log-to-console``, ``-v``: If set, prints logging to the console as well as to a file.
* ``--logging-level=<loggingLevel>``: Enable logging at this level and higher. Possible values: ERROR, WARN, INFO, DEBUG, TRACE. Default: INFO.
* ``--help``, ``-h``: Show this help message and exit.
* ``--version``, ``-V``: Print version information and exit.

Database Manager shell extensions
"""""""""""""""""""""""""""""""""

The ``install-shell-extensions`` subcommand can be used to install the ``database-manager`` alias and auto completion for
bash and zsh. See :doc:`cli-application-shell-extensions` for more info.


.. note:: When running the database management tool, it is preferable to use absolute paths when specifying the "base-directory".

.. warning:: It is good practice for node operators to back up the database before upgrading to a new version.

Troubleshooting
"""""""""""""""

Symptom: Problems acquiring the lock, with output like this:

    Waiting for changelog lock....
    Waiting for changelog lock....
    Waiting for changelog lock....
    Waiting for changelog lock....
    Waiting for changelog lock....
    Waiting for changelog lock....
    Waiting for changelog lock....
    Liquibase Update Failed: Could not acquire change log lock.  Currently locked by SomeComputer (192.168.15.X) since 2013-03-20 13:39
    SEVERE 2013-03-20 16:59:liquibase: Could not acquire change log lock.  Currently locked by SomeComputer (192.168.15.X) since 2013-03-20 13:39
    liquibase.exception.LockException: Could not acquire change log lock.  Currently locked by SomeComputer (192.168.15.X) since 2013-03-20 13:39
            at liquibase.lockservice.LockService.waitForLock(LockService.java:81)
            at liquibase.Liquibase.tag(Liquibase.java:507)
            at liquibase.integration.commandline.Main.doMigration(Main.java:643)
            at liquibase.integration.commandline.Main.main(Main.java:116)

Advice: See `this StackOverflow question <https://stackoverflow.com/questions/15528795/liquibase-lock-reasons>`_.
You can run ``java -jar tools-database-manager-4.0.jar --base-directory /path/to/node --release-lock`` to force Liquibase to give up the lock.


Node database tables
--------------------

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

For more details, see :doc:`node-database-tables`.

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
