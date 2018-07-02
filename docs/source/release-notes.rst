Release notes
=============

.. _release_notes_v3_2:

Release 3.2
-----------

As Corda deployments move toward production this minor release of the open sauce platform brings
several fixes that address issues with a node's interactions with the defined compatibility zone.
This will ensure Corda nodes will be free to interact with the upcoming network platforms from
R3 and any other privately deployed compatibility zone.

To support the ongoing move toward usability, testing, and deployment, 3.2 is releasing the
Network Bootstrapper <docs ref> to facilitate the simple creation of more dynamic ad-hoc testing
environments.

Finally, the Blob Inspector <docs ref> brings the ability to inspect serializes Corda Blobs at the
command line, meaning those serialized Network Parameters and Node Info files are suddenly a lot
easier to understand and debug.

Issues Fixed
~~~~~~~~~~~~

<fill me in>

.. _release_notes_v3_1:

Release 3.1
-----------

This rapid follow-up to Corda 3.0 corrects an issue discovered by some users of Spring Boot and a number of other
smaller issues discovered post release. All users are recommended to upgrade.

Special Thanks
~~~~~~~~~~~~~~

Without passionate and engaged users Corda would be all the poorer. As such, we are extremely grateful to
`Bret Lichtenwald <https://github.com/bret540>`_ for helping nail down a reproducible test case for the
Spring Boot issue.

Major Bug Fixes
~~~~~~~~~~~~~~~

* **Corda Serialization fails with "Unknown constant pool tag"**

  This issue is most often seen when running a CorDapp with a Rest API using / provided by ``Spring Boot``.

  The fundamental cause was ``Corda 3.0`` shipping with an out of date dependency for the
  `fast-classpath-scanner <https://github.com/lukehutch/fast-classpath-scanner>`_ library, where the manifesting
  bug was already fixed in a released version newer than our dependant one. In response, we've updated our dependent
  version to one including that bug fix.

* **Corda Versioning**

  Those eagle eyed amongst you will have noticed for the 3.0 release we altered the versioning scheme from that used by previous Corda
  releases (1.0.0, 2.0.0, etc) with the addition of an prepended product name, resulting in ``corda-3.0``. The reason for this was so
  that developers could clearly distinguish between the base open source platform and any distributions based on on Corda that may
  be shipped in the future (including from R3), However, we have heard the complaints and feel the pain that's caused by various
  tools not coping well with this change. As such, from now on the versioning scheme will be inverted, with this release being ``3.1-corda``.

  As to those curious as to why we dropped the patch number from the version string, the reason is very simple: there won't
  be any patches applied to a release of Corda. Either a release will be a collection of bug fixes and non API breaking
  changes, thus eliciting a minor version bump as with this release, or major functional changes or API additions and warrant
  a major version bump. Thus, rather than leave a dangling ``.0`` patch version on every release we've just dropped it. In the
  case where a major security flaw needed addressing, for example, then that would generate a release of a new minor version.

Issues Fixed
~~~~~~~~~~~~

* RPC server leaks if a single client submits a lot of requests over time [`CORDA-1295 <https://r3-cev.atlassian.net/browse/CORDA-1295>`_]
* Flaky startup, no db transaction in context, when using postgresql [`CORDA-1276 <https://r3-cev.atlassian.net/browse/CORDA-1276>`_]
* Corda's JPA classes should not be final or have final methods [`CORDA-1267 <https://r3-cev.atlassian.net/browse/CORDA-1267>`_]
* Backport api-scanner changes [`CORDA-1178 <https://r3-cev.atlassian.net/browse/CORDA-1178>`_]
* Misleading error message shown when node is restarted after the flag day
* Hash constraints not working from Corda 3.0 onwards
* Serialisation Error between Corda 3 RC01 and Corda 3
* Nodes don't start when network-map/doorman is down

.. _release_notes_v3_0:

Release 3.0
-----------

