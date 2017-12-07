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

Integration test can be parameterised to run against any remote database (by default, integration tests use in-memory H2 instances).
For the purpose of testing other relational database providers or different database setups (for example, H2 in server mode),
we introduce an optional system property called ``databaseProvider`` which is resolved at run-time to load a configuration file on the classpath with the
name ``$databaseProvider.conf`` containing database configuration properties that override the default H2 settings
defined in the general node configuration file (see ``reference.conf``).
Integration test runs predefined set of SQL setup scripts selected for a specific database provider by ``test.db.script.dir``.
SQL scripts is a template which contains standard SQL DDL statements with a ${schema} placeholder. An integration test runs the SQL scripts
for all nodes involed in the test and replaces ${schema} with appropriate value derived from node name.
SQL templates files are executed at different stage of an integration test:
``db-global-setup.sql``- before a test class (@BeforeClass), should create database users, schema and permissions
``db-setup.sql`` - before a test (@Before), should clean up/drop tables
``db-cleanup.sql` - after a test (@After), may clean up/drop tables
``db-global-cleanup.sql`` - after a test class (@AfterClass), may drop user and schema
Depends on the database providers not each SQL file is present (e.g. db-setp always deletes tabels so db-cleanp is not needed).

The setup ensures that all nodes involved in a single integration test use different database users to achieve database separation.
The  data source configuration files (denote by ``databaseProvider``) define user and schema by ${nodeOrganizationName} placeholder.
At a runtime the node resolves the placeholder to its organization name.


To run integration tests against a remote database provide these system properties:

- ``databaseProvider`` - a template dataSource/database configuration for a node, accepted values [“integration-azure-sql”, “integration-sql-server”]

- ``test.db.script.dir`` - a relative directory path with SQL script templates for a given database,
                        accepted values [“database-scripts/azure-sql”, “database-scripts/sql-server”]

- ``test.db.admin.user`` - a database user to run SQL setup scripts, the user need to have permissions
                            to create other users and grant them permissions
- ``test.db.admin.password`` -  a password for the database user to run SQL scripts

Provided configuration file ``$databaseProvider.conf``file () contains a specific JDBC connection string with a dummy database host,
 the following properties can override JDBC connection string and password:

- ``dataSourceProperties.dataSource.url`` - specify full JDBC connection string use by a node to connect to database

- ``dataSourceProperties.dataSource.password``  - optional setup, currently SQL scripts creates users with a given hardcoded
                                                        password with matches one in node configuration
All defaults are taken from the ``reference.conf`` file.

Example running Gradle integration tests task against Azure SQL database running at ``mycordadb.database.windows.net``:
``./gradlew integrationTest -DdataSourceProperties.dataSource.url="jdbc:sqlserver://mycordadb.database.windows.net:1433;databaseName=mycordadb;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30"
-DdatabaseProvider=integration-sql-azure -Dtest.db.admin.user=dbuser@mycordadb -Dtest.db.admin.password='paSSword(!'
 -Dtest.db.script.dir=database-scripts/sql-azure --info``



