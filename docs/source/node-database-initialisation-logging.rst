.. _database-initialisation-logging:

Database Schema Initialisation Logging
======================================

Database initialisation and upgrade for Corda internal tables (and other objects like sequences)
is recorded in the node's default log file.
The default logs record each change set name to be run (from Liquibase script files) in a structured format.

The detailed, unstructured logs produced by Liquibase can be enabled by providing an additional log4j2 configuration.

The setup of CorDapps' custom tables is not recorded in the node's logs explicitly
(it would require enabling Hibernate logger category).

Default logs
^^^^^^^^^^^^

The default logs are denoted by 'databaseInitialisation' category name and starts with the same prefix.
The log consist of two parts, the sequence of the change sets to be run and the sequence of applied change sets.
Change set is the smallest unit of a database schema change defined in a Liquibase script,
a single change set may contain one or more DDL statements.
The log sequence:

.. sourcecode:: none

 [ START ]
 [ CHANGE_SETS_COUNT ]
 [ CHANGE_SETS ]
 [ PROGRESS ]
 [ END | ERROR ]

where:

* START := a log line denoting the start of database schema setup or update
* CHANGE_SETS_COUNT := number of change sets detected as needed to be applied against the database
* CHANGE_SETS := a list of all migrations to run, specified one by log line
* PROGRESS := log lines before and after running each change set
* END := a line denoting the successful end of database initialisation
* ERROR := any error related to a database connection and setup e.g. the node database misconfiguration preventing the node to start, or an error related to creating database schema, a list of predefined error codes will be documented

The log line format:

.. sourcecode:: none

    databaseInitialisation(id=<RANDOM_ID>;[changeset=<ID>];status=<STATUS>[;error_code=<CODE>;message=<ERROR>])

where:

* RANDOM_ID := random generated value allowing to grep all logs from withing a single migration run
* ID := Liquibase change set ID
* STATUS := the status for the entire database schema initialisation process and a specific change sets:
  |        "start" - start og the whole database initialisation process,
  |        "to be run" - the list of change sets before migration run,
  |        "started" - the change set is about to be run,
  |        "successful" - the change sets has been run,
  |        "error" - an error for the whole process or an error while running a specific change set
*  CODE := a predefined error code, the list will be published later
*  ERROR := a detailed message, for change set error it will be error produced by Liquibase

The example database initialisation logs:

 .. sourcecode:: none

    databaseInitialisation(id=FrSzFgm2;status="start")
    databaseInitialisation(id=FrSzFgm2;change_set_count="2")
    databaseInitialisation(id=FrSzFgm2;changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="to be run")
    databaseInitialisation(id=FrSzFgm2;changeset="migration/vault-schema.changelog-init.xml::1511451595465-22::R3.Corda";status="to be run")
    databaseInitialisation(id=FrSzFgm2;changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="started")
    databaseInitialisation(id=FrSzFgm2;changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="successful")
    databaseInitialisation(id=FrSzFgm2;changeset="migration/vault-schema.changelog-init.xml::1511451595465-22::R3.Corda";status="started")
    databaseInitialisation(id=FrSzFgm2;changeset="migration/vault-schema.changelog-init.xml::1511451595465-22::R3.Corda";status="successful")
    databaseInitialisation(id=FrSzFgm2;status="successful")

The example unsuccessful database initialisation logs:

 .. sourcecode:: none

    ...
    databaseInitialisation(id=FrSzFgm2;changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="started")
    databaseInitialisation(id=FrSzFgm2;changeset="migration/common.changelog-init.xml::1511451595465-1.1::R3.Corda";status="error";message="Migration failed for change set migration/node-services.changelog-init.xml::1511451595465-39::R3.Corda:      Reason: liquibase.exception.DatabaseException: Table "NODE_MESSAGE_RETRY" not found; SQL statement: ALTER TABLE PUBLIC.node_message_retry ADD CONSTRAINT node_message_retry_pkey PRIMARY KEY (message_id) [42102-197] [Failed SQL: ALTER TABLE PUBLIC.node_message_retry ADD CONSTRAINT node_message_retry_pkey PRIMARY KEY (message_id)]")
    databaseInitialisation(id=FrSzFgm2;status="error";message="Migration failed for change set migration/node-services.changelog-init.xml::1511451595465-39::R3.Corda:      Reason: liquibase.exception.DatabaseException: Table "NODE_MESSAGE_RETRY" not found; SQL statement: ALTER TABLE PUBLIC.node_message_retry ADD CONSTRAINT node_message_retry_pkey PRIMARY KEY (message_id) [42102-197] [Failed SQL: ALTER TABLE PUBLIC.node_message_retry ADD CONSTRAINT node_message_retry_pkey PRIMARY KEY (message_id)]")

Detailed logs
^^^^^^^^^^^^^

The native Liquibase logs are disabled by default.
They can be enabled by adding an extra log4j2 file with 'INFO' log level for 'liquibase' package:

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