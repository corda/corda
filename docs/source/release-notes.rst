Release notes
=============

R3 Corda 3.0 Developer Preview
------------------------------
This Developer Preview takes us towards the launch of R3 Corda, R3's commercially supported enterprise blockchain platform.

Whilst the recent major releases of **Corda** ("Open Source") - V1.0 and V2.0 - have focused on providing API stability and
functionality, **R3 Corda** has been primarily focused on non-functional aspects of the platform: performance, scalability,
robustness, security, configurability, manageability, supportability and upgradeability.

Here is a summary of some of the key new features in this preview, many of which will also appear in the next open
source release:

- support for :ref:`Azure SQL and SQL Server 2017 <sql_server_ref>` databases.
- integrated :ref:`database schema management <database_migration_ref>` tooling using `Liquibase <http://www.liquibase.org/>`_
- completely re-designed :doc:`network-map` Service.
- enabled :ref:`AMQP serialization <amqp_ref>` for peer to peer messaging, and vault transaction storage.
- pluggable :ref:`user authentication <authentication_ref>` and :ref:`fine-grained access control <rpc_security_mgmt_ref>`.
- re-designed Flow Framework manager in preparation for fully multi-threaded implementation.
- improvements to the Doorman certificate issuance process (including integration with HSMs).
- additional JMX metrics exported via :ref:`Jolokia for monitoring <jolokia_ref>` and pro-active alert management.
- a secure, remotely accessible, :ref:`SSH server <ssh_server>` in the node with built-in authorization and permissioning to enable remote
  node administration without requiring console access to the underlying operating system.
- re-architected and re-designed Corda bridge management for secure P2P connectivity between participants.
- enhanced Explorer tool with new :ref:`flow triage <flow_triage>` user interface panel to visualize all currently running flows.
- preliminary implementation of Business Networks concept (a private sub group within a Corda Compatibility Zone).

We also continue to make improvements in usability of our Test Framework APIs in preparation for declaring the test
framework API stable (in addition to the already-stabilised core APIs).

Significant changes implemented in reaching this Developer Preview include:

* **AMQP**:
  AMQP Serialization is now enabled for both peer to peer communication and the writing of states to the vault. This
  change brings a serialisation format that will allow us to deliver enhanced security and wire stability. It is a key
  prerequisite to enabling different Corda node versions to coexist on the same network and to enable easier upgrades.

  Details on the AMQP serialization framework can be found :ref:`here <amqp_ref>`. This provides an introduction and
  overview of the framework whilst more specific details on object evolution as it relates to serialization is similarly
  found in :doc:`serialization-default-evolution` and :doc:`serialization-enum-evolution` respectively.

  .. note:: This release delivers the bulk of our transition from Kryo serialisation to AMQP serialisation. This means that many of the restrictions
    that were documented in previous versions of Corda are now enforced.

    In particular, you are advised to review the section titled :ref:`Custom Types <amqp_custom_types_ref>`.
    To aid with the transition, we have included support in this release for default construction and instantiation of
    objects with inaccessible private fields, but it is not guaranteed that this support will continue into future versions;
    the restrictions documented at the link above are the canonical source.

* **New Network Map Service**:
  This release introduces the new network map architecture. The network map service has been completely redesigned and
  implemented to enable future increased network scalability and redundancy, reduced runtime operational overhead,
  support for multiple notaries, and administration of network compatibility zones (CZ) and business networks.

  A Corda Compatibility Zone (CZ) is defined as a grouping of participants and services (notaries, oracles,
  doorman, network map server) configured within an operational Corda network to be interoperable and compatible with
  each other.

  We introduce the concept of network parameters, which will be used in a future version of Corda to specify precisely
  the set of constants (or ranges of constants) upon which a set of nodes need to agree in order to be assured of seamless
  inter-operation. Additional security controls ensure that all network map data is now signed, thus reducing the power
  of the network operator to tamper with the map.

  This release also adds Hardware Security Module (HSM) support to the doorman service (certificate authority).
  By integrating with external HSMs, we have further strengthened the security of issuing network certificates and
  signing of network map related data.

  Further information can be found in the :doc:`changelog` and :doc:`network-map` documentation.

* **Third party database support**:
  R3 Corda has been tested against Azure SQL and SQL Server 2017 databases (in addition to the existing default support
  of H2 for development mode). This preview adds preliminary support for :ref:`PostgreSQL 9.6 <postgres_ref>`.
  Support for Oracle 11g RC02 and Oracle 12c is currently under development. All required database settings can be
  specified in the node configuration file. For configuration details see :doc:`node-database`.

