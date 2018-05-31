Release notes
=============

.. note::

    There have been three developer previews, namely Developer Preview 1 through 3. In this section we are discussing the developer preview releases in general unless explicitly
    stated otherwise (e.g., where referencing Developer Preview 3).

Corda Enterprise 3.0 Developer Preview
--------------------------------------
This release is an early access preview of Corda Enterprise 3.0 - a commercial distribution of the Corda blockchain platform designed for mission critical enterprise deployments.

Corda Enterprise 3.0 Developer Preview 3 adds enterprise features to Open Source Corda 3.0 while remaining operationally compatible with it. Please refer to the release notes for Corda 3.0 if upgrading from an earlier version.

Key new features and components
*******************************

* **High Availability**:
  This release introduces the Hot-Cold High Availability configuration for Corda Enterprise nodes, which addresses the following requirements:

  - A logical Corda node continues to be available as long as at least one of the clustered physical nodes is available.
  - No loss, corruption or duplication of data on the ledger due to component outages.
  - Continuity of flows throughout node failures.
  - Support for rolling software upgrades in a live network.

  See :ref:`here <hot-cold_ref>` for further details on how to set-up and operate Hot-Cold node HA.

* **Additional Supported SQL Databases**:
  :ref:`PostgreSQL 9.6 <postgres_ref>`, :ref:`Azure SQL and SQL Server 2017 <sql_server_ref>` are now supported SQL databases.
  Database settings can be specified as part of the node configuration file.
  See :doc:`node-database` for further details.

* **Database Migration Tool**:
  Corda Enterprise ships with a tool for tracking, managing and applying database schema migrations.
  A framework for data migration provides a way to upgrade the version of Corda Enterprise or installed CorDapps while preserving data integrity and service continuity.
  Based on `Liquibase <http://www.liquibase.org/>`_, the tool also allows exporting DDL and data to a file, allowing DBAs to inspect the SQL or apply the SQL statements and to apply them manually if necessary.
  See :ref:`database migration <database_migration_ref>` for further details.

* **New Network Map Service**:
  This release introduces the new network map architecture. The network map service has been redesigned to enable future increased network scalability and redundancy, reduced runtime operational overhead,
  support for multiple notaries, and administration of network compatibility zones (CZ) and business networks.

  A Corda Compatibility Zone (CZ) is defined as a grouping of participants and services (notaries, oracles,
  doorman, network map server) configured within an operational Corda network to be interoperable and compatible with
  each other.
  See :doc:`network-map` for further details.

* **Doorman Service**:
  In order to automate a node's network joining process, a Doorman service has been introduced with this release.
  The Doorman's main purpose is to restrict network access only to those nodes whose identity has been confirmed and their network joining request approved.
  It issues node-level certificates which are then used by other nodes in the network to confirm a node's identity and network permissions.
  See :doc:`running-doorman` for further details.

* **Signing Service**:
  Corda Enterprise 3.0 Developer Preview supports external Hardware Security Module (HSM) integration for certificate issuing and data signing.
  Doorman certificates (together with their keys) can be stored on secured hardware, constraining the way those certificates are accessed. Any usage of those certificates
  (e.g. data signing or node-level certificate generation) falls into a restrictive process that is automatically audited
  and can be configured to involve human-in-the-loop in order to prevent unauthorised access.
  See :doc:`signing-service` for further details.

* **Operational Compatibility With Open Source Corda**
  Operational Compatibility with Corda Enterprise 3.0 Developer Preview 3 provides a baseline for wire stability and compatibility with the open-source Corda.

  Corda Enterprise 3.0 Developer Preview 3 delivers forward compatibility with future versions of Corda Enterprise:

  - Is operationally compatible with future versions of Corda Enterprise.
  - Is upgradeable to future version of Corda Enterprise, preserving transaction and other data.

  Corda Enterprise 3.0 Developer Preview 3 delivers operational compatibility with open-source Corda:

  - Can be used in networks seamlessly transacting with nodes running Corda 3.0 and future versions.
  - Can run CorDapps developed on Corda 3.0 and future versions.
  - Is compatible with ledger data created using Corda 3.0 and future versions.


Further improvements and additions
**********************************

