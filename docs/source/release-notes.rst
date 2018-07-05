Release Notes
=============

|release|
---------

This release is the first official release of Corda Enterprise - a commercial distribution of the open source Corda blockchain platform, designed for mission critical enterprise deployments.
|release| supports Linux for production deployments, with Windows and Mac OS support for development and demonstration purposes only. Please refer to product documentation for details.

|release| is operationally compatible with Open Source Corda 3.x while providing enterprise-grade features and performance.

Key new features and components
*******************************

**High Availability**

  This release introduces the Hot-Cold High Availability configuration for Corda Enterprise nodes, which addresses the following requirements:

  - A logical Corda node continues to be available as long as at least one of the clustered physical nodes is available.
  - No loss, corruption or duplication of data on the ledger due to component outages.
  - Continuity of flows throughout node failures.
  - Support for rolling software upgrades in a live network.

  See :ref:`here <hot_cold_ref>` for further details on how to set-up and operate Hot-Cold node HA.

**Additional Supported SQL Databases**

  :ref:`PostgreSQL 9.6 <postgres_ref>`, :ref:`Azure SQL and SQL Server 2017 <sql_server_ref>` and :ref:`Oracle 11g and 12c <oracle_ref>` are now supported SQL databases.
  Database settings can be specified as part of the node configuration file.
  See :doc:`node-database` for further details.

**Database Management and Migration Tool**

  |release| ships with a tool for tracking, managing and applying database schema migrations.
  A framework for data migration provides a way to upgrade the version of Corda Enterprise or installed CorDapps while preserving data integrity and service continuity. It is also used to prepare a database for first use with Corda Enterprise.
  Based on `Liquibase <http://www.liquibase.org/>`_, the tool also allows exporting DDL and data to a file, allowing DBAs to inspect the SQL or apply the SQL statements and to apply them manually if necessary.
  See :ref:`database migration <database_migration_ref>` for further details.

**Multi-threaded Flow Processing**

  |release| enables multi-threaded processing of flows, resulting in vastly higher performance than Corda 3.0. In Corda flows are processed on a single thread and
  thus individual flow steps are not processed concurrently. Corda Enterprise is able to process multiple flow steps concurrently, the number of which is only limited by
  available CPU cores, memory and database configuration.  This allows Corda Enterprise to process a greater number
  of flows and transactions in a given time frame than Open Source Corda and to truly take advantage of large server
  / VM configurations. The number of operating system threads that are used is determined by the ``flowThreadPoolSize`` configuration property.
  See :doc:`corda-configuration-file` for further details.

**New Network Map Architecture**

  This release introduces the new network map architecture. The network map service has been redesigned to enable future increased network scalability and redundancy, reduced runtime operational overhead,
  support for multiple notaries, and administration of network compatibility zones (CZ) and business networks.

  A Corda Compatibility Zone (CZ) is defined as a grouping of participants and services (notaries, oracles,
  doorman, network map server) configured within an operational Corda network to be interoperable and compatible with
  each other.
  See :doc:`Network Map <network-map>` and :ref:`bootstrapping the network <bootstrapping_ref>` for further details.

**Corda Firewall**

  |release| introduces the Bridge and Corda Firewall components to enable secure setup of a Corda Node in a DMZ environment.
  See :doc:`Corda Firewall Overview <corda-firewall>` for further details.

**Improved Operational Metrics**

  |release| provides additional metrics compared to Corda. A richer collection of information is exported through JMX via :ref:`Jolokia for monitoring <jolokia_ref>`.

**Operational Compatibility With Open Source Corda**

  |release| provides a baseline for wire stability and compatibility with open-source releases of Corda from version 3.0 onwards.

  It delivers forward compatibility with future versions of Corda Enterprise:

  - Is operationally compatible with future versions of Corda Enterprise.
  - Is upgradeable to future version of Corda Enterprise, preserving transaction and other data.

  It delivers operational compatibility with open-source Corda:

  - Can be used in networks seamlessly transacting with nodes running Corda 3.x and future versions.
  - Can run CorDapps developed on Corda 3.x and future versions. Note that some database changes may be required to achieve this. See :doc:`node-operations-upgrading` for more information.
  - Is compatible with ledger data created using Corda 3.x and future versions.

  Furthermore, the RPC client-server communications transport protocol is now fully AMQP based.