* **Integrated database migration tooling**:
  We have adopted and integrated `Liquibase <http://www.liquibase.org/>`_ , an open source database-independent library
  for tracking, managing and applying database schema changes in order to ease the evolution (creation and migration) of
  CorDapp custom contract schemas and facilitate the operational administration of a Corda nodes database.
  We provide tooling to export DDL and data (as SQL statements) to a file to be inspected and/or manually applied by a DBA.
  Please see :ref:`database migration <database_migration_ref>` for further details.

* **Pluggable user authentication and fine-grained access control**:
  All RPC functions are now subject to permission checks (previously these only applied when starting flows).
  We have also included experimental support for external user credentials data source and password encryption using the
  `Apache Shiro <https://shiro.apache.org>`_ framework. Please see :ref:`RPC security management <rpc_security_mgmt_ref>` for further details.

* **Preliminary preview of new bridge management functionality**:
  The bridge manager component is responsible for dynamically establishing remote connectivity with participant nodes
  in a Corda peer to peer network. A new Bridge manager has been designed and implemented to be used integrally
  within a :ref:`Corda node <config_amqp_bridge>` or deployed (in the final R3 Corda 3.0 release) as a standalone component in DMZ operational deployments,
  where security concerns require separation of infrastructure messaging subsystems.

* **Preliminary preview of flow triage functionality**:
  The explorer GUI was extended with a panel similar to the ``flow watch`` CRaSH shell command. It provides users with a view of all
  flows currently executed on the node, with information about success/failure. The "Flow Triage" panel will be enhanced in the future
  to enable operators to take corrective actions upon flow failures (eg. retry, terminate, amend and replay).

* **Experimental preview of a new operational Corda network grouping concept: Business Networks**:
  Business Networks are introduced as a way to partition the global population of nodes (a Compatibility Zone) into
  independent, possibly overlapping, groups. A Business Network operator (BNO) will have control over which nodes will
  be admitted into a Business Network. Some nodes may choose not to register themselves in the global Network Map, and
  will therefore remain invisible to nodes outside of their Business Network. Further documentation will be forthcoming
  by the final R3 Corda 3.0 release.

  See the "Business Network reference implementation" prototype example in the Explorer tool (instructions in README.md).

In addition to enhancements focused on non-functional capabilities, this release encompasses a number of functional
improvements, including:

* Doorman Service
  In order to automate a node's network joining process, a new Doorman service has been introduced with this release.
  The Doorman's main purpose is to restrict network access only to those nodes whose identity has been confirmed and their network joining request approved.
  It issues node-level certificates which are then used by other nodes in the network to confirm a nodes identity and network permissions.
  More information on Doorman and how to run it can be found in :doc:`running-doorman`.

* Hardware Security Module (HSM) for Doorman
  To allow for increased security, R3 Corda introduces HSM integration. Doorman certificates (together with their keys)
  can now be stored on secured hardware constraining the way those certificates are accessed. Any usage of those certificates
  (e.g. data signing or node-level certificate generation) falls into a restrictive process that is automatically audited
  and can be configured to involve human-in-the-loop in order to prevent unauthorised access. The HSM integration is embodied
  in our new Signing Service. More on this in :doc:`signing-service`.

* X.509 certificates now have an extension that specifies the Corda role the certificate is used for, and the role
  hierarchy is now enforced in the validation code. This only has impact on those developing integrations with external
  PKI solutions. In most cases it is managed transparently by Corda. A formal specification of the extension can be
  found at see :doc:`permissioning-certificate-specification`.

* Custom Serializers
  To allow interop with third party libraries that cannot be recompiled we add functionality that allows custom serializers
  to be written for those classes. If needed, a proxy object can be created as an interim step that allows Corda's internal
  serializers to operate on those types. A good example of this is the SIMM valuation demo which has a number of such
  serializers defined in the plugin/custom serializers package

Please refer to the :doc:`changelog` for detailed explanations of all new features.

Finally, please note that although this developer preview has not yet been security audited, it is currently being subjected
to a full external secure code review and penetration test.

As per previous major releases, we have provided a comprehensive upgrade notes (:doc:`upgrade-notes`) to ease the upgrade
of CorDapps to R3 Corda 3.0 Developer Preview. In line with our commitment to API stability, code level changes
are fairly minimal, and mostly related to improvements to our nearly API stable test framework.

From a build perspective, switching CorDapps built using Corda (the "Open Source" code) to R3 Corda is mostly effortless,
and simply requires setting two gradle build file variables:

.. sourcecode:: shell

  ext.corda_release_version = 'R3.CORDA-3.0.0-DEV-PREVIEW'
  ext.corda_release_distribution = 'com.r3.corda'

Please note this release is distributed under license and should not be used in a Production environment yet.

We look forward to hearing your feedback on this Developer Preview.

<<<<<<< HEAD
Corda 2.0
---------
=======
  Documentation can be found in :doc:`cordapp-custom-serializers`


Security Auditing
~~~~~~~~~~~~~~~~~

  This version of Corda is the first to have had select components subjected to the newly established security review process
  by R3's internal security team. Security review will be an on-going process that seeks to provide assurance that the
  security model of Corda has been implemented to the highest standard, and is in line with industry best practice.

  As part of this security review process, an independent external security audit of the HTTP based components of the code
  was undertaken and its recommendations were acted upon. The security assurance process will develop in parallel to the
  Corda platform and will combine code review, automated security testing and secure development practices to ensure Corda
  fulfils its security guarantees.

Security fixes
~~~~~~~~~~~~~~

  * Due to a potential privacy leak, there has been a breaking change in the error object returned by the
    notary service when trying to consume the same state twice: `NotaryError.Conflict` no longer contains the identity
    of the party that initiated the first spend of the state, and specifies the hash of the consuming transaction id for
    a state instead of the id itself.

    Without this change, knowing the reference of a particular state, an attacker could construct an invalid
    double-spend transaction, and obtain the information on the transaction and the party that consumed it. It could
    repeat this process with the newly obtained transaction id by guessing its output indexes to obtain the forward
    transaction graph with associated identities. When anonymous identities are used, this could also reveal the identity
    of the owner of an asset.

Minor Changes
~~~~~~~~~~~~~

  * Upgraded gradle to 4.4.1.

    .. note:: To avoid potential incompatibility issues we recommend you also upgrade your CorDapp's gradle
      plugin to match. Details on how to do this can be found on the official
      `gradle website <https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:upgrading_wrapper>`_

  * Cash Spending now allows for sending multiple amounts to multiple parties with a single API call

    - documentation can be found within the JavaDocs on ``TwoPartyTradeFlow``.
  * Overall improvements to error handling (RPC, Flows, Network Client).
  * TLS authentication now supports mixed RSA and ECDSA keys.
  * PrivacySalt computation is faster as it does not depend on the OS's entropy pool directly.
  * Numerous bug fixes and documentation tweaks.
  * Removed dependency on Jolokia WAR file.

.. _release_notes_v2_0:

Release 2.0
-----------
>>>>>>> open/master
Following quickly on the heels of the release of Corda 1.0, Corda version 2.0 consolidates
a number of security updates for our dependent libraries alongside the reintroduction of the Observer node functionality.
This was absent from version 1 but based on user feedback its re-introduction removes the need for complicated "isRelevant()" checks.

In addition the fix for a small bug present in the coin selection code of V1.0 is integrated from master.

* **Version Bump**

Due to the introduction of new APIs, Corda 2.0 has a platform version of 2. This will be advertised in the network map structures
and via the versioning APIs.

* **Observer Nodes**

Adds the facility for transparent forwarding of transactions to some third party observer, such as a regulator. By having
that entity simply run an Observer node they can simply receive a stream of digitally signed, de-duplicated reports that
can be used for reporting.

<<<<<<< HEAD
Corda 1.0
---------
=======
.. _release_notes_v1_0:

Release 1.0
-----------
>>>>>>> open/master
Corda 1.0 is finally here!

This critical step in the Corda journey enables the developer community, clients, and partners to build on Corda with confidence.
Corda 1.0 is the first released version to provide API stability for Corda application (CorDapp) developers.
Corda applications will continue to work against this API with each subsequent release of Corda. The public API for Corda
will only evolve to include new features.

As of Corda 1.0, the following modules export public APIs for which we guarantee to maintain backwards compatibility,
unless an incompatible change is required for security reasons:

 * **core**: 
   Contains the bulk of the APIs to be used for building CorDapps: contracts, transactions, flows, identity, node services, 
   cryptographic libraries, and general utility functions.

 * **client-rpc**: 
   An RPC client interface to Corda, for use by both UI facing clients and integration with external systems.

 * **client-jackson**: 
   Utilities and serialisers for working with JSON representations of basic types.

Our extensive testing frameworks will continue to evolve alongside future Corda APIs. As part of our commitment to ease of use and modularity
we have introduced a new test node driver module to encapsulate all test functionality in support of building standalone node integration
tests using our DSL driver.

