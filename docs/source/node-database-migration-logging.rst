.. _database-schema-migration-logging:

Database Schema Migration Logging
=================================

Database migrations for the Corda nodes internal database objects are recorded in the node's default log file.

The detailed, unstructured logs produced by Liquibase can be enabled by providing additional log4j2 configuration.

The setup of CorDapps' custom tables (which only happens automatically when using H2) are not recorded in the node's logs
by default. Enabling the Hibernate logger will produce these logs (see :ref:`node-administration-logging`).

Log format
^^^^^^^^^^

The migration logs are in a fixed format, prefixed with `DatabaseInitialisation`.
The log consists of two parts, the sequence of the change sets to be run and the progress of change sets execution.
A change set is a single database schema change, defined in a Liquibase script, which may contain one or more DDL statements.

The log sequence:

.. sourcecode:: none

 [ START ]
 [ CHANGE_SETS_COUNT ]
 [ CHANGE_SETS ]
 [ PROGRESS ]
 [ END | ERROR ]

where:

* START := a log line denoting the start of a database migration
* CHANGE_SETS_COUNT := the number of change sets needed to be applied to the database
* CHANGE_SETS := a list of all migrations to run, each change set on a separate line
* PROGRESS := log lines before and after running each change set
* END := a line denoting the successful end of database initialisation
* ERROR := information about errors that occurred during migration, if any

The log line format:

.. sourcecode:: none

    DatabaseInitialisation(id=<RANDOM_ID>;[changeset=<ID>];status=<STATUS>[;error_code=<CODE>;message=<ERROR>])

where:

* RANDOM_ID := a random generated value identifying the set of migrations, to aid with log parsing
* ID := Liquibase `change set ID <https://www.liquibase.org/documentation/changeset.html>`_
* STATUS := the status for the entire database schema initialisation process and a specific change sets:
  |        "start" - start of the whole database migration process,
  |        "to be run" - the list of change sets before migration run,
  |        "started" - the start of the current change set,
  |        "successful" - the successful completion of a change set,
  |        "error" - an error for the whole process or an error while running a specific change set
*  CODE := a predefined error code (see :ref:`node-database-migration-logging-error-codes`)
*  ERROR := a detailed message, for change set error it will be error produced by Liquibase

An example database initialisation log:

 .. sourcecode:: none

    DatabaseInitialisation(id="FrSzFgm2";status="start")
    DatabaseInitialisation(id="FrSzFgm2";change_set_count="2")
    DatabaseInitialisation(id="FrSzFgm2";changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="to be run")
    DatabaseInitialisation(id="FrSzFgm2";changeset="migration/vault-schema.changelog-init.xml::1511451595465-22::R3.Corda";status="to be run")
    DatabaseInitialisation(id="FrSzFgm2";changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="started")
    DatabaseInitialisation(id="FrSzFgm2";changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="successful")
    DatabaseInitialisation(id="FrSzFgm2";changeset="migration/vault-schema.changelog-init.xml::1511451595465-22::R3.Corda";status="started")
    DatabaseInitialisation(id="FrSzFgm2";changeset="migration/vault-schema.changelog-init.xml::1511451595465-22::R3.Corda";status="successful")
    DatabaseInitialisation(id="FrSzFgm2";status="successful")

An example unsuccessful database initialisation log:

 .. sourcecode:: none

    ...
    DatabaseInitialisation(id="FrSzFgm2";changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="started")
    DatabaseInitialisation(id="FrSzFgm2";changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="error";error_code="9";message="Migration failed for change set migration/node-services.changelog-init.xml::1511451595465-39::R3.Corda:      Reason: liquibase.exception.DatabaseException: Table "NODE_MESSAGE_RETRY" not found; SQL statement: ALTER TABLE PUBLIC.node_message_retry ADD CONSTRAINT node_message_retry_pkey PRIMARY KEY (message_id) [42102-197] [Failed SQL: ALTER TABLE PUBLIC.node_message_retry ADD CONSTRAINT node_message_retry_pkey PRIMARY KEY (message_id)]")
    DatabaseInitialisation(id="FrSzFgm2";status="error";error_code="9";message="Migration failed for change set migration/node-services.changelog-init.xml::1511451595465-39::R3.Corda:      Reason: liquibase.exception.DatabaseException: Table "NODE_MESSAGE_RETRY" not found; SQL statement: ALTER TABLE PUBLIC.node_message_retry ADD CONSTRAINT node_message_retry_pkey PRIMARY KEY (message_id) [42102-197] [Failed SQL: ALTER TABLE PUBLIC.node_message_retry ADD CONSTRAINT node_message_retry_pkey PRIMARY KEY (message_id)]")

.. _node-database-migration-logging-error-codes:

Error codes
^^^^^^^^^^^

As mentioned above, an error log entry includes a numeric ``<CODE>`` preceded by the ``error_code=`` label. These error codes serve
as predefined categories grouping potentially many specific errors.  The following codes are currently in use:

* 1 - error not belonging to any other category;
* 2 - missing database driver or an invalid value for the dataSourceClassName property, for example:

  .. sourcecode:: none

      DatabaseInitialisation(id=bMmdUxxZ;status="error";error_code="2";message="Could not find the database driver class. Please add it to the 'drivers' folder. See: https://docs.corda.net/corda-configuration-file.html")

* 3 - invalid data source property, for example:

  .. sourcecode:: none

      DatabaseInitialisation(id=jcaavDAO;status="error";error_code="3";message="Could not create the DataSource: Property invalid_property does not exist on target class org.postgresql.ds.PGSimpleDataSource")

* 4 - initialization error, for example:

  .. sourcecode:: none

      DatabaseInitialisation(id=r52KsERT;status="error";error_code="4";message="Could not connect to the database. Please check your JDBC connection URL, or the connectivity to the database.")

* 5 - missing database migration script, for example:

  .. sourcecode:: none

      DatabaseInitialisation(id=i9JkXGJP;status="error";error_code="5";message="Could not create the DataSource: Could not find Liquibase database migration script migration/common.changelog-master.xml. Please ensure the jar file containing it is deployed in the cordapps directory.")

* 6 - error while parsing database migration script, for example:

  .. sourcecode:: none

      DatabaseInitialisation(id=GdQy2mdL;status="error";error_code="6";message="Could not create the DataSource: Error parsing master.changelog.json")

* 7 - invalid SQL statement in database migration script, for example:

  .. sourcecode:: none

      DatabaseInitialisation(id=Klvw19Cp;status="error";error_code="7";message="Could not create the DataSource: Migration failed for change set migration/vault-schema.changelog-v8.xml::create-external-id-to-state-party-view::R3.Corda:      Reason: liquibase.exception.DatabaseException: ERROR: syntax error at or near \"choose\"   Position: 48 [Failed SQL: CREATE VIEW my_schema.v_pkey_hash_ex_id_map AS choose                 state_party.public_key_hash,                 state_party.transaction_id,                 state_party.output_index,                 pk_hash_to_ext_id_map.external_id             from state_party             join pk_hash_to_ext_id_map             on state_party.public_key_hash = pk_hash_to_ext_id_map.public_key_hash]")

* 8 - invalid SQL type in database migration script, for example:

  .. sourcecode:: none

      DatabaseInitialisation(id=YrhVTZsn;status="error";error_code="8";message="Could not create the DataSource: Migration failed for change set migration/node-services.changelog-init.xml::1511451595465-14::R3.Corda:      Reason: liquibase.exception.DatabaseException: ERROR: type \"biginteger\" does not exist   Position: 55 [Failed SQL: CREATE TABLE my_schema.node_message_retry (message_id BIGINTEGER NOT NULL, message OID, recipients OID)]")

* 9 - unable to apply a change set due to its incompatibility with the current database state, for example:

  .. sourcecode:: none

      DatabaseInitialisation(id=liyxdqHP;status="error";error_code="9";message="Could not create the DataSource: Migration failed for change set migration/vault-schema.changelog-init.xml::1511451595465-22::R3.Corda:      Reason: liquibase.exception.DatabaseException: ERROR: relation \"vault_fungible_states\" already exists [Failed SQL: CREATE TABLE my_schema.vault_fungible_states (output_index INTEGER NOT NULL, transaction_id VARCHAR(64) NOT NULL, issuer_name VARCHAR(255), issuer_ref BYTEA, owner_name VARCHAR(255), quantity BIGINT)]")

* 10 - uncategorised exception when applying a change set;
* 11 - outstanding database migration change sets, for example:

  .. sourcecode:: none

      DatabaseInitialisation(id=x0gKBFBQ;status="error";error_code="10";message="Incompatible database schema version detected, please run the node with configuration option database.initialiseSchema=true. Reason: There are 95 outstanding database changes that need to be run.")

* 12 - mapped schema incompatible with database management script, for example:

  .. sourcecode:: none

      DatabaseInitialisation(id=x0gANuBQ;status="error";error_code="11";message="Exception during node startup: Incompatible schema change detected. Please run the node with database.initialiseSchema=true. Reason: Schema-validation: missing table [node_identities] [errorCode=1q9b16q, moreInformationAt=https://errors.corda.net/OS/5.0-SNAPSHOT/1q9b16q]")

Native Liquibase logs
^^^^^^^^^^^^^^^^^^^^^

The native Liquibase logs are disabled by default.
They can be enabled by adding an extra log4j2 file with 'INFO' log level for the 'liquibase' logger:

.. sourcecode:: xml

    <?xml version="1.0" encoding="UTF-8"?>
    <Configuration status="INFO">
        <Loggers>
            <Logger name="liquibase" additivity="false" level="INFO"/>
        </Loggers>
    </Configuration>

When starting the Corda node the extra config file need to be provided:

.. sourcecode:: bash

    java -jar -Dlog4j.configurationFile=log4j2.xml,path_to_custom_file.xml corda.jar

Enabling custom logging is also described in :ref:`node-administration-logging`.