.. note:: RPC clients communicating with Corda Enterprise nodes must be linked against the enterprise RPC client binaries, because open-source Corda `3.x` does not yet use the AMQP serialisation protocol for RPC communication.
   From Corda open-source version `4.x` onwards, the RPC client binaries will be compatible with the enterprise distribution.

Further improvements, additions and changes
*******************************************

* Built-in flows have been improved to automatically utilise the new 'hospital' and retry functionality. For example, the :ref:`FinalityFlow <finality_flow_contract_constraints_ref>` now enables a node operator to easily address contract constraints validation errors when using mixed CorDapp versions. Furthermore, flows will attempt to automatically replay from their last saved checkpoint when, for example, a race condition has occurred for writing into the database or a database deadlock has been encountered. Flows will also retry notarisation attempts if a Highly Available Notary cluster member does not respond within the acceptable time period. There are configuration options which affect the behaviour of the retry functionality. See :doc:`corda-configuration-file` for further details.

* |release| nodes will now fail to start if unknown property keys are found in configuration files. Any unsupported property can be moved to the newly introduced "custom" section. See :doc:`corda-configuration-file` for further details.

* Property keys with double quotes (e.g. `"key"`) in ``node.conf`` are no longer allowed. See :doc:`corda-configuration-file` for further details.

* Corda's web server now has its own ``web-server.conf`` file, separate from the ``node.conf`` used by the Corda node. See :doc:`corda-configuration-file` for further details. `Note that this module is deprecated and we intend to remove it in the future.`

* |release| includes a new 'Blob Inspector' tool for viewing the contents of custom binary serialised files (such as ``node-info``, ``network-parameters``) in a human-readable format.
  See :doc:`blob-inspector` for further details.

* |release| introduces additional network parameters (event horizon) and component run-time validation (maximum allowed message size).
  The event horizon is the span of time that is allowed to elapse before an offline node is considered to be permanently gone.

* |release| adds certificate revocation list checking when running a node in a fully operational Corda Network environment backed by Network Services.
  See :doc:`certificate-revocation` for further details.

* |release| nodes support separate configuration parameters for specifying the location of the Doorman and the NetworkMap services independently of each other.

* RPC Server now masks internal errors in responses returned to RPC clients for enhanced privacy.

* Miscellaneous changes to the operation of the network bootstrapper tool, and node configuration changes.


Known issues
************

The following list contains important known issues identified in this release. We will endeavour to fix these in future
releases of Corda.

* Certificate revocation revokes identities, not keys, and is currently irreversible. If your keys are lost or compromised,
  new keys cannot be re-issued with the same X.500/legal entity name. It is strongly advised to backup your certificates
  appropriately and to apply sensible policy for management of private keys.

* The finance CorDapp from Corda 3.0 and 3.1 cannot be used with |release|.
  In a mixed-distribution network the finance CorDapp from |release| should be deployed on both Corda 3.0/3.1 and |release| nodes.
  This will be fixed in the next release of open source Corda.

* Explorer GUI does not display correctly HA notaries or multiple notaries.

* Certain characters in X500 party names prevent flows from being started from the shell [CORDA-1635].

* Missing configuration files for a network bootstrapper cause an exception [CORDA-1643].
  Network bootstrapper should gracefully exit with a helpful error message.

* Java Lambda expressions with named parameters can cause flows to become not startable from shell [CORDA-1658].

* Some ``java.time.*`` types cannot be used in mapped (JPA) schemas [CORDA-1392].

* |release| does not support class evolution using non-nullable properties [CORDA-1702].

Upgrade notes
*************

As per previous major releases, we have provided a comprehensive upgrade notes (:doc:`upgrade-notes`) to ease the upgrade
of CorDapps to |release|. In line with our commitment to API stability, code level changes are fairly minimal.

From a build perspective, switching CorDapps built using Corda 3.x to |release| is mostly effortless,
and simply requires making the Corda Enterprise binaries available to Gradle, and changing two variables in the build file:


.. parsed-literal:: shell

    ext.corda_release_version = '|version|'
    ext.corda_release_distribution = 'com.r3.corda'

..

Visit the `https://www.r3.com/corda-enterprise <https://www.r3.com/corda-enterprise/>`_ for more information about Corda Enterprise. Customers that have purchased support can access it online at  `https://support.r3.com <https://support.r3.com/>`_.