Corda 3.0 is here and brings with it a commitment to a wire stable platform, a path for contract and node upgradability,
and a host of other exciting features. The aim of which is to enhance the developer and user experience whilst providing
for the long term usability of deployed Corda instances. This release will provide functionality to ensure anyone wishing
to move to the anticipated release of R3 Corda can do so seamlessly and with the assurance that stateful data persisted to
the vault will remain understandable between newer and older nodes.

Special Thanks
~~~~~~~~~~~~~~

As ever, we are grateful to the enthusiastic user and developer community that has  grown up to surround Corda.
As an open project we are always grateful to take code contributions from individual users where they feel they
can add functionality useful to themselves and the wider community.

As such we'd like to extend special thanks to

  * Ben Wyeth for providing a mechanism for registering a callback on app shutdown

    Ben's contribution can be found on GitHub
    `here <https://github.com/corda/corda/commit/d17670c747d16b7f6e06e19bbbd25eb06e45cb93>`_

  * Tomas Tauber for adding support for running Corda atop PostgresSQL in place of the in-memory H2 service

    Tomas's contribution can be found on GitHub
    `here <https://github.com/corda/corda/commit/342090db62ae40cef2be30b2ec4aa451b099d0b7>`_

    .. warning:: This is an experimental feature that has not been tested as part of our standard release testing.

  * Rose Molina Atienza for correcting our careless spelling slip

    Rose's change can be found on GitHub
    `here <https://github.com/corda/corda/commit/128d5cad0af7fc5595cac3287650663c9c9ac0a3>`_

Significant Changes in 3.0
~~~~~~~~~~~~~~~~~~~~~~~~~~

* **Wire Stability**:

  Wire stability brings the same promise to developers for their data that API stability did for their code. From this
  point any state generated by a Corda system will always be retrievable, understandable, and seen as valid by any
  subsequently released version (versions 3.0 and above).

  Systems can thus be deployed safe in the knowledge that valuable and important information will always be accessible through
  upgrade and change. Practically speaking this means from this point forward upgrading all, or part, of a Corda network
  will not require the replaying of data; "it will just work".

  This has been facilitated by the switch over from Kryo to Corda's own AMQP based serialization framework, a framework
  designed to interoperate with stateful information and allow the evolution of such contract states over time as developers
  refine and improve their systems written atop the core Corda platform.

  * **AMQP Serialization**

    AMQP Serialization is now enabled for both peer to peer communication and the writing of states to the vault. This
    change brings a serialisation format that will allow us to deliver enhanced security and wire stability. This was a key
    prerequisite to enabling different Corda node versions to coexist on the same network and to enable easier upgrades.

    Details on the AMQP serialization framework can be found :ref:`here <amqp_ref>`. This provides an introduction and
    overview of the framework whilst more specific details on object evolution as it relates to serialization can be
    found in :doc:`serialization-default-evolution` and :doc:`serialization-enum-evolution` respectively.

    .. note:: This release delivers the bulk of our transition from Kryo serialisation to AMQP serialisation. This means
      that many of the restrictions that were documented in previous versions of Corda are now enforced.

      In particular, you are advised to review the section titled :ref:`Custom Types <amqp_custom_types_ref>`.
      To aid with the transition, we have included support in this release for default construction and instantiation of
      objects with inaccessible private fields, but it is not guaranteed that this support will continue into future versions;
      the restrictions documented at the link above are the canonical source.

    Whilst this is an important step for Corda, in no way is this the end of the serialisation story. We have many new
    features and tools planned for future releases, but feel it is more important to deliver the guarantees discussed above
    as early as possible to allow the community to develop with greater confidence.

   .. important:: Whilst Corda has stabilised its wire protocol and infrastructure for peer to peer communication and persistent storage
      of states, the RPC framework will, for this release, not be covered by this guarantee. The moving of the client and
      server contexts away from Kryo to our stable AMQP implementation is planned for the next release of Corda

  * **Artemis and Bridges**

    Corda has now achieved the long stated goal of using the AMQP 1.0 open protocol standard as its communication protocol
    between peers. This forms a strong and flexible framework upon which we can deliver future enhancements that will allow
    for much smoother integrations between Corda and third party brokers, languages, and messaging systems. In addition,
    this is also an important step towards formally defining the official peer to peer messaging protocol of Corda, something
    required for more in-depth security audits of the Corda protocol.

