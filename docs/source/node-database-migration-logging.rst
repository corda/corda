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
The log consists of two parts, the sequence of the change sets to be run and the sequence of applied change sets.
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
* ID := `Liquibase change set ID <https://www.liquibase.org/documentation/changeset.html>`_
* STATUS := the status for the entire database schema initialisation process and a specific change sets:
  |        "start" - start of the whole database migration process,
  |        "to be run" - the list of change sets before migration run,
  |        "started" - the start of the current change set,
  |        "successful" - the successful completion of a change set,
  |        "error" - an error for the whole process or an error while running a specific change set
*  CODE := a predefined error code, the list will be published later
*  ERROR := a detailed message, for change set error it will be error produced by Liquibase

An example database initialisation log:

 .. sourcecode:: none

    DatabaseInitialisation(id=FrSzFgm2;status="start")
    DatabaseInitialisation(id=FrSzFgm2;change_set_count="2")
    DatabaseInitialisation(id=FrSzFgm2;changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="to be run")
    DatabaseInitialisation(id=FrSzFgm2;changeset="migration/vault-schema.changelog-init.xml::1511451595465-22::R3.Corda";status="to be run")
    DatabaseInitialisation(id=FrSzFgm2;changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="started")
    DatabaseInitialisation(id=FrSzFgm2;changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="successful")
    DatabaseInitialisation(id=FrSzFgm2;changeset="migration/vault-schema.changelog-init.xml::1511451595465-22::R3.Corda";status="started")
    DatabaseInitialisation(id=FrSzFgm2;changeset="migration/vault-schema.changelog-init.xml::1511451595465-22::R3.Corda";status="successful")
    DatabaseInitialisation(id=FrSzFgm2;status="successful")

An example unsuccessful database initialisation log:

 .. sourcecode:: none

    ...
    DatabaseInitialisation(id=FrSzFgm2;changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="started")
    DatabaseInitialisation(id=FrSzFgm2;changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="error";message="Migration failed for change set migration/node-services.changelog-init.xml::1511451595465-39::R3.Corda:      Reason: liquibase.exception.DatabaseException: Table "NODE_MESSAGE_RETRY" not found; SQL statement: ALTER TABLE PUBLIC.node_message_retry ADD CONSTRAINT node_message_retry_pkey PRIMARY KEY (message_id) [42102-197] [Failed SQL: ALTER TABLE PUBLIC.node_message_retry ADD CONSTRAINT node_message_retry_pkey PRIMARY KEY (message_id)]")
    DatabaseInitialisation(id=FrSzFgm2;status="error";message="Migration failed for change set migration/node-services.changelog-init.xml::1511451595465-39::R3.Corda:      Reason: liquibase.exception.DatabaseException: Table "NODE_MESSAGE_RETRY" not found; SQL statement: ALTER TABLE PUBLIC.node_message_retry ADD CONSTRAINT node_message_retry_pkey PRIMARY KEY (message_id) [42102-197] [Failed SQL: ALTER TABLE PUBLIC.node_message_retry ADD CONSTRAINT node_message_retry_pkey PRIMARY KEY (message_id)]")

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