Please read :doc:`corda-api` for complete details.

.. note:: it may be necessary to recompile applications against future versions of the API until we begin offering
         `ABI (Application Binary Interface) <https://en.wikipedia.org/wiki/Application_binary_interface>`_ stability as well.
         We plan to do this soon after this release of Corda.

Significant changes implemented in reaching Corda API stability include:

* **Flow framework**:
  The Flow framework communications API has been redesigned around session based communication with the introduction of a new 
  ``FlowSession`` to encapsulate the counterparty information associated with a flow. 
  All shipped Corda flows have been upgraded to use the new `FlowSession`. Please read :doc:`api-flows` for complete details.

* **Complete API cleanup**:
  Across the board, all our public interfaces have been thoroughly revised and updated to ensure a productive and intuitive developer experience.
  Methods and flow naming conventions have been aligned with their semantic use to ease the understanding of CorDapps.
  In addition, we provide ever more powerful re-usable flows (such as `CollectSignaturesFlow`) to minimize the boiler-plate code developers need to write.

* **Simplified annotation driven scanning**:
  CorDapp configuration has been made simpler through the removal of explicit configuration items in favour of annotations
  and classpath scanning. As an example, we have now completely removed the `CordaPluginRegistry` configuration.
  Contract definitions are no longer required to explicitly define a legal contract reference hash. In their place an
  optional `LegalProseReference` annotation to specify a URI is used.

* **Java usability**:
  All code has been updated to enable simple access to static API parameters. Developers no longer need to 
  call getter methods, and can reference static API variables directly.

In addition to API stability this release encompasses a number of major functional improvements, including:

* **Contract constraints**:
  Provides a means with which to enforce a specific implementation of a State's verify method during transaction verification.
  When loading an attachment via the attachment classloader, constraints of a transaction state are checked against the 
  list of attachment hashes provided, and the attachment is rejected if the constraints are not matched.

* **Signature Metadata support**:
  Signers now have the ability to add metadata to their digital signatures. Whereas previously a user could only sign the Merkle root of a
  transaction, it is now possible for extra information to be attached to a signature, such as a platform version
  and the signature-scheme used.

  .. image:: resources/signatureMetadata.png

* **Backwards compatibility and improvements to core transaction data structures**:
  A new Merkle tree model has been introduced that utilises sub-Merkle trees per component type. Components of the
  same type, such as inputs or commands, are grouped together and form their own Merkle tree. Then, the roots of
  each group are used as leaves in the top-level Merkle tree. This model enables backwards compatibility, in the
  sense that if new component types are added in the future, old clients will still be able to compute the Merkle root
  and relay transactions even if they cannot read (deserialise) the new component types. Due to the above,
  `FilterTransaction` has been made simpler with a structure closer to `WireTransaction`. This has the effect of making the API
  more user friendly and intuitive for both filtered and unfiltered transactions.

* **Enhanced component privacy**:
  Corda 1.0 is equipped with a scalable component visibility design based on the above sophisticated
  sub-tree model and the introduction of nonces per component. Roughly, an initial base-nonce, the "privacy-salt",
  is used to deterministically generate nonces based on the path of each component in the tree. Because each component
  is accompanied by a nonce, we protect against brute force attacks, even against low-entropy components. In addition,
  a new privacy feature is provided that allows non-validating notaries to ensure they see all inputs and if there was a
  `TimeWindow` in the original transaction. Due to the above, a malicious user cannot selectively hide one or more
  input states from the notary that would enable her to bypass the double-spending check. The aforementioned
  functionality could also be applied to Oracles so as to ensure all of the commands are visible to them.

  .. image:: resources/subTreesPrivacy.png

* **Full support for confidential identities**:
  This includes rework and improvements to the identity service to handle both `well known` and `confidential` identities.
  This work ships in an experimental module in Corda 1.0, called `confidential-identities`. API stabilisation of confidential
  identities will occur as we make the integration of this privacy feature into applications even easier for developers.

* **Re-designed network map service**:
  The foundations for a completely redesigned network map service have been implemented to enable future increased network 
  scalability and redundancy, support for multiple notaries, and administration of network compatibility zones and business networks.

Finally, please note that the 1.0 release has not yet been security audited.

We have provided a comprehensive :doc:`upgrade-notes` to ease the transition of migrating CorDapps to Corda 1.0

Upgrading to this release is strongly recommended, and you will be safe in the knowledge that core APIs will no longer break.

Thank you to all contributors for this release!