* **New Network Map Service**:

  This release introduces the new network map architecture. The network map service has been completely redesigned and
  implemented to enable future increased network scalability and redundancy, reduced runtime operational overhead,
  support for multiple notaries, and administration of network compatibility zones (CZ).

  A Corda Compatibility Zone is defined as a grouping of participants and services (notaries, oracles,
  doorman, network map server) configured within an operational Corda network to be interoperable and compatible with
  each other.

  We introduce the concept of network parameters to specify precisely the set of constants (or ranges of constants) upon
  which the nodes within a network need to agree in order to be assured of seamless inter-operation. Additional security
  controls ensure that all network map data is now signed, thus reducing the power of the network operator to tamper with
  the map.

  There is also support for a group of nodes to operate locally, which is achieved by copying each
  node's signed info file to the other nodes' directories. We've added a bootstrapping tool to facilitate this use case.

  .. important:: This replaces the Network Map service that was present in Corda 1.0 and Corda 2.0.

  Further information can be found in the :doc:`changelog`, :doc:`network-map` and :doc:`setting-up-a-corda-network` documentation.

* **Contract Upgrade**

  Support for the upgrading of contracts has been significantly extended in this release.

  Contract states express which attached JARs can define and verify them using _constraints_. In older versions the only supported
  constraint was a hash constraint. This provides similar behaviour as public blockchain systems like Bitcoin and Ethereum, in
  which code is entirely fixed once deployed and cannot be changed later. In Corda there is an upgrade path that involves the
  cooperation of all involved parties (as advertised by the states themselves), but this requires explicit transactions to be
  applied to all states and be signed by all parties.

  .. tip:: This is a fairly heavyweight operation. As such, consideration should be given as to the most opportune time at
    which it should be performed.

  Hash constraints provide for maximum decentralisation and minimum trust, at the cost of flexibility. In Corda 3.0 we add a
  new constraint, a _network parameters_ constraint, that allows the list of acceptable contract JARs to be maintained by the
  operator of the compatibility zone rather than being hard-coded. This allows for simple upgrades at the cost of the introduction
  of an element of centralisation.

  Zone constraints provide a less restrictive but more centralised control mechanism. This can be useful when you want
  the ability to upgrade an app and you don’t mind the upgrade taking effect “just in time” when a transaction happens
  to be required for other business reasons. These allow you to specify that the network parameters of a compatibility zone
  (see :doc:`network-map`) is expected to contain a map of class name to hashes of JARs that are allowed to provide that
  class. The process for upgrading an app then involves asking the zone operator to add the hash of your new JAR to the
  parameters file, and trigger the network parameters upgrade process. This involves each node operator running a shell
  command to accept the new parameters file and then restarting the node. Node owners who do not restart their node in
  time effectively stop being a part of the network.

  .. note:: In prior versions of Corda, states included the hash of their defining application JAR (in the Hash Constraint).
    In this release, transactions have the JAR containing the contract and states attached to them, so the code will be copied
    over the network to the recipient if that peer lacks a copy of the app.

    Prior to running the verification code of a contract the JAR within which the verification code of the contract resides
    is tested for compliance to the contract constraints:
        - For the ``HashConstraint``: the hash of the deployed CorDapp jar must be the same as the hash found in the Transaction.
        - For the ``ZoneConstraint``: the Transaction must come with a whitelisted attachment for each Contract State.
    If this step fails the normal transaction verification failure path is followed.

    Corda 3.0 lays the groundwork for future releases, when contract verification will be done against the attached contract JARs
    rather than requiring a locally deployed CorDapp of the exact version specified by the transaction. The future vision for this
    feature will entail the dynamic downloading of the appropriate version of the smart contract and its execution within a
    sandboxed environment.

    .. warning:: This change means that your app JAR must now fit inside the 10mb attachment size limit. To avoid redundantly copying
      unneeded code over the network and to simplify upgrades, consider splitting your application into two or more JARs - one that
      contains states and contracts (which we call the app "kernel"), and another that contains flows, services, web apps etc. For
      example, our `Cordapp template <https://github.com/corda/cordapp-template-kotlin/tree/release-V3>`_ is structured like that.
      Only the first will be attached. Also be aware that any dependencies your app kernel has must be bundled into a fat JAR,
      as JAR dependencies are not supported in Corda 3.0.

  Future versions of Corda will add support for signature based constraints, in which any JAR signed by a given identity
  can be attached to the transaction. This final constraint type provides a balance of all requirements: smooth rolling upgrades
  can be performed without any additional steps or transactions being signed, at the cost of trusting the app developer more and
  some additional complexity around managing app signing.

  Please see the :doc:`upgrading-cordapps` for more information on upgrading contracts.

