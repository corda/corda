Testing Corda
=============

Automated Tests
---------------

Corda has a maintained suite of tests that any contributing developers must maintain and add to if new code has been added.

There are several distinct test suites each with a different purpose;

**Unit tests**: These are traditional unit tests that should only test a single code unit, typically a method or class.

**Integration tests**: These tests should test the integration of small numbers of units, preferably with mocked out services.

**Smoke tests**: These are full end to end tests which start a full set of Corda nodes and verify broader behaviour.

**Other**: These include tests such as performance tests, stress tests, etc, and may be in an external repo.

These tests are mostly written with JUnit and can be run via ``gradle``. On windows run ``gradlew test integrationTest
smokeTest`` and on unix run ``./gradlw test integrationTest smokeTest`` or any combination of these three arguments.

Before creating a pull request please make sure these pass.

Manual Testing
--------------

Manual testing would ideally be done for every set of changes merged into master, but practically you should manually test
anything that would be impacted by your changes. The areas that usually need to be manually tested and when are below;

**Node startup** - changes in the ``node`` or ``node:capsule`` project in both the Kotlin or gradle or the ``cordformation`` gradle plugin.

**Sample project** - changes in the ``samples`` project. eg; changing the IRS demo means you should manually test the IRS demo.

**Explorer** - changes to the ``tools/explorer`` project.

**Demobench** - changes to the ``tools/demobench`` project.

How to manually test each of these areas differs and is currently not fully specified. For now the best thing to do is
ensure the program starts, that you can interact with it, and that no exceptions are generated in normal operation.

TODO: Add instructions on manual testing

External Database Testing
-------------------------

Integration tests can be parameterised to run against any remote database (instead of the default embeded H2 instance).

When running Gradle task `integrationTest`, a combination of several system properties (passed as `-Dproperty=...`) can modify the node default JDBC setting and trigger a database setup before and after each test.
The property ``custom.databaseProvider`` is resolved at run-time to load a configuration file on the classpath with the
name `$custom.databaseProvider.conf` containing database configuration properties. These settings override the default H2 ones
defined in the node configuration file (see ``reference.conf``).
Integration test runs predefined set of SQL setup scripts selected for a specific database provider by ``test.db.script.dir``.
SQL scripts are templates which contain SQL DDL statements with the `${schema}` placeholder.
Integration tests run the script for all nodes involved in the test and replace `${schema}` with the appropriate value, derived from a node name. SQL templates files are executed at different stage of the integration test:

- ``db-global-setup.sql``- before a test class (@BeforeClass), should create database users, schema and permissions
- ``db-setup.sql`` - before a test (@Before), should clean up/drop tables
- ``db-cleanup.sql`` - after a test (@After), may clean up/drop tables
- ``db-global-cleanup.sql`` - after a test class (@AfterClass), may drop user and schema

Not all stages need to be present e.g. when ``db-setup.sql`` deletes all tables before a test then ``db-cleanup.sql`` after the test is not needed.

The setup ensures that all nodes involved in a single integration test use different database users to achieve database separation.
The configuration file (denoted by the ``custom.databaseProvider`` property) define a user and a schema as `${custom.nodeOrganizationName}` value.
The value is a placeholder which is resolved at runtime to a node organization name.

To run integration tests against a remote database provide these system properties:

- ``custom.databaseProvider`` -  the predefined configuration template for a node, the template is a name of the file under resources` folder and a switch to add JDBC driver runtime dependency, accepted values: `integration-azure-sql`, `integration-sql-server`, `integration-oracle-11`, `integration-oracle`, `integration-postgress`

- ``test.db.script.dir`` - the path to predefined set of SQL script templates for a given database, accepted values: `database-scripts/azure-sql`, `database-scripts/sql-server`, `database-scripts/oracle`, `database-scripts/postgress`

- ``test.db.admin.user`` - a database user to run SQL setup scripts, the user needs to have permissions to create other users and grant them permissions

- ``test.db.admin.password`` -  a password for the database user to run SQL scripts

- ``corda.dataSourceProperties.dataSource.url`` - specify full JDBC connection string use by a node to connect to database, JDBC URL provided by the predefined configuration file (by ``databaseProvider``) doesn't contain specific host names and port

- ``corda.dataSourceProperties.dataSource.password`` - optional parameter, currently a database user password in the SQL setup script ``test.db.script.dir`` matches one in the node configuration file ``test.db.script.dir``


Example running Gradle integration tests task against Azure SQL database at `mycordadb.database.windows.net` host:

.. code:: bash

    ./gradlew integrationTest -Dcustom.databaseProvider=integration-sql-azure \
    -Dcorda.dataSourceProperties.dataSource.url="jdbc:sqlserver://mycordadb.database.windows.net:1433;databaseName=mycordadb;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30" \
    -Dtest.db.admin.user=dbuser@mycordadb \
    -Dtest.db.admin.password='paSSword(!' \
    -Dtest.db.script.dir=database-scripts/sql-azure