* Corda nodes will now fail to start if unknown property keys are found in configuration files. Any unsupported property can be moved to the newly introduced "custom" section. See :doc:`corda-configuration-file.rst` for further details.
* Property keys with double quotes (e.g. `"key"`) in ``node.conf`` are no longer allowed. See :doc:`corda-configuration-file` for further details.
* CorDapp specific configuration is now supported. ``CordappContext`` now exposes a ``CordappConfig`` object that is populated
  at CorDapp context creation time from a file source during runtime. See :doc:`cordapp-build-systems` for further details.
* Flow framework multi-threading enabled, which provides vastly higher performance than Corda 3.0.
* Additional JMX metrics exported via :ref:`Jolokia for monitoring <jolokia_ref>` and pro-active alert management.
* Corda's web server now has its own ``web-server.conf`` file, separate from the ``node.conf`` used by the Corda node. See :doc:`corda-configuration-file.rst` for further details. :warning:`This module is deprecated and we aim to remove it in the future.`

Known issues
************

The following lists known issues identified in this release. We will endeavour to fix these as part of the upcoming General Availability release of Corda Enterprise.

* The database migration tool unnecessarily prints ``{}`` characters at the end of every log line [ENT-1720].

* Running the database migration tool over a node configured against a local SQLServer instance hosted in Docker results in ``ClassNotFoundException`` exception. [ENT-1717]

* The database migration tool throws ``org.hibernate.AnnotationException`` in presence of ``MappedSchema`` sub-classes that reference other ``MappedSchema`` sub-classes. [ENT-1712]

* The database migration tool does not support relative paths in the JDBC url. [ENT-1698]

* Doorman crashes ungracefully when started with incorrect or no program arguments. Should display a meaningful message instead. [ENT-1661]

* Exception when starting a Corda node against a non-H2 database the first time. [ENT-1635]

  This means the :ref:`database schema management <database_migration_ref>` process should be performed but the exception is confusing.
  Example: ``internal.Node.run - Exception during node startup {} java.lang.IllegalStateException:There are 65 outstanding database changes that need to be run. Please use the provided tools to update the database.``

* ``CommandWithParties`` should be deprecated and not be used. [ENT-1610]

  The involved public keys resolution against known party names is non-deterministic and shouldn't be used as part of transactions' verification.

* Transactions with no inputs and no time window still get "requesting signature by notary service" progress update despite no notarisation is actually involved. [ENT-1574]

* Array of ``JoinColumn`` values for ``JoinColumns`` annotated entities result in compilation error due to Kotlin 1.1 API version. [CORDA-1269]

  Example: ``@JoinColumns(value = arrayOf(JoinColumn(name = "cash_txid"), JoinColumn(name = "cash_outidx")))`` does not work.
  Workaround 1: ``JoinColumns(value = *arrayOf(JoinColumn(name = "cash_txid"), JoinColumn(name = "cash_outidx")))`` works.
  Workaround 2: ``@JoinColumns(JoinColumn(name = "cash_txid"), JoinColumn(name = "cash_outidx"))`` also works.

* Coin selection (eg. cash spending) soft locking may deadlock, especially when used together with multi-threading. [ENT-934]

  Reserving of states (soft locking) is automatically applied to fungible states before transactions are notarised, to preemptively avoid notarisation clashes to ensure that no two transactions attempt to spend the same fungible assets. Switching off multithreading may reduce the likelihood of failure, to disable multi-threading ensure ``enterpriseConfiguration.useMultiThreadedSMM`` in the node.conf is set to ``false``.

Further notes
*************

As per previous major releases, we have provided a comprehensive upgrade notes (:doc:`upgrade-notes`) to ease the upgrade
of CorDapps to Corda Enterprise 3.0 Developer Preview. In line with our commitment to API stability, code level changes
are fairly minimal.

From a build perspective, switching CorDapps built using Corda 3.0 to Corda Enterprise 3.0 Developer Preview is mostly effortless,
and simply requires setting two Gradle build file variables:

.. sourcecode:: shell

  ext.corda_release_version = 'R3.CORDA-3.0.0-DEV-PREVIEW'
  ext.corda_release_distribution = 'com.r3.corda'

Please note this release is distributed under the evaluation license and should not be used in a Production environment yet.

We look forward to hearing your feedback on this release!