* **Test API Stability**

  A great deal of work has been carried out to refine the APIs provided to test CorDapps, making them simpler, more intuitive,
  and generally easier to use. In addition, these APIs have been added to the *locked* list of the APIs we guarantee to be stable
  over time. This should greatly increase productivity when upgrading between versions, as your testing environments will work
  without alteration.

  Please see the :doc:`upgrade-notes` for more information on transitioning older tests to the new framework.

Other Functional Improvements
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

* **Clean Node Shutdown**

  We, alongside user feedback, concluded there was a strong need for the ability to have a clean inflection point where a node
  could be shutdown without any in-flight transactions pending to allow for a clean system for upgrade purposes. As such, a flows
  draining mode has been added. When activated, this places the node into a state of quiescence that guarantees no new work will
  be started and all outstanding work completed prior to shutdown.

  A clean shutdown can thus be achieved by:

    1. Subscribing to state machine updates
    2. Trigger flows draining mode by ``rpc.setFlowsDrainingModeEnabled(true)``
    3. Wait until the subscription setup as phase 1 lets you know that no more checkpoints are around
    4. Shut the node down however you want

  .. note:: Once set, this mode is a persistent property that will be preserved across node restarts. It must be explicitly disabled
    before a node will accept new RPC flow connections.

* **X.509 certificates**

  These now have an extension that specifies the Corda role the certificate is used for, and the role
  hierarchy is now enforced in the validation code. This only has impact on those developing integrations with external
  PKI solutions; in most cases it is managed transparently by Corda. A formal specification of the extension can be
  found at see :doc:`permissioning-certificate-specification`.

* **Configurable authorization and authentication data sources**

  Corda can now be configured to load RPC user credentials and permissions from an external database and supports password
  encryption based on the `Apache Shiro framework <https://shiro.apache.org>`_. See :ref:`RPC security management
  <rpc_security_mgmt_ref>` for documentation.

* **SSH Server**

  Remote administration of Corda nodes through the CRaSH shell is now available via SSH, please see :doc:`shell` for more details.

* **RPC over SSL**

  Corda now allows for the configuration of its RPC calls to be made over SSL. See :doc:`corda-configuration-file` for details
  how to configure this.

* **Improved Notary configuration**

  The configuration of notaries has been simplified into a single ``notary`` configuration object. See
  :doc:`corda-configuration-file` for more details.

  .. note:: ``extraAdvertisedServiceIds``, ``notaryNodeAddress``, ``notaryClusterAddresses`` and ``bftSMaRt`` configs have been
    removed.

* **Database Tables Naming Scheme**

  To align with common conventions across all supported Corda and R3 Corda databases some table names have been changed.

  In addition, for existing contract ORM schemas that extend from CommonSchemaV1.LinearState or CommonSchemaV1.FungibleState,
  you will need to explicitly map the participants collection to a database table. Previously this mapping was done in the
  superclass, but that makes it impossible to properly configure the table name. The required change is to add the override var
  ``participants: MutableSet<AbstractParty>? = null`` field to your class, and add JPA mappings.

