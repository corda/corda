Database management
===================

The Corda platform, and the installed CorDapps store their data in a relational database.

Corda Enterprise supports a range of commercial 3rd party databases: Azure SQL, SQL Server, Oracle and PostgreSQL.

The documentation contains the required database user permission and schema creation steps
for production systems :doc:`node-database-admin` for a new Corda installation
or :ref:`Database upgrade<node_upgrade_notes_update_database_ref>` for upgrading Corda nodes.
Database schema updates may be also required when :doc:`node-operations-cordapp-deployment`
or :doc:`node-operations-upgrade-cordapps`.

For development/testing purposes :doc:`node-database-developer` covers database setup with simplified user permissions.
:doc:`node-database` explains the differences between both setups.

Corda Enterpise is released with the :ref:`Database Management Tool <database-management-tool-ref>`.
The tool is distributed as a standalone JAR file named ``tools-database-manager-${corda_version}.jar``.
It is intended to be used by Corda Enterprise node administrators during database schema creation.

It can be also used by CorDapp developers as a helper to create Liquibase database migration scripts.
Any CorDapp deployed onto a Corda Enteprise node, which stores data in a custom tables,
requires embedded DDL scripts written in a cross database manner :doc:`database-management`.
