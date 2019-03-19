Release notes
=============

|release|
---------

This release of Corda Enterprise extends the `Corda (open source) 4 release <https://docs.corda.net/releases/release-V4.0/release-notes.html>`_
with new mission critical enterprise capabilities to include support for HSM (hardware security module) signing devices and improved high
availability deployment configurations for Enterprise customers using DMZ environments.

|release| supports Linux for production deployments, with Windows and macOS support for development and demonstration purposes only. Please refer to product documentation for details.

|release| is operationally compatible with Corda (open source) 4.x and 3.x, and Corda Enterprise 3.x, while providing enterprise-grade features and performance.

.. note:: The compatibility and interoperability assurances apply to nodes running at the latest patch level for any given integer version.
   For example, at the time of writing, the Corda Enterprise 4.0 interoperability and compatibility assurance is with respect to Corda 3.3, Corda Enterprise 3.2 and Corda 4.0.

Key new features and components
*******************************

* **Multiple nodes behind a single firewall**

  Facility to share an HA deployment of a Corda firewall (and shared Artemis message broker) by multiple nodes, reducing cost and complexity
  while providing increased security for end users. This also brings the added benefit of local only keys for Artemis access and as a result
  the corda TLS key and legal signing keys are fully separated.

  See :doc:`Corda Firewall <corda-firewall>` for further details.

  We have included a detailed :doc:`Corda Firewall upgrade guide <corda-firewall-upgrade>` to simplify the migration from Corda Enterprise 3.x to use these new features.

* **Hardware Security Module (HSM) support**

  Support for node CA and legal identity signing keys in hardware security module providing clients with increased security. HSMs are standard in many
  enterprise organizations, store and safeguard cryptographic keys in tamper-proof hardware.
  Vendors supported include Azure Key Vault, Gemalto Luna and Utimaco.

  See :doc:`HSM support <cryptoservice-configuration>` for more information.

* **High Availability improvements**

  This release builds on the Hot-Cold High Availability configuration available in Corda Enterprise 3.x as follows:

  - Standalone Artemis MQ broker (previously was always embedded within the node process) for peer to peer messaging.
    This ultimately reduces VM footprint of node processes and facilitates configurability of a single HA messaging broker component.
  - New configuration mode that enables using Artemis broadcasts as an alternative to Zookeeper to manage HA nodes and associated Firewall
    components (float and bridge).

  See :doc:`Hot-cold high availability deployment <hot-cold-deployment>` for further details.

* **Operational Deployment improvements**

  Enterprise 4 introduces architectural improvements that optimize larger scale deployments, reduce the cost of infrastructure, and minimize
  the operational complexity of multi-node hosting:

  -	Management of multiple clusters of HA nodes with a single interface. Enterprise 4 achieves this with Zookeeper.
  -	Shareable Artemis messaging servers across nodes allows for less infrastructure components in a multi-node configuration.
    Additionally, we have added support for the RedHat distribution of Artemis called `Red Hat AMQ <https://access.redhat.com/documentation/en-us/red_hat_amq/7.2/>`_.

* **Performance Test Suite for benchmarking**

  The Performance Test suite enables customers to run their own benchmarks for comparative testing across different configurations and identifying hardware and resource sizing requirements.
  Using this test framework, customers can test and validate their infrastructure performance and determine whether or not improvements are needed
  before going live.

  See :doc:`Performance Test Suite <performance-testing/installation>` and :doc:`performance-testing/toc-tree` for further details.

* **Node health survey tool**

  This is a simple tool that collects and packages up material that R3 Support will need to be able to help a customer with a support request, including things like:

    * a censored version of the config (i.e., without passwords, etc.),
    * logs from the last 3 days (if the user is happy to include these),
    * version numbers of Corda, the Java virtual machine and the operating system,
    * networking information with DNS lookups to various endpoints (database, network map, doorman, external hosts),
    * a copy of the network parameters file,
    * a list of installed CorDapps (including their file sizes and checksums),
    * a list of the files in the drivers directory,
    * a copy of the node information file for the node and a list of the ones in the additional-node-infos directory.

  In future versions of Corda, we will expand on this tool's capabilities for it to be a complete deployment verification tool, also usable with more complex, high availability deployments.

* **RPC client compatibility**

  With Corda (open source) 4 upgrading to use the AMQP serialisation protocol for RPC communication, it is now possible to communicate
  remotely to a Corda 4 or Corda Enterprise 4 node using either of the respective 4.x distributions' RPC client binary library.

.. note:: RPC clients communicating with Corda (open source) `3.x` nodes must continue to use a respective 3.x Kryo-based RPC client binary library.

* **Operational Compatibility With Open Source Corda**

  |release| maintains the wire stability and compatibility assurance with open-source releases of Corda from version 3.0 onwards.

  It delivers forward compatibility with future versions of Corda Enterprise:

  - Is operationally compatible with future versions of Corda Enterprise.
  - Is upgradeable to future version of Corda Enterprise, preserving transaction and other data.

  It delivers operational compatibility with open-source Corda:

  - Can be used in networks seamlessly transacting with nodes running Corda 3.x and future versions.
  - Can run CorDapps developed on Corda 3.x and future versions. Note that some database changes may be required to achieve this. See :doc:`node-upgrade-notes` for more information.
  - Is compatible with ledger data created using Corda 3.x and future versions.