* **Pluggable Custom Serializers**

  With the introduction of AMQP we have introduced the requirement that to be seamlessly serializable classes, specifically
  Java classes (as opposed to Kotlin), must be compiled with the ``-parameter`` flag. However, we recognise that this
  isn't always possible, especially dealing with third party libraries in tightly controlled business environments.

  To work around this problem as simply as possible CorDapps now support the creation of pluggable proxy serializers for
  such classes. These should be written such that they create an intermediary representation that Corda can serialise that
  is mappable directly to and from the unserializable class.

  A number of examples are provided by the SIMM Valuation Demo in

  ``samples/simm-valuation-demo/src/main/kotlin/net/corda/vega/plugin/customserializers``

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
Following quickly on the heels of the release of Corda 1.0, Corda version 2.0 consolidates
a number of security updates for our dependent libraries alongside the reintroduction of the Observer node functionality.
This was absent from version 1 but based on user feedback its re-introduction removes the need for complicated "isRelevant()" checks.

In addition the fix for a small bug present in the coin selection code of V1.0 is integrated from master.

* **Version Bump**

Due to the introduction of new APIs, Corda 2.0 has a platform version of 2. This will be advertised in the network map structures
and via the versioning APIs.

* **Observer Nodes**

Adds the facility for transparent forwarding of transactions to some third party observer, such as a regulator. By having
that entity simply run an Observer node they can simply recieve a stream of digitally signed, de-duplicated reports that
can be used for reporting.

.. _release_notes_v1_0:

Release 1.0
-----------
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

Milestone 14
------------

This release continues with the goal to improve API stability and developer friendliness. There have also been more
bug fixes and other improvements across the board.

The CorDapp template repository has been replaced with a specific repository for
`Java <https://github.com/corda/cordapp-template-java>`_ and `Kotlin <https://github.com/corda/cordapp-template-kotlin>`_
to improve the experience of starting a new project and to simplify the build system.

It is now possible to specify multiple IP addresses and legal identities for a single node, allowing node operators
more flexibility in setting up nodes.

A format has been introduced for CorDapp JARs that standardises the contents of CorDapps across nodes. This new format
now requires CorDapps to contain their own external dependencies. This paves the way for significantly improved
dependency management for CorDapps with the release of `Jigsaw (Java Modules) <http://openjdk.java.net/projects/jigsaw/>`_. For those using non-gradle build systems it is important
to read :doc:`cordapp-build-systems` to learn more. Those using our ``cordformation`` plugin simply need to update
to the latest version (``0.14.0``) to get the fixes.

We've now begun the process of demarcating which classes are part of our public API and which ones are internal.
Everything found in ``net.corda.core.internal`` and other packages in the ``net.corda`` namespace which has ``.internal`` in it are
considered internal and not for public use. In a future release any CorDapp using these packages will fail to load, and
when we migrate to Jigsaw these will not be exported.

The transaction finalisation flow (``FinalityFlow``) has had hooks added for alternative implementations, for example in
scenarios where no single participant in a transaction is aware of the well known identities of all parties.

DemoBench has a fix for a rare but inconvenient crash that can occur when sharing your display across multiple devices,
e.g. a projector while performing demonstrations in front of an audience.

Guava types are being removed because Guava does not have backwards compatibility across versions, which has serious
issues when multiple libraries depend on different versions of the library.

The identity service API has been tweaked, primarily so anonymous identity registration now takes in
AnonymousPartyAndPath rather than the individual components of the identity, as typically the caller will have
an AnonymousPartyAndPath instance. See change log for further detail.

Upgrading to this release is strongly recommended in order to keep up with the API changes, removal and additions.

Milestone 13
------------

Following our first public beta in M12, this release continues the work on API stability and user friendliness. Apart
from bug fixes and code refactoring, there are also significant improvements in the Vault Query and the
Identity Service (for more detailed information about what has changed, see :doc:`changelog`).
More specifically:

The long awaited new **Vault Query** service makes its debut in this release and provides advanced vault query
capabilities using criteria specifications (see ``QueryCriteria``), sorting, and pagination. Criteria specifications
enable selective filtering with and/or composition using multiple operator primitives on standard attributes stored in
Corda internal vault tables (eg. vault_states, vault_fungible_states, vault_linear_states), and also on custom contract
state schemas defined by CorDapp developers when modelling new contract types. Custom queries are specifiable using a
simple but sophisticated builder DSL (see ``QueryCriteriaUtils``). The new Vault Query service is usable by flows and by
RPC clients alike via two simple API functions: ``queryBy()`` and ``trackBy()``. The former provides point-in-time
snapshot queries whilst the later supplements the snapshot with dynamic streaming of updates.
See :doc:`api-vault-query` for full details.

We have written a comprehensive Hello, World! tutorial, showing developers how to build a CorDapp from start
to finish. The tutorial shows how the core elements of a CorDapp - states, contracts and flows - fit together
to allow your node to handle new business processes. It also explains how you can use our contract and
flow testing frameworks to massively reduce CorDapp development time.

Certificate checks have been enabled for much of the identity service. These are part of the confidential (anonymous)
identities work, and ensure that parties are actually who they claim to be by checking their certificate path back to
the network trust root (certificate authority).

To deal with anonymized keys, we've also implemented a deterministic key derivation function that combines logic
from the HMAC-based Extract-and-Expand Key Derivation Function (HKDF) protocol and the BIP32 hardened
parent-private-key -> child-private-key scheme. This function currently supports the following algorithms:
ECDSA secp256K1, ECDSA secpR1 (NIST P-256) and EdDSA ed25519. We are now very close to fully supporting anonymous
identities so as to increase privacy even against validating notaries.

We have further tightened the set of objects which Corda will attempt to serialise from the stack during flow
checkpointing. As flows are arbitrary code in which it is convenient to do many things, we ended up pulling in a lot of
objects that didn't make sense to put in a checkpoint, such as ``Thread`` and ``Connection``. To minimize serialization
cost and increase security by not allowing certain classes to be serialized, we now support class blacklisting
that will return an ``IllegalStateException`` if such a class is encountered during a checkpoint. Blacklisting supports
superclass and superinterface inheritance and always precedes ``@CordaSerializable`` annotation checking.

We've also started working on improving user experience when searching, by adding a new RPC to support fuzzy matching
of X.500 names.

Milestone 12 - First Public Beta
--------------------------------

One of our busiest releases, lots of changes that take us closer to API stability (for more detailed information about
what has changed, see :doc:`changelog`). In this release we focused mainly on making developers' lives easier. Taking
into account feedback from numerous training courses and meet-ups, we decided to add ``CollectSignaturesFlow`` which
factors out a lot of code which CorDapp developers needed to write to get their transactions signed.
The improvement is up to 150 fewer lines of code in each flow! To have your transaction signed by different parties, you
need only now call a subflow which collects the parties' signatures for you.

Additionally we introduced classpath scanning to wire-up flows automatically. Writing CorDapps has been made simpler by
removing boiler-plate code that was previously required when registering flows. Writing services such as oracles has also been simplified.

We made substantial RPC performance improvements (please note that this is separate to node performance, we are focusing
on that area in future milestones):

- 15-30k requests per second for a single client/server RPC connection.
  * 1Kb requests, 1Kb responses, server and client on same machine, parallelism 8, measured on a Dell XPS 17(i7-6700HQ, 16Gb RAM)
- The framework is now multithreaded on both client and server side.
- All remaining bottlenecks are in the messaging layer.