Further improvements, additions and changes
*******************************************

* Corda Firewall (bridge and float) components now have dedicated private keystores and passwords.
  A special tool has been created to simplify generation of the keystores.
  See :ref:`Firewall keystore generation <firewall_keystore_generation_ref>` for further details.

* A number of HA utilities to improve the setup and configuration of enterprise deployments.
  See :doc:`HA Utilities <ha-utilities>` for further details.

* Passwords may now be obfuscated in configuration files.
  See :doc:`Configuration Obfuscator <tools-config-obfuscator>` for further details.

* Oracle Wallet support: you can now connect to an Oracle database using credentials stored in an Oracle Wallet.
  See :ref:`Connecting to Oracle using Oracle Wallet <oracle_wallet_ref>` for further details.

* Improve diagnostics for operational support and troubleshooting.
  Stacktraces are still printed to the log files, but errors printed to the console include an automatically derived error code instead.
  These error codes make it easier to discover diagnostics and troubleshooting steps for various error conditions, and can also be used to
  find corresponding information in log files or to correlate errors across multiple nodes in a broader deployment. The introduction of error
  codes also allows us to maintain and continuously improve our error information and recovery guidelines on a different cadence to that of our releases.

* Improved Notary retry support
  Client-side notarisation flows have special retry logic that enables failover in case a member of the notary cluster goes down while processing
  a request. This release includes a fix that disables retries when talking to a single-node notary, as it does not provide any benefit.
  Additionally, the maximum retry limit has been dropped, and notarisations will be retried indefinitely. This is to prevent potential transaction
  loss in case of network issues or misconfiguration where the notary is unable to send back a response to the client.

* Notary support
  The experimental Raft notary implementation has been deprecated in favour of the MySQL-based HA notary implementation (see :doc:`running-a-notary-cluster/toctree`).
  The experimental BFT-Smart notary implementation has been deprecated – a fully supported BFT implementation is under development.

* Flow hospital enhancements
  New in |release|, if a node receives a message from a peer to initiate a flow that is not recognised by the node, perhaps due to
  the CorDapp not being installed, rather than sending an error back to the initiating peer node it is kept in the hospital until the CorDapp
  is installed and the node restarted. Furthermore, in addition to the existing hospitalisation for flow errors in the FinalityHandler of
  Corda Enterprise 3, flow errors in the new inlined ``ReceiveFinalityFlow`` replacement will also be hospitalised similarly.

* Improved Database Management and Migration Tooling
  |release| improves the database administration tool for tracking, managing and applying database schema and data changes (for both Corda infrastructure
  and CorDapps). See :ref:`database migration <database_migration_ref>` for further details.

* Support for class evolution using non-nullable properties if you supply an evolution constructor which fills in the missing property values.

Known issues
************

The following list contains important known issues identified in this release. We will endeavour to fix these in future releases of Corda.

* Prior to |release| all CorDapps were classloaded in the same applications classloader, with no isolation or visibility constraints.
  With the introduction of the *Attachments Classloader* in for transaction verification, a CorDapp JAR is now only classloaded if there is
  at least one class that implements the ``Contract`` interface. Where a Contract CorDapp previously depended on classes packaged in a separate
  JAR (eg. a 3rd party library, common classes or other CorDapp contracts), these must now be included in the same Contract CorDapp JAR.
  Please read :ref:`Contract CorDapps JARs with external dependencies <cordapps_external_dependencies>` for a more detailed explanation and
  a reference example.

  .. note:: CorDapps built using the new `Token SDK <https://github.com/corda/token-sdk>`_ fall into this category and are required
     to include Token SDK CorDapps in their own Contract CorDapps JAR.

* The experimental finance CorDapp compiled against Corda 3.3 or Enterprise Corda 3.2 and run on |release| is not guaranteed to interoperate with
  its upgraded equivalent Corda 4 version compiled against Corda 4.0 or |release| and running on |release|.

  See :doc:`CorDapp Upgradeability Guarantees <cordapp-upgradeability>` for further information.

* Certificate revocation revokes identities, not keys, and is currently irreversible. If your keys are lost or compromised,
  new keys cannot be re-issued with the same X.500/legal entity name. It is strongly advised to backup your certificates
  appropriately and to apply sensible policy for management of private keys.

Upgrade notes
*************

As per previous major releases, we have provided a comprehensive upgrade notes (:doc:`app-upgrade-notes-enterprise`) to ease the upgrade
of CorDapps to |release|. In line with our commitment to API stability, code level changes are fairly minimal.

For **developers**, switching CorDapps built using Corda (open source) 4.x to |release| is mostly effortless,
and simply requires making the Corda Enterprise binaries available to Gradle, and changing two variables in the build file:

.. sourcecode:: shell

    ext.corda_release_version = '4.0'
    ext.corda_release_distribution = 'com.r3.corda'

For **node operators**, it is advisable to follow the instructions outlined in :doc:`Upgrading a Corda Node <node-upgrade-notes>`.

.. note:: In a mixed-distribution network the open source finance contract CorDapp should be deployed on both Corda 4.0 (open source) and |release| nodes.

Visit the `https://www.r3.com/corda-enterprise <https://www.r3.com/corda-enterprise/>`_ for more information about Corda Enterprise.
Customers that have purchased support can access it online at  `https://support.r3.com <https://support.r3.com/>`_.