Security of the key management service has been improved by removing support for extracting private keys, in order that
it can support use of a hardware security module (HSM) for key storage. Instead it exposes functionality for signing data
(typically transactions). The service now also supports multiple signature schemes (not just EdDSA).

We've added the beginnings of flow versioning. Nodes now reject flow requests if the initiating side is not using the same
flow version. In a future milestone release will add the ability to support backwards compatibility.

As with the previous few releases we have continued work extending identity support. There are major changes to the ``Party``
class as part of confidential identities, and how parties and keys are stored in transaction state objects.
See :doc:`changelog` for full details.

Added new Byzantine fault tolerant (BFT) decentralised notary demo, based on the `BFT-SMaRT protocol <https://bft-smart.github.io/library/>`_
For how to run the demo see: :ref:`notary-demo`

We continued to work on tools that enable diagnostics on the node. The newest addition to Corda Shell is ``flow watch`` command which
lets the administrator see all flows currently running with result or error information as well as who is the flow initiator.
Here is the view from DemoBench:

.. image:: resources/flowWatchCmd.png

We also started work on the strategic wire format (not integrated).

Milestone 11
------------

Special thank you to `Gary Rowe <https://github.com/gary-rowe>`_ for his contribution to Corda's Contracts DSL in M11.

Work has continued on confidential identities, introducing code to enable the Java standard libraries to work with
composite key signatures. This will form the underlying basis of future work to standardise the public key and signature
formats to enable interoperability with other systems, as well as enabling the use of composite signatures on X.509
certificates to prove association between transaction keys and identity keys.

The identity work will require changes to existing code and configurations, to replace party names with full X.500
distinguished names (see RFC 1779 for details on the construction of distinguished names). Currently this is not
enforced, however it will be in a later milestone.

* "myLegalName" in node configurations will need to be replaced, for example "Bank A" is replaced with
  "CN=Bank A,O=Bank A,L=London,C=GB". Obviously organisation, location and country ("O", "L" and "C" respectively)
  must be given values which are appropriate to the node, do not just use these example values.
* "networkMap" in node configurations must be updated to match any change to the legal name of the network map.
* If you are using mock parties for testing, try to standardise on the ``DUMMY_NOTARY``, ``DUMMY_BANK_A``, etc. provided
  in order to ensure consistency.

We anticipate enforcing the use of distinguished names in node configurations from M12, and across the network from M13.

We have increased the maximum message size that we can send to Corda over RPC from 100 KB to 10 MB.

The Corda node now disables any use of ObjectInputStream to prevent Java deserialisation within flows. This is a security fix,
and prevents the node from deserialising arbitrary objects.

We've introduced the concept of platform version which is a single integer value which increments by 1 if a release changes
any of the public APIs of the entire Corda platform. This includes the node's public APIs, the messaging protocol,
serialisation, etc. The node exposes the platform version it's on and we envision CorDapps will use this to be able to
run on older versions of the platform to the one they were compiled against. Platform version borrows heavily from Android's
API Level.

We have revamped the DemoBench user interface. DemoBench will now also be installed as "Corda DemoBench" for both Windows
and MacOSX. The original version was installed as just "DemoBench", and so will not be overwritten automatically by the
new version.

Milestone 10
------------

Special thank you to `Qian Hong <https://github.com/fracting>`_, `Marek Skocovsky <https://github.com/marekdapps>`_,
`Karel Hajek <https://github.com/polybioz>`_, and `Jonny Chiu <https://github.com/johnnyychiu>`_ for their contributions
to Corda in M10.

A new interactive **Corda Shell** has been added to the node. The shell lets developers and node administrators
easily command the node by running flows, RPCs and SQL queries. It also provides a variety of commands to monitor
the node. The Corda Shell is based on the popular `CRaSH project <http://www.crashub.org/>`_ and new commands can
be easily added to the node by simply dropping Groovy or Java files into the node's ``shell-commands`` directory.
We have many enhancements planned over time including SSH access, more commands and better tab completion.

The new "DemoBench" makes it easy to configure and launch local Corda nodes. It is a standalone desktop app that can be
bundled with its own JRE and packaged as either EXE (Windows), DMG (MacOS) or RPM (Linux-based). It has the following
features:

 #. New nodes can be added at the click of a button. Clicking "Add node" creates a new tab that lets you edit the most
    important configuration properties of the node before launch, such as its legal name and which CorDapps will be loaded.
 #. Each tab contains a terminal emulator, attached to the pseudoterminal of the node. This lets you see console output.
 #. You can launch an Corda Explorer instance for each node at the click of a button. Credentials are handed to the Corda
    Explorer so it starts out logged in already.
 #. Some basic statistics are shown about each node, informed via the RPC connection.
 #. Another button launches a database viewer in the system browser.
 #. The configurations of all running nodes can be saved into a single ``.profile`` file that can be reloaded later.

Soft Locking is a new feature implemented in the vault to prevent a node constructing transactions that attempt to use the
same input(s) simultaneously. Such transactions would result in naturally wasted effort when the notary rejects them as
double spend attempts. Soft locks are automatically applied to coin selection (eg. cash spending) to ensure that no two
transactions attempt to spend the same fungible states.

The basic Amount API has been upgraded to have support for advanced financial use cases and to better integrate with
currency reference data.

We have added optional out-of-process transaction verification. Any number of external verifier processes may be attached
to the node which can handle loadbalanced verification requests.

We have also delivered the long waited Kotlin 1.1 upgrade in M10! The new features in Kotlin allow us to write even more
clean and easy to manage code, which greatly increases our productivity.

This release contains a large number of improvements, new features, library upgrades and bug fixes. For a full list of
changes please see :doc:`changelog`.

Milestone 9
-----------

This release focuses on improvements to resiliency of the core infrastructure, with highlights including a Byzantine
fault tolerant (BFT) decentralised notary, based on the BFT-SMaRT protocol and isolating the web server from the
Corda node.

With thanks to open source contributor Thomas Schroeter for providing the BFT notary prototype, Corda can now resist
malicious attacks by members of a distributed notary service. If your notary service cluster has seven members, two can
become hacked or malicious simultaneously and the system continues unaffected! This work is still in development stage,
and more features are coming in the next snapshot!

The web server has been split out of the Corda node as part of our ongoing hardening of the node. We now provide a Jetty
servlet container pre-configured to contact a Corda node as a backend service out of the box, which means individual
webapps can have their REST APIs configured for the specific security environment of that app without affecting the
others, and without exposing the sensitive core of the node to malicious Javascript.

We have launched a global training programme, with two days of classes from the R3 team being hosted in London, New York
and Singapore. R3 members get 5 free places and seats are going fast, so sign up today.

We've started on support for confidential identities, based on the key randomisation techniques pioneered by the Bitcoin
and Ethereum communities. Identities may be either anonymous when a transaction is a part of a chain of custody, or fully
legally verified when a transaction is with a counterparty. Type safety is used to ensure the verification level of a
party is always clear and avoid mistakes. Future work will add support for generating new identity keys and providing a
certificate path to show ownership by the well known identity.

There are even more privacy improvements when a non-validating notary is used; the Merkle tree algorithm is used to hide
parts of the transaction that a non-validating notary doesn't need to see, whilst still allowing the decentralised
notary service to sign the entire transaction.

The serialisation API has been simplified and improved. Developers now only need to tag types that will be placed in
smart contracts or sent between parties with a single annotation... and sometimes even that isn't necessary!

Better permissioning in the cash CorDapp, to allow node users to be granted different permissions depending on whether
they manage the issuance, movement or ledger exit of cash tokens.

We've continued to improve error handling in flows, with information about errors being fed through to observing RPC
clients.

There have also been dozens of bug fixes, performance improvements and usability tweaks. Upgrading is definitely
worthwhile and will only take a few minutes for most apps.

For a full list of changes please see :doc:`changelog`.
