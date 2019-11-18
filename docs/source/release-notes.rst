Release notes
-------------

.. contents:: 
    :depth: 2

Welcome to the Corda 4.3 release notes. Please read these carefully to understand what’s new in this release and how the features can help you. Just as prior releases have brought with them commitments to wire and API stability, Corda 4.3 comes with those same guarantees. States and apps valid in Corda 3.0 are transparently usable in Corda 4.3.

.. _release_notes_v4_3:

Corda 4.3
=========

Corda 4.1 was released with a great suite of new features to build on top of the success of Corda 4. Now, Corda 4.3 extends upon that with some powerful new capabilities. Corda 4.3 contains over 400 fixes and documentation updates to bring additional stability and quality of life improvements to those developing on the Corda platform.

We recommend you upgrade from Corda 4.1 to Corda 4.3 as soon as possible.

Changes for developers in Corda 4.3
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Introduction of Accounts
++++++++++++++++++++++++

With Corda 4.3 we are introducing the concept of “Accounts”. Vaults can be logically partitioned into subsets, each subset representing an account.  

This is advantageous for several reasons:

* Node operators can reduce costs by hosting multiple entities, as accounts, on one node
* Node operators can partition the vault on a per entity basis
* In many cases, node owners or operators will be maintaining balances of cash, assets, or agreements on behalf of others
* Accounts allow network access to those who cannot (or do not want to) be first-class citizens on the network

This new functionality allows hosts to take a custodial role over their nodes, supporting a broader range of use-cases.

Please find more information on Accounts functionality in the `documentatation <https://github.com/corda/accounts/blob/master/docs.md>`_

Confidential Identities
+++++++++++++++++++++++

Confidential Identities have been revisited, and nodes no longer use or store X.500 certificates. Keys used for signing confidential transactions have been decoupled from the node's identity, and a nonce challenge is used to confirm a Confidential Identity belongs to the legal identity claiming it.

This removes the requirement to serialize and store the certificate chain for each new key that is registered.

In addition, confidential identities can now be shared without needing a transaction.

Improved RPC client connectivity 
++++++++++++++++++++++++++++++++

The CordaRPCClient library has been improved in Corda 4.3 to address issues where the library does not automatically reconnect to the node if the RPC connection is broken.

The improved library provides the following enhancements:

* Reconnects to the node via RPC if the RPC connection to the node is broken
* Reconnects any observables that have been created
* Retries all operations on failure, except for flow start operations that die before receiving a valid `FlowHandle`, in which case a `CouldNotStartFlowException` is thrown

We're confident in the improvements made to RPC client connectivity but would remind you that applications should be developed with contingencies in the event of an RPC connection failure. See :doc:`clientrpc` for details.

Additional flexibility in recording transactions
++++++++++++++++++++++++++++++++++++++++++++++++

In Corda 4.3, nodes can choose to record a transaction with three different levels of visibility:

* Store only the relevant states in the transaction (the default)
* Store every state in the transaction (used when observing a transaction, for example)
* Store none of the states in the transaction (used during transaction resolution, for example)

Previously, there was a limitation in that if a node initially records a transaction with a specific level of visibility, they cannot later record it with a different level of visibility.

In Corda 4.3, an enhancement has been made to observer node functionality to allow observers to re-record transactions that have already been recorded at a lower visibility. 
See :doc:`tutorial-observer-nodes` for details of how to work with observer nodes

Changes for operators in Corda 4.3
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Additional flexibility for RPC permissioning
++++++++++++++++++++++++++++++++++++++++++++

RPC permissions can now contain wildcards; for example: com.example.* matches both com.example.foo.ExampleFlow and com.example.bar.BogusFlow

Security Upgrades
+++++++++++++++++

There have been several security upgrades, including changes to the Corda webserver, dependency changes, changes to X509 extended key usage, and whitelisting attachments.

* Extended key usage: Corda certificates previously defined the X509 'Extended Key Usage' as 'anyExtendedKeyUsage' which was too broad. Only the necessary key uses are included now. For example, for Corda TLS certificates, the only required extended key usages are 'Client Authentication' and 'Server Authentication'.
* Corda webserver moved to testing module: The Corda webserver is deprecated and not suitable for production use. In Corda 4.3 it has been renamed test-server and moved to the testing module.
* Enhancements to attachment whitelisting: Transactions referencing contracts that are not installed on a node can still be accepted if the contract is signed by a trusted party.
* Updated vulnerable dependency: Jolokia 1.2 to 1.6.0 are vulnerable to system-wide cross-site-request-forgery attacks. Updated to Jolokia 1.6.1 

Platform version change
~~~~~~~~~~~~~~~~~~~~~~~

Given the addition of a new API to support the Accounts feature, the platform version of Corda 4.3 has been bumped up from 4 to 5. This is to prevent CorDapps that use it being deployed onto nodes unable to host them. Note that the minimum platform version has not been changed - this means that older Corda nodes can still interoperate with Corda 4.3 nodes. Since the APIs added do not affect the wire protocol or have other zone-level implications, applications can take advantage of these new platform version 5 features even if the Corda 4.3 node is running on a network whose minimum platform version is 4.

For more information on platform version, please see :doc:`versioning`. For more details on upgrading a CorDapp to use platform version 5, please see :doc:`app-upgrade-notes`.

Deprecations
~~~~~~~~~~~~

The Corda Finance library is now deprecated and has been superseded by the Corda Tokens SDK. While the finance library is not yet being removed, we are no longer improving or updating it. We strongly encourage users to transition from the Corda Finance library to the Corda Tokens SDK. Find more information and begin using the tokens SDK in the GitHub repository `here <https://github.com/corda/token-sdk>`_

Any confidential identities registered using the old API will not be reflected in the new tables after migration to Corda 4.3. However, the standard APIs work with both old and new confidential identities tables. For this reason, we do not recommend the use of both old and new confidential identities APIs in the same deployment. The old confidential identities API will be deprecated in a future release.

Issues Fixed
~~~~~~~~~~~~

* Register custom serializers for jackson as well as amqp [`CORDA-3152 <https://r3-cev.atlassian.net/browse/CORDA-3152>`_]
* Cleanup non-finalised, errored flows [`CORDA-3122 <https://r3-cev.atlassian.net/browse/CORDA-3122>`_]
* Introduce max number of retries per invocation for reconnecting rpc [`CORDA-3304 <https://r3-cev.atlassian.net/browse/CORDA-3304>`_]
* Fix for CORDA-3315 [`CORDA-3315 <https://r3-cev.atlassian.net/browse/CORDA-3315>`_]
* Add a check for shutdown to avoid some of the errors ()" , (#5578) [`Revert "CORDA-3281 <https://r3-cev.atlassian.net/browse/Revert "CORDA-3281>`_]
* RPC Invocation fails when calling classes with defaulted constructors O/S [`CORDA-3043 <https://r3-cev.atlassian.net/browse/CORDA-3043>`_]
* Avoid flushing when inside a cascade [`CORDA-3303 <https://r3-cev.atlassian.net/browse/CORDA-3303>`_]
* fix observables not being tagged with notUsed() [`CORDA-3236 <https://r3-cev.atlassian.net/browse/CORDA-3236>`_]
* deployNodes doesn't use right version of Java [`ISSUE-246 <https://r3-cev.atlassian.net/browse/ISSUE-246>`_]
* Remove quasarRPC client [`CORDA-2979 <https://r3-cev.atlassian.net/browse/CORDA-2979>`_]
* Fix infinite loop [`CORDA-3306 <https://r3-cev.atlassian.net/browse/CORDA-3306>`_]
* Add a check for shutdown to avoid some of the errors [`CORDA-3281 <https://r3-cev.atlassian.net/browse/CORDA-3281>`_]
* Make Tx verification exceptions serializable [`CORDA-2965 <https://r3-cev.atlassian.net/browse/CORDA-2965>`_]
* Node configuration doc change [`CORDA-2756 <https://r3-cev.atlassian.net/browse/CORDA-2756>`_]
* Improve error handling for registering peer node [`CORDA-3263 <https://r3-cev.atlassian.net/browse/CORDA-3263>`_]
* JDK11,  built and published artifacts to include classifier [`CORDA-3224 <https://r3-cev.atlassian.net/browse/CORDA-3224>`_]
* Missing logs on shutdown [`CORDA-3246 <https://r3-cev.atlassian.net/browse/CORDA-3246>`_]
* Improve CorDapp loading logic for duplicates [`CORDA-3243 <https://r3-cev.atlassian.net/browse/CORDA-3243>`_]
* Publish checkpoint agent jar and allow for inclusion of version id in jar upon run-time execution
* O/S version of fix for slow running in 4.3 [`CORDA-3235 <https://r3-cev.atlassian.net/browse/CORDA-3235>`_]
* Enhance backwards compatibility logic to include Interâ€¦ [`CORDA-3274 <https://r3-cev.atlassian.net/browse/CORDA-3274>`_]
* Prevent node startup failure upon cross-platform execution [`CORDA-2050 <https://r3-cev.atlassian.net/browse/CORDA-2050>`_]
* Remove Gradle's evaluation dependency on node:capsule [`CORDA-2050 <https://r3-cev.atlassian.net/browse/CORDA-2050>`_]
* New detekt rules based on feedback [`TM-44 <https://r3-cev.atlassian.net/browse/TM-44>`_]
* Remove Gradle's evaluation dependency on node:capsule [`CORDA-2050 <https://r3-cev.atlassian.net/browse/CORDA-2050>`_]
* Fix dba migration for PostgreSQL following changes in CORDA-3009 [`CORDA-3226 <https://r3-cev.atlassian.net/browse/CORDA-3226>`_]
* Vault Query API enhancement, strict participants matching [`CORDA-3184 <https://r3-cev.atlassian.net/browse/CORDA-3184>`_]
* Move executor thread management into CordaRPCConnection [`CORDA-3091 <https://r3-cev.atlassian.net/browse/CORDA-3091>`_]
* Replace deprecated use of Class.newInstance() for sake of DJVM [`CORDA-3273 <https://r3-cev.atlassian.net/browse/CORDA-3273>`_]
* Support of multiple interfaces for RPC calls [`CORDA-3232 <https://r3-cev.atlassian.net/browse/CORDA-3232>`_]
* Rename the webserver [`CORDA-3024 <https://r3-cev.atlassian.net/browse/CORDA-3024>`_]
* optional node.conf property not recognized when overridden [`CORDA-3240 <https://r3-cev.atlassian.net/browse/CORDA-3240>`_]
* Add missing quasar classifier to web server capsule manifest [`CORDA-3266 <https://r3-cev.atlassian.net/browse/CORDA-3266>`_]
* Revert back to quasar 0.7.10 (Java 8) [`CORDA-2050 <https://r3-cev.atlassian.net/browse/CORDA-2050>`_]
* Ensure that ArraySerializer.elementType is resolved for GenericArray [`CORDA-2050 <https://r3-cev.atlassian.net/browse/CORDA-2050>`_]
* backporting detekt config changes to OS 4.1 and rebaselining [`TM-32 <https://r3-cev.atlassian.net/browse/TM-32>`_]
* Fix vault query for participants specified in common criteria [`CORDA-3209 <https://r3-cev.atlassian.net/browse/CORDA-3209>`_]
* Do not add java.lang.Class fields and properties to local type cache [`CORDA-2050 <https://r3-cev.atlassian.net/browse/CORDA-2050>`_]
* Fix Classgraph scanning lock type [`CORDA-3238 <https://r3-cev.atlassian.net/browse/CORDA-3238>`_]
* Added exception handling for missing files that displays appropriate messages rather than defaulting to file names [`CORDA-2368 <https://r3-cev.atlassian.net/browse/CORDA-2368>`_]
* new baseline for 4.3 since new debt has been added with the last few commits [`TM-29 <https://r3-cev.atlassian.net/browse/TM-29>`_]
* Upgrade Corda to Java 11 (compatibility mode) [`CORDA-2050 <https://r3-cev.atlassian.net/browse/CORDA-2050>`_]
* Add GracefulReconnect callbacks which allow logic to be performed when RPC disconnects unexpectedly [`CORDA-3141 <https://r3-cev.atlassian.net/browse/CORDA-3141>`_]
* Checkpoints which cannot be deserialised no longer prevent the nodestarting up [`CORDA-1836 <https://r3-cev.atlassian.net/browse/CORDA-1836>`_]
* Make set of serializer types considered suitable for object reference to be configurable [`CORDA-3218 <https://r3-cev.atlassian.net/browse/CORDA-3218>`_]
* Notary logging improvements [`CORDA-3060 <https://r3-cev.atlassian.net/browse/CORDA-3060>`_]
* Improve Notary loggingan operator/admins point of view [`CORDA-3060 <https://r3-cev.atlassian.net/browse/CORDA-3060>`_]
* Make set of serializer types considered suitable for object reference to be configurable [`CORDA-3218 <https://r3-cev.atlassian.net/browse/CORDA-3218>`_]
* Fix postgres oid/ bytea column issue [`CORDA-3200 <https://r3-cev.atlassian.net/browse/CORDA-3200>`_]
* Load drivers directory automatically [`CORDA-3079 <https://r3-cev.atlassian.net/browse/CORDA-3079>`_]
* Fixed bug where observable leaks on ctrl+c interrupt while waiting in stateMachinesFeed [`CORDA-3151 <https://r3-cev.atlassian.net/browse/CORDA-3151>`_]
* Fail build on compiler warnings [`TM-23 <https://r3-cev.atlassian.net/browse/TM-23>`_]
* (Version 2) [`CORDA-3133 <https://r3-cev.atlassian.net/browse/CORDA-3133>`_]
* Prevent node running SwapIdentitiesFlowinitiating session with itself [`CORDA-2837 <https://r3-cev.atlassian.net/browse/CORDA-2837>`_]
* Split migrations as per https://github.com/ENTerprisâ€¦ [`CORDA-3200 <https://r3-cev.atlassian.net/browse/CORDA-3200>`_]
* Remove RPC exception obfuscation [`CORDA-2740 <https://r3-cev.atlassian.net/browse/CORDA-2740>`_]
* Whitelisting attachments by public key, phase two tooling [`CORDA-3018 <https://r3-cev.atlassian.net/browse/CORDA-3018>`_]
* Use PersistentIdentityMigrationBuilder instead of schema aâ€¦ [`CORDA-3200 <https://r3-cev.atlassian.net/browse/CORDA-3200>`_]
* Add -XX:+HeapDumpOnOutOfMemoryError -XX:+CrashOnOutOfMemoryError to default JVM args for node [`CORDA-3187 <https://r3-cev.atlassian.net/browse/CORDA-3187>`_]
* Ignore synthetic and static fields when searching for state pointers [`CORDA-3188 <https://r3-cev.atlassian.net/browse/CORDA-3188>`_]
* Additional Back Chain Resolution performance enhancements [`CORDA-3177 <https://r3-cev.atlassian.net/browse/CORDA-3177>`_]
* Close security manager after broker is shut down [`CORDA-2890 <https://r3-cev.atlassian.net/browse/CORDA-2890>`_]
* Added additional property on VaultQueryCriteria for querying by account [`CORDA-3182 <https://r3-cev.atlassian.net/browse/CORDA-3182>`_]
* Added ability to lookup the associated UUID for a public key to KeyManagementService [`CORDA-3180 <https://r3-cev.atlassian.net/browse/CORDA-3180>`_]
* Remove dependency on 3rd party javax.xml.bind library for simple hex parsing/printing [`CORDA-3175 <https://r3-cev.atlassian.net/browse/CORDA-3175>`_]
* FilterMyKeys now uses the key store as opposed to the cert store [`CORDA-3178 <https://r3-cev.atlassian.net/browse/CORDA-3178>`_]
* Refine documentation around rpc reconnection [`CORDA-3106 <https://r3-cev.atlassian.net/browse/CORDA-3106>`_]
* Rebase identity service changes onto 4.3 [`CORDA-2925 <https://r3-cev.atlassian.net/browse/CORDA-2925>`_]
* Close previous connection after reconnection [`CORDA-3098 <https://r3-cev.atlassian.net/browse/CORDA-3098>`_]
* Add wildcard RPC permissions [`CORDA-3022 <https://r3-cev.atlassian.net/browse/CORDA-3022>`_]
* Migrate identity service to use to string short [`CORDA-3009 <https://r3-cev.atlassian.net/browse/CORDA-3009>`_]
* Modify Corda's custom serialiser support for the DJVM [`CORDA-3157 <https://r3-cev.atlassian.net/browse/CORDA-3157>`_]
* JacksonSupport, for CordaSerializable classes, improved to only uses those properties that are part of Corda serialisation [`CORDA-2919 <https://r3-cev.atlassian.net/browse/CORDA-2919>`_]
* Update cache to check node identity keys in identity table [`CORDA-3149 <https://r3-cev.atlassian.net/browse/CORDA-3149>`_]
* Removed InMemoryTransactionsResolver as it's not needed and other resolution cleanup [`CORDA-3138 <https://r3-cev.atlassian.net/browse/CORDA-3138>`_]
* Update app upgrade notes to document source incompatibility [`CORDA-3082 <https://r3-cev.atlassian.net/browse/CORDA-3082>`_]
* improvements to checkpoint dumper [`CORDA-3094 <https://r3-cev.atlassian.net/browse/CORDA-3094>`_]
* Add a cache for looking up external UUIDspublic keys [`CORDA-3130 <https://r3-cev.atlassian.net/browse/CORDA-3130>`_]
* Cater for port already bound scenario during port allocation [`CORDA-3139 <https://r3-cev.atlassian.net/browse/CORDA-3139>`_]
* Update owasp scanner [`CORDA-3120 <https://r3-cev.atlassian.net/browse/CORDA-3120>`_]
* Whitelisting attachments by public key, relax signer restrictions [`CORDA-3018 <https://r3-cev.atlassian.net/browse/CORDA-3018>`_]
* Add failover listeners to terminate node process [`CORDA-2617 <https://r3-cev.atlassian.net/browse/CORDA-2617>`_]
* Parallel node info download [`CORDA-3055 <https://r3-cev.atlassian.net/browse/CORDA-3055>`_]
* Checkpoint agent tool [`CORDA-3071 <https://r3-cev.atlassian.net/browse/CORDA-3071>`_]
* More information in log warning for Cordapps missing advised JAR manifest file entries [`CORDA-3012 <https://r3-cev.atlassian.net/browse/CORDA-3012>`_]
* Restore CompositeKey support to core-deterministic [`CORDA-2871 <https://r3-cev.atlassian.net/browse/CORDA-2871>`_]
* Restrict extended key usage of certificate types [`CORDA-2216 <https://r3-cev.atlassian.net/browse/CORDA-2216>`_]
* Hash to Signature Constraint automatic propagation [`CORDA-2920 <https://r3-cev.atlassian.net/browse/CORDA-2920>`_]
* Prevent connection threads leaking on reconnect [`CORDA-2923 <https://r3-cev.atlassian.net/browse/CORDA-2923>`_]
* Exception is logged if flow session message can't be deserialised [`CORDA-3092 <https://r3-cev.atlassian.net/browse/CORDA-3092>`_]
* Do not throw exception for missing fiber and log instead
* Make the RPC client reconnect with gracefulReconnect param [`CORDA-2923 <https://r3-cev.atlassian.net/browse/CORDA-2923>`_]
* Pass base directory when resolving relative paths [`CORDA-3068 <https://r3-cev.atlassian.net/browse/CORDA-3068>`_]
* Add Node Diagnostics Info RPC Call, Update changelog [`CORDA-3028 <https://r3-cev.atlassian.net/browse/CORDA-3028>`_]
* Add Node Diagnostics Info RPC Call, Backport a diff from [`CORDA-3028 <https://r3-cev.atlassian.net/browse/CORDA-3028>`_]
* fix network builder [`CORDA-2998 <https://r3-cev.atlassian.net/browse/CORDA-2998>`_]
* Add Node Diagnostics Info RPC Call [`CORDA-3028 <https://r3-cev.atlassian.net/browse/CORDA-3028>`_]
* Allow transactions to be re-recorded using StatesToRecord.ALL_VISIBLE [`CORDA-2086 <https://r3-cev.atlassian.net/browse/CORDA-2086>`_]
* shorten poll intervals for node info file propagation [`CORDA-2991 <https://r3-cev.atlassian.net/browse/CORDA-2991>`_]
* Allow certificate directory to be a symlink [`CORDA-2914 <https://r3-cev.atlassian.net/browse/CORDA-2914>`_]
* fix network builder [`CORDA-2998 <https://r3-cev.atlassian.net/browse/CORDA-2998>`_]
* min after normal operation [`CORDA-3034. Reconnecting Rpc will now not wait only for 60 <https://r3-cev.atlassian.net/browse/CORDA-3034. Reconnecting Rpc will now not wait only for 60>`_]
* Refactor NodeConfiguration out of NodeRegistrationHelper [`CORDA-2720 <https://r3-cev.atlassian.net/browse/CORDA-2720>`_]
* NotaryLoader, improve exception handling [`CORDA-2996 <https://r3-cev.atlassian.net/browse/CORDA-2996>`_]
* Introduce `SignOnlyCryptoService` and use it whenever possible [`CORDA-3021 <https://r3-cev.atlassian.net/browse/CORDA-3021>`_]
* Introducing Destination interface for initiating flows with [`CORDA-3033 <https://r3-cev.atlassian.net/browse/CORDA-3033>`_]
* Fine-tune compile vs runtime scopes of published deterministic jars [`CORDA-2871 <https://r3-cev.atlassian.net/browse/CORDA-2871>`_]
* Upgrade notes for C4 need to include required minimum previous Corda version () , (#5124) [`CORDA-2511 <https://r3-cev.atlassian.net/browse/CORDA-2511>`_]
* Align timeouts for CRL retrieval and TLS handshake [`CORDA-2935 <https://r3-cev.atlassian.net/browse/CORDA-2935>`_]
* disable hibernate validator integration with hibernate () , (#5144) [`CORDA-2934 <https://r3-cev.atlassian.net/browse/CORDA-2934>`_]
* Fix release tooling when product name != jira project [`CORDA-3017 <https://r3-cev.atlassian.net/browse/CORDA-3017>`_]
* Constrain max heap size for Spring boot processes [`CORDA-3031 <https://r3-cev.atlassian.net/browse/CORDA-3031>`_]
* Updated the majority of the dependencies that were out of date [`CORDA-2333 <https://r3-cev.atlassian.net/browse/CORDA-2333>`_]
* Allow AbstractParty to initiate flow [`CORDA-3000 <https://r3-cev.atlassian.net/browse/CORDA-3000>`_]
* Reverting jersey and mockito as it currently causes issues with ENT [`CORDA-2333 <https://r3-cev.atlassian.net/browse/CORDA-2333>`_]
* Fixing x500Prinicipal matching [`CORDA-2974 <https://r3-cev.atlassian.net/browse/CORDA-2974>`_]
* Fix for liquibase changelog warnings [`CORDA-2774 <https://r3-cev.atlassian.net/browse/CORDA-2774>`_]
* Add documentation on the options for deploying nodes [`CORDA-1912 <https://r3-cev.atlassian.net/browse/CORDA-1912>`_]
* Disable slow consumers for RPC since it doesn't work [`CORDA-2981 <https://r3-cev.atlassian.net/browse/CORDA-2981>`_]
* Revert usage of Gradle JUnit 5 Platform Runner [`CORDA-2970 <https://r3-cev.atlassian.net/browse/CORDA-2970>`_]
* Fix for CORDA-2972 [`CORDA-2972 <https://r3-cev.atlassian.net/browse/CORDA-2972>`_]
* Catch IllegalArgumentException to avoid shutdown of NodeExplorer [`CORDA-2945 <https://r3-cev.atlassian.net/browse/CORDA-2945>`_]
* Remove version uniqueness check [`CORDA-2975 <https://r3-cev.atlassian.net/browse/CORDA-2975>`_]
* Support for custom Jackson serializers ()" , (#5167) [`Revert "CORDA-2773 <https://r3-cev.atlassian.net/browse/Revert "CORDA-2773>`_]
* disable hibernate validator integration with hibernate [`CORDA-2934 <https://r3-cev.atlassian.net/browse/CORDA-2934>`_]
* improve error messages for non composable types [`CORDA-2870 <https://r3-cev.atlassian.net/browse/CORDA-2870>`_]
* Align timeouts for CRL retrieval and TLS handshake [`CORDA-2935 <https://r3-cev.atlassian.net/browse/CORDA-2935>`_]
* Remove AMQP system property [`CORDA-2473 <https://r3-cev.atlassian.net/browse/CORDA-2473>`_]
* Simple prose checking [`DEVREL-1287 <https://r3-cev.atlassian.net/browse/DEVREL-1287>`_]
* Minor Typos & Commands info in "Other transaction components" intro [`DEVREL-1287 <https://r3-cev.atlassian.net/browse/DEVREL-1287>`_]
* Minor Typographic Changes [`DEVREL-1287 <https://r3-cev.atlassian.net/browse/DEVREL-1287>`_]
* Whitelist attachments signed by keys that already sign existing trusted attachments [`CORDA-2517 <https://r3-cev.atlassian.net/browse/CORDA-2517>`_]
* Prevent node startup if legal identity key is lost but node key isn't [`CORDA-2866 <https://r3-cev.atlassian.net/browse/CORDA-2866>`_]
* change default dataSource.url to match the docker container structure [`CORDA-2888 <https://r3-cev.atlassian.net/browse/CORDA-2888>`_]
* change documentation [`CORDA-2641 <https://r3-cev.atlassian.net/browse/CORDA-2641>`_]
* Allow bring-your-own-config to docker image [`CORDA-2888 <https://r3-cev.atlassian.net/browse/CORDA-2888>`_]
* Remove the CanonicalizerPluginbuildSrc [`CORDA-2902 <https://r3-cev.atlassian.net/browse/CORDA-2902>`_]
* Improve Signature Constraints documentation [`CORDA-2477 <https://r3-cev.atlassian.net/browse/CORDA-2477>`_]
* Automatic propagation of whitelisted to Signature Constraints [`CORDA-2280 <https://r3-cev.atlassian.net/browse/CORDA-2280>`_]
* Docker build tasks will pull the corda jarartifactory [`CORDA-2884 <https://r3-cev.atlassian.net/browse/CORDA-2884>`_]
* Support for custom Jackson serializers [`CORDA-2773 <https://r3-cev.atlassian.net/browse/CORDA-2773>`_]
* Added ability to specify signature scheme when signing [`CORDA-2882 <https://r3-cev.atlassian.net/browse/CORDA-2882>`_]
* Drop the acknowledge window for RPC responses to 16KB1MB because the memory footprint is multipled by the number of RPC clients [`CORDA-2845 <https://r3-cev.atlassian.net/browse/CORDA-2845>`_]
* Handle exceptions when file does not exist [`CORDA-2632 <https://r3-cev.atlassian.net/browse/CORDA-2632>`_]
* Allow users to whitelist attachments by public key config [`CORDA-2575 <https://r3-cev.atlassian.net/browse/CORDA-2575>`_]
* Remove CORDA_VERSION_THAT_INTRODUCED_FLATTENED_COMMANDS as commands are not flattened anymore [`CORDA-2817 <https://r3-cev.atlassian.net/browse/CORDA-2817>`_]
* Fix issue with Quasar errors redirecting to useless page [` CORDA-2821 <https://r3-cev.atlassian.net/browse/ CORDA-2821>`_]
* Support custom serialisers when attaching missing attachments to txs [`CORDA-2847 <https://r3-cev.atlassian.net/browse/CORDA-2847>`_]
* Use `compileOnly` instead of `cordaCompile` in irs-demo to depend on `node` module
* Improvements to docker image , compatible with v3.3 [`CORDA-4954 <https://r3-cev.atlassian.net/browse/CORDA-4954>`_]
* Add peer information to stacktrace of received FlowException [`CORDA-2572 <https://r3-cev.atlassian.net/browse/CORDA-2572>`_]
* Fix to allow softlinks of logs directory [`CORDA-2862 <https://r3-cev.atlassian.net/browse/CORDA-2862>`_]
* Add dynamic port allocation [`CORDA-2743 <https://r3-cev.atlassian.net/browse/CORDA-2743>`_]
* relax property type checking [`CORDA-2860 <https://r3-cev.atlassian.net/browse/CORDA-2860>`_]
* give the message executor its own artemis session and producer [`CORDA-2861 <https://r3-cev.atlassian.net/browse/CORDA-2861>`_]
* Do not remove exception information in dev mode [`CORDA-2645 <https://r3-cev.atlassian.net/browse/CORDA-2645>`_]
* Update getting setup guide java details [`CORDA-2602 <https://r3-cev.atlassian.net/browse/CORDA-2602>`_]
* Documentation around explicit upgrades [`CORDA-2456 <https://r3-cev.atlassian.net/browse/CORDA-2456>`_]
* Follow up changes to error reporting around failed flows [`CORDA-2522 <https://r3-cev.atlassian.net/browse/CORDA-2522>`_]
* change parameter syntax to conform to Corda CLI guidelines [`CORDA-2833 <https://r3-cev.atlassian.net/browse/CORDA-2833>`_]
* relax fingerprinter strictness [`CORDA-2848 <https://r3-cev.atlassian.net/browse/CORDA-2848>`_]
* Check if resources are in classpath [`CORDA-2651 <https://r3-cev.atlassian.net/browse/CORDA-2651>`_]
* Improve error reporting around failed flows [`CORDA-2522 <https://r3-cev.atlassian.net/browse/CORDA-2522>`_]
* Fix the way serialization whitelist is calculated for CordappImpl [`CORDA-2851 <https://r3-cev.atlassian.net/browse/CORDA-2851>`_]
* Changed crash version to our latest [`CORDA-2519 <https://r3-cev.atlassian.net/browse/CORDA-2519>`_]
* Clarify error message when base directory doesn't exist [`CORDA-2834 <https://r3-cev.atlassian.net/browse/CORDA-2834>`_]
* change message when rpc/p2p login fails [`CORDA-2621 <https://r3-cev.atlassian.net/browse/CORDA-2621>`_]
* nodeinfo signing tool [`CORDA-2833 <https://r3-cev.atlassian.net/browse/CORDA-2833>`_]
* Restructure evolution serialization errors to print reason first [`CORDA-2633 <https://r3-cev.atlassian.net/browse/CORDA-2633>`_]
* Add Java samples to upgrading to Corda 4 documentation [`CORDA-2710 <https://r3-cev.atlassian.net/browse/CORDA-2710>`_]
* Update contract testing documentation [`CORDA-2528 <https://r3-cev.atlassian.net/browse/CORDA-2528>`_]
* Do not start the P2P consumer until we have at least one registered handler (the state machine). This prevents message being delivered too early
* Fix Progress Tracker bug [`CORDA-2825 <https://r3-cev.atlassian.net/browse/CORDA-2825>`_]

.. _release_notes_v4_1:

Corda 4.1
=========

It's been a little under 3 1/2 months since the release of Corda 4.0 and all of the brand new features that added to the powerful suite
of tools Corda offers. Now, following the release of Corda Enterprise 4.0, we are proud to release Corda 4.1, bringing over 150 fixes
and documentation updates to bring additional stability and quality of life improvements to those developing on the Corda platform.

Information on Corda Enterprise 4.0 can be found `here <https://www.r3.com/wp-content/uploads/2019/05/CordaEnterprise4_Enhancements_FS.pdf>`_ and
`here <https://docs.corda.r3.com/releases/4.0/release-notes.html>`_. (It's worth noting that normally this document would have started with a comment
about whether or not you'd been recently domiciled under some solidified mineral material regarding the release of Corda Enterprise 4.0. Alas, we made
that joke when we shipped the first release of Corda after Enterprise 3.0 shipped, so the thunder has been stolen and repeating ourselves would be terribly gauche.)

Corda 4.1 brings the lessons and bug fixes discovered during the process of building and shipping Enterprise 4.0 back to the open source community. As mentioned above
there are over 150 fixes and tweaks here. With this release the core feature sets of both entities are far closer aligned than past major
releases of the Corda that should make testing your CorDapps in mixed type environments much easier.

As such, we recommend you upgrade from Corda 4.0 to Corda 4.1 as soon possible.

Issues Fixed
~~~~~~~~~~~~

* Docker images do not support passing a prepared config with initial registration [`CORDA-2888 <https://r3-cev.atlassian.net/browse/CORDA-2888>`_]
* Different hashes for container Corda and normal Corda jars [`CORDA-2884 <https://r3-cev.atlassian.net/browse/CORDA-2884>`_]
* Auto attachment of dependencies fails to find class [`CORDA-2863 <https://r3-cev.atlassian.net/browse/CORDA-2863>`_]
* Artemis session can't be used in more than one thread [`CORDA-2861 <https://r3-cev.atlassian.net/browse/CORDA-2861>`_]
* Property type checking is overly strict [`CORDA-2860 <https://r3-cev.atlassian.net/browse/CORDA-2860>`_]
* Serialisation bug (or not) when trying to run SWIFT Corda Settler tests [`CORDA-2848 <https://r3-cev.atlassian.net/browse/CORDA-2848>`_]
* Custom serialisers not found when running mock network tests [`CORDA-2847 <https://r3-cev.atlassian.net/browse/CORDA-2847>`_]
* Base directory error message where directory does not exist is slightly misleading [`CORDA-2834 <https://r3-cev.atlassian.net/browse/CORDA-2834>`_]
* Progress tracker not reloadable in checkpoints written in Java [`CORDA-2825 <https://r3-cev.atlassian.net/browse/CORDA-2825>`_]
* Missing quasar error points to non-existent page [`CORDA-2821 <https://r3-cev.atlassian.net/browse/CORDA-2821>`_]
* ``TransactionBuilder`` can build unverifiable transactions in V5 if more than one CorDapp loaded [`CORDA-2817 <https://r3-cev.atlassian.net/browse/CORDA-2817>`_]
* The node hangs when there is a dis-connection of Oracle database [`CORDA-2813 <https://r3-cev.atlassian.net/browse/CORDA-2813>`_]
* Docs: fix the latex warnings in the build [`CORDA-2809 <https://r3-cev.atlassian.net/browse/CORDA-2809>`_]
* Docs: build the docs page needs updating [`CORDA-2808 <https://r3-cev.atlassian.net/browse/CORDA-2808>`_]
* Don't retry database transaction in abstract node start [`CORDA-2807 <https://r3-cev.atlassian.net/browse/CORDA-2807>`_]
* Upgrade Corda Core to use Java Persistence API 2.2 [`CORDA-2804 <https://r3-cev.atlassian.net/browse/CORDA-2804>`_]
* Network map stopped updating on Testnet staging notary [`CORDA-2803 <https://r3-cev.atlassian.net/browse/CORDA-2803>`_]
* Improve test reliability by eliminating fixed-duration Thread.sleeps [`CORDA-2802 <https://r3-cev.atlassian.net/browse/CORDA-2802>`_]
* Not handled exception when certificates directory is missing [`CORDA-2786 <https://r3-cev.atlassian.net/browse/CORDA-2786>`_]
* Unable to run FinalityFlow if the initiating app has ``targetPlatformVersion=4`` and the recipient is using the old version [`CORDA-2784 <https://r3-cev.atlassian.net/browse/CORDA-2784>`_]
* Performing a registration with an incorrect Config gives error without appropriate info [`CORDA-2783 <https://r3-cev.atlassian.net/browse/CORDA-2783>`_]
* Regression: ``java.lang.Comparable`` is not on the default whitelist but never has been [`CORDA-2782 <https://r3-cev.atlassian.net/browse/CORDA-2782>`_]
* Docs: replace version string with things that get substituted [`CORDA-2781 <https://r3-cev.atlassian.net/browse/CORDA-2781>`_]
* Inconsistent docs between internal and external website [`CORDA-2779 <https://r3-cev.atlassian.net/browse/CORDA-2779>`_]
* Change the doc substitution so that it works in code blocks as well as in other places [`CORDA-2777 <https://r3-cev.atlassian.net/browse/CORDA-2777>`_]
* ``net.corda.core.internal.LazyStickyPool#toIndex`` can create a negative index [`CORDA-2772 <https://r3-cev.atlassian.net/browse/CORDA-2772>`_]
* ``NetworkMapUpdater#fileWatcherSubscription`` is never assigned and hence the subscription is never cleaned up [`CORDA-2770 <https://r3-cev.atlassian.net/browse/CORDA-2770>`_]
* Infinite recursive call in ``NetworkParameters.copy`` [`CORDA-2769 <https://r3-cev.atlassian.net/browse/CORDA-2769>`_]
* Unexpected exception de-serializing throwable for ``OverlappingAttachmentsException`` [`CORDA-2765 <https://r3-cev.atlassian.net/browse/CORDA-2765>`_]
* Always log config to log file [`CORDA-2763 <https://r3-cev.atlassian.net/browse/CORDA-2763>`_]
* ``ReceiveTransactionFlow`` states to record flag gets quietly ignored if ``checkSufficientSignatures = false`` [`CORDA-2762 <https://r3-cev.atlassian.net/browse/CORDA-2762>`_]
* Fix Driver's ``PortAllocation`` class, and then use it for Node's integration tests. [`CORDA-2759 <https://r3-cev.atlassian.net/browse/CORDA-2759>`_]
* State machine logs an error prior to deciding to escalate to an error [`CORDA-2757 <https://r3-cev.atlassian.net/browse/CORDA-2757>`_]
* Migrate DJVM into a separate module [`CORDA-2750 <https://r3-cev.atlassian.net/browse/CORDA-2750>`_]
* Error in ``HikariPool`` in the performance cluster [`CORDA-2748 <https://r3-cev.atlassian.net/browse/CORDA-2748>`_]
* Package DJVM CLI for standalone distribution [`CORDA-2747 <https://r3-cev.atlassian.net/browse/CORDA-2747>`_]
* Unable to insert state into vault if notary not on network map [`CORDA-2745 <https://r3-cev.atlassian.net/browse/CORDA-2745>`_]
* Create sample code and integration tests to showcase rpc operations that support reconnection [`CORDA-2743 <https://r3-cev.atlassian.net/browse/CORDA-2743>`_]
* RPC v4 client unable to subscribe to progress tracker events from Corda 3.3 node [`CORDA-2742 <https://r3-cev.atlassian.net/browse/CORDA-2742>`_]
* Doc Fix: Rpc client connection management section not fully working in Corda 4 [`CORDA-2741 <https://r3-cev.atlassian.net/browse/CORDA-2741>`_]
* ``AnsiProgressRenderer`` may start reporting incorrect progress if tree contains identical steps [`CORDA-2738 <https://r3-cev.atlassian.net/browse/CORDA-2738>`_]
* The ``FlowProgressHandle`` does not always return expected results [`CORDA-2737 <https://r3-cev.atlassian.net/browse/CORDA-2737>`_]
* Doc fix: integration testing tutorial could do with some gradle instructions [`CORDA-2729 <https://r3-cev.atlassian.net/browse/CORDA-2729>`_]
* Release upgrade to Corda 4 notes: include upgrading quasar.jar explicitly in the Corda Kotlin template [`CORDA-2728 <https://r3-cev.atlassian.net/browse/CORDA-2728>`_]
* DJVM CLI log file is always empty [`CORDA-2725 <https://r3-cev.atlassian.net/browse/CORDA-2725>`_]
* DJVM documentation incorrect around `djvm check` [`CORDA-2721 <https://r3-cev.atlassian.net/browse/CORDA-2721>`_]
* Doc fix: reflect the CorDapp template doc changes re quasar/test running the official docs [`CORDA-2715 <https://r3-cev.atlassian.net/browse/CORDA-2715>`_]
* Upgrade to Corda 4 test docs only have Kotlin examples [`CORDA-2710 <https://r3-cev.atlassian.net/browse/CORDA-2710>`_]
* Log message "Cannot find flow corresponding to session" should not be a warning [`CORDA-2706 <https://r3-cev.atlassian.net/browse/CORDA-2706>`_]
* Flow failing due to "Flow sessions were not provided" for its own identity [`CORDA-2705 <https://r3-cev.atlassian.net/browse/CORDA-2705>`_]
* RPC user security using ``Shiro`` docs have errant commas in example config [`CORDA-2703 <https://r3-cev.atlassian.net/browse/CORDA-2703>`_]
* The ``crlCheckSoftFail`` option is not respected, allowing transactions even if strict checking is enabled [`CORDA-2701 <https://r3-cev.atlassian.net/browse/CORDA-2701>`_]
* Vault paging fails if setting max page size to `Int.MAX_VALUE` [`CORDA-2698 <https://r3-cev.atlassian.net/browse/CORDA-2698>`_]
* Upgrade to Corda Gradle Plugins 4.0.41 [`CORDA-2697 <https://r3-cev.atlassian.net/browse/CORDA-2697>`_]
* Corda complaining of duplicate classes upon start-up when it doesn't need to [`CORDA-2696 <https://r3-cev.atlassian.net/browse/CORDA-2696>`_]
* Launching node explorer for node creates error and explorer closes [`CORDA-2694 <https://r3-cev.atlassian.net/browse/CORDA-2694>`_]
* Transactions created in V3 cannot be verified in V4 if any of the state types were included in "depended upon" CorDapps which were not attached to the transaction [`CORDA-2692 <https://r3-cev.atlassian.net/browse/CORDA-2692>`_]
* Reduce CorDapp scanning logging [`CORDA-2690 <https://r3-cev.atlassian.net/browse/CORDA-2690>`_]
* Clean up verbose warning: `ProgressTracker has not been started` [`CORDA-2689 <https://r3-cev.atlassian.net/browse/CORDA-2689>`_]
* Add a no-carpenter context [`CORDA-2688 <https://r3-cev.atlassian.net/browse/CORDA-2688>`_]
* Improve CorDapp upgrade guidelines for migrating existing states on ledger (pre-V4) [`CORDA-2684 <https://r3-cev.atlassian.net/browse/CORDA-2684>`_]
* ``SessionRejectException.UnknownClass`` trapped by flow hospital but no way to call dropSessionInit() [`CORDA-2683 <https://r3-cev.atlassian.net/browse/CORDA-2683>`_]
* Repeated ``CordFormations`` can fail with ClassLoader exception. [`CORDA-2676 <https://r3-cev.atlassian.net/browse/CORDA-2676>`_]
* Backwards compatibility break in serialisation engine when deserialising nullable fields [`CORDA-2674 <https://r3-cev.atlassian.net/browse/CORDA-2674>`_]
* Simplify sample CorDapp projects. [`CORDA-2672 <https://r3-cev.atlassian.net/browse/CORDA-2672>`_]
* Remove ``ExplorerSimulator`` from Node Explorer [`CORDA-2671 <https://r3-cev.atlassian.net/browse/CORDA-2671>`_]
* Reintroduce ``pendingFlowsCount`` to the public API [`CORDA-2669 <https://r3-cev.atlassian.net/browse/CORDA-2669>`_]
* Trader demo integration tests fails with jar not found exception [`CORDA-2668 <https://r3-cev.atlassian.net/browse/CORDA-2668>`_]
* Fix Source ClassLoader for DJVM [`CORDA-2667 <https://r3-cev.atlassian.net/browse/CORDA-2667>`_]
* Issue with simple transfer of ownable asset  [`CORDA-2665 <https://r3-cev.atlassian.net/browse/CORDA-2665>`_]
* Fix references to Docker images in docs [`CORDA-2664 <https://r3-cev.atlassian.net/browse/CORDA-2664>`_]
* Add something to docsite the need for a common contracts Jar between OS/ENT and how it should be compiled against OS [`CORDA-2656 <https://r3-cev.atlassian.net/browse/CORDA-2656>`_]
* Create document outlining CorDapp Upgrade guarantees [`CORDA-2655 <https://r3-cev.atlassian.net/browse/CORDA-2655>`_]
* Fix DJVM CLI tool [`CORDA-2654 <https://r3-cev.atlassian.net/browse/CORDA-2654>`_]
* Corda Service needs Thread Context ClassLoader [`CORDA-2653 <https://r3-cev.atlassian.net/browse/CORDA-2653>`_]
* Useless migration error when finance workflow jar is not installed [`CORDA-2651 <https://r3-cev.atlassian.net/browse/CORDA-2651>`_]
* Database connection pools leaking memory on every checkpoint [`CORDA-2646 <https://r3-cev.atlassian.net/browse/CORDA-2646>`_]
* Exception swallowed when querying vault via RPC with bad page spec [`CORDA-2645 <https://r3-cev.atlassian.net/browse/CORDA-2645>`_]
* Applying CordFormation and Cordapp Gradle plugins together includes Jolokia into the Cordapp. [`CORDA-2642 <https://r3-cev.atlassian.net/browse/CORDA-2642>`_]
* Wrong folder ownership while trying to connect to Testnet using  RC* docker image [`CORDA-2641 <https://r3-cev.atlassian.net/browse/CORDA-2641>`_]
* Provide a better error message on an incompatible implicit contract upgrade [`CORDA-2633 <https://r3-cev.atlassian.net/browse/CORDA-2633>`_]
* ``uploadAttachment`` via shell can fail with unhelpful message if the result of the command is unsuccessful [`CORDA-2632 <https://r3-cev.atlassian.net/browse/CORDA-2632>`_]
* Provide a better error msg when the notary type is misconfigured on the net params [`CORDA-2629 <https://r3-cev.atlassian.net/browse/CORDA-2629>`_]
* Maybe tone down the level of panic when somebody types their SSH password in incorrectly... [`CORDA-2621 <https://r3-cev.atlassian.net/browse/CORDA-2621>`_]
* Cannot complete transaction that has unknown states in the transaction history [`CORDA-2615 <https://r3-cev.atlassian.net/browse/CORDA-2615>`_]
* Switch off the codepaths that disable the FinalityHandler [`CORDA-2613 <https://r3-cev.atlassian.net/browse/CORDA-2613>`_]
* is our API documentation (what is stable and what isn't) correct? [`CORDA-2610 <https://r3-cev.atlassian.net/browse/CORDA-2610>`_]
* Getting set up guide needs to be updated to reflect Java 8 fun and games [`CORDA-2602 <https://r3-cev.atlassian.net/browse/CORDA-2602>`_]
* Not handle exception when Explorer tries to connect to inaccessible server [`CORDA-2586 <https://r3-cev.atlassian.net/browse/CORDA-2586>`_]
* Errors received from peers can't be distinguished from local errors [`CORDA-2572 <https://r3-cev.atlassian.net/browse/CORDA-2572>`_]
* Add `flow kill` command, deprecate `run killFlow` [`CORDA-2569 <https://r3-cev.atlassian.net/browse/CORDA-2569>`_]
* Hash to signature constraints migration: add a config option that makes hash constraints breakable. [`CORDA-2568 <https://r3-cev.atlassian.net/browse/CORDA-2568>`_]
* Deadlock between database and AppendOnlyPersistentMap [`CORDA-2566 <https://r3-cev.atlassian.net/browse/CORDA-2566>`_]
* Docfix: Document custom cordapp configuration [`CORDA-2560 <https://r3-cev.atlassian.net/browse/CORDA-2560>`_]
* Bootstrapper - option to include contracts to whitelist from signed jars [`CORDA-2554 <https://r3-cev.atlassian.net/browse/CORDA-2554>`_]
* Explicit contract upgrade sample fails upon initiation (ClassNotFoundException) [`CORDA-2550 <https://r3-cev.atlassian.net/browse/CORDA-2550>`_]
* IRS demo app missing demodate endpoint [`CORDA-2535 <https://r3-cev.atlassian.net/browse/CORDA-2535>`_]
* Doc fix: Contract testing tutorial errors [`CORDA-2528 <https://r3-cev.atlassian.net/browse/CORDA-2528>`_]
* Unclear error message when receiving state from node on higher version of signed cordapp [`CORDA-2522 <https://r3-cev.atlassian.net/browse/CORDA-2522>`_]
* Terminating ssh connection to node results in stack trace being thrown to the console [`CORDA-2519 <https://r3-cev.atlassian.net/browse/CORDA-2519>`_]
* Error propagating hash to signature constraints [`CORDA-2515 <https://r3-cev.atlassian.net/browse/CORDA-2515>`_]
* Unable to import trusted attachment  [`CORDA-2512 <https://r3-cev.atlassian.net/browse/CORDA-2512>`_]
* Invalid node command line options not always gracefully handled [`CORDA-2506 <https://r3-cev.atlassian.net/browse/CORDA-2506>`_]
* node.conf with rogue line results non-comprehensive error [`CORDA-2505 <https://r3-cev.atlassian.net/browse/CORDA-2505>`_]
* Fix v4's inability to migrate V3 vault data [`CORDA-2487 <https://r3-cev.atlassian.net/browse/CORDA-2487>`_]
* Vault Query fails to process states upon CorDapp Contract upgrade [`CORDA-2486 <https://r3-cev.atlassian.net/browse/CORDA-2486>`_]
* Signature Constraints end-user documentation is limited [`CORDA-2477 <https://r3-cev.atlassian.net/browse/CORDA-2477>`_]
* Docs update: document transition from the whitelist constraint to the sig constraint [`CORDA-2465 <https://r3-cev.atlassian.net/browse/CORDA-2465>`_]
* The ``ContractUpgradeWireTransaction`` does not support the Signature Constraint [`CORDA-2456 <https://r3-cev.atlassian.net/browse/CORDA-2456>`_]
* Intermittent `relation "hibernate_sequence" does not exist` error when using Postgres [`CORDA-2393 <https://r3-cev.atlassian.net/browse/CORDA-2393>`_]
* Implement package namespace ownership [`CORDA-1947 <https://r3-cev.atlassian.net/browse/CORDA-1947>`_]
* Show explicit error message when new version of OS CorDapp contains schema changes [`CORDA-1596 <https://r3-cev.atlassian.net/browse/CORDA-1596>`_]
* Dockerfile improvements and image size reduction [`CORDA-2929 <https://r3-cev.atlassian.net/browse/CORDA-2929>`_]
* Update QPID Proton-J library to latest [`CORDA-2856 <https://r3-cev.atlassian.net/browse/CORDA-2856>`_]
* Not handled excpetion when certificates directory is missing [`CORDA-2786 <https://r3-cev.atlassian.net/browse/CORDA-2786>`_]
* The DJVM cannot sandbox instances of Contract.verify(LedgerTransaction) when testing CorDapps. [`CORDA-2775 <https://r3-cev.atlassian.net/browse/CORDA-2775>`_]
* State machine logs an error prior to deciding to escalate to an error [`CORDA-2757 <https://r3-cev.atlassian.net/browse/CORDA-2757>`_]
* Should Jolokia be included in the built jar files? [`CORDA-2699 <https://r3-cev.atlassian.net/browse/CORDA-2699>`_]
* Transactions created in V3 cannot be verified in V4 if any of the state types were included in "depended upon" CorDapps which were not attached to the transaction [`CORDA-2692 <https://r3-cev.atlassian.net/browse/CORDA-2692>`_]
* Prevent a node re-registering with the doorman if it did already and the node "state" has not been erased [`CORDA-2647 <https://r3-cev.atlassian.net/browse/CORDA-2647>`_]
* The cert hierarchy diagram for C4 is the same as C3.0 but I thought we changed it between C3.1 and 3.2? [`CORDA-2604 <https://r3-cev.atlassian.net/browse/CORDA-2604>`_]
* Windows build fails with `FileSystemException` in `TwoPartyTradeFlowTests` [`CORDA-2363 <https://r3-cev.atlassian.net/browse/CORDA-2363>`_]
* `Cash.generateSpend` cannot be used twice to generate two cash moves in the same tx [`CORDA-2162 <https://r3-cev.atlassian.net/browse/CORDA-2162>`_]
* FlowException thrown by session.receive is not propagated back to a counterparty
* invalid command line args for corda result in 0 exit code
* Windows build fails on TwoPartyTradeFlowTests
* C4 performance below C3, bring it back into parity
* Deserialisation of ContractVerificationException blows up trying to put null into non-null field
* Reference state test (R3T-1918) failing probably due to unconsumed linear state that was referenced.
* Signature constraint: Jarsigner verification allows removal of files from the archive.
* Node explorer bug revealed from within Demobench: serialisation failed error is shown
* Security: Fix vulnerability where an attacker can use CustomSerializers to alter the meaning of serialized data
* Node/RPC is broken after CorDapp upgrade
* RPC client disconnects shouldn't be a warning
* Hibernate logs warning and errors for some conditions we handle

.. _release_notes_v4_0:

Corda 4
=======

Welcome to the Corda 4 release notes. Please read these carefully to understand what's new in this
release and how the changes can help you. Just as prior releases have brought with them commitments
to wire and API stability, Corda 4 comes with those same guarantees. States and apps valid in
Corda 3 are transparently usable in Corda 4.

For app developers, we strongly recommend reading ":doc:`app-upgrade-notes`". This covers the upgrade
procedure, along with how you can adjust your app to opt-in to new features making your app more secure and
easier to upgrade in future.

For node operators, we recommend reading ":doc:`node-upgrade-notes`". The upgrade procedure is simple but
it can't hurt to read the instructions anyway.

Additionally, be aware that the data model improvements are changes to the Corda consensus rules. To use
apps that benefit from them, *all* nodes in a compatibility zone must be upgraded and the zone must be
enforcing that upgrade. This may take time in large zones like the testnet. Please take this into
account for your own schedule planning.

.. warning:: There is a bug in Corda 3.3 that causes problems when receiving a ``FungibleState`` created
   by Corda 4. There will shortly be a followup Corda 3.4 release that corrects this error. Interop between
   Corda 3 and Corda 4 will require that Corda 3 users are on the latest patchlevel release.

Changes for developers in Corda 4
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Reference states
++++++++++++++++

With Corda 4 we are introducing the concept of "reference input states". These allow smart contracts
to reference data from the ledger in a transaction without simultaneously updating it. They're useful
not only for any kind of reference data such as rates, healthcare codes, geographical information etc,
but for anywhere you might have used a SELECT JOIN in a SQL based app.

A reference input state is a ``ContractState`` which can be referred to in a transaction by the contracts
of input and output states but, significantly, whose contract is not executed as part of the transaction
verification process and is not consumed when the transaction is committed to the ledger. Rather, it is checked
for "current-ness". In other words, the contract logic isn't run for the referencing transaction only.
Since they're normal states, if they do occur in the input or output positions, they can evolve on the ledger,
modeling reference data in the real world.

Signature constraints
+++++++++++++++++++++

CorDapps built by the ``corda-gradle-plugins`` are now signed and sealed JAR files by default. This
signing can be configured or disabled with the default certificate being the Corda development certificate.

When an app is signed, that automatically activates the use of signature constraints, which are an
important part of the Corda security and upgrade plan. They allow states to express what contract logic
governs them socially, as in "any contract JAR signed by a threshold of these N keys is suitable",
rather than just by hash or via zone whitelist rules, as in previous releases.

**We strongly recommend all apps be signed and use signature constraints going forward.**

Learn more about this new feature by reading the :doc:`app-upgrade-notes`.

State pointers
++++++++++++++

:ref:`state_pointers` formalize a recommended design pattern, in which states may refer to other states
on the ledger by ``StateRef`` (a pair of transaction hash and output index that is sufficient to locate
any information on the global ledger). State pointers work together with the reference states feature
to make it easy for data to point to the latest version of any other piece of data, with the right
version being automatically incorporated into transactions for you.

New network builder tool
++++++++++++++++++++++++

A new graphical tool for building test Corda networks has been added. It can build Docker images for local
deployment and can also remotely control Microsoft Azure, to create a test network in the cloud.

Learn more on the :doc:`network-builder` page.

.. image:: _static/images/network-builder-v4.png

JPA access in flows and services
++++++++++++++++++++++++++++++++

Corda 3 provides the ``jdbcConnection`` API on ``FlowLogic`` to give access to an active connection to your
underlying database. It is fully intended that apps can store their own data in their own tables in the
node database, so app-specific tables can be updated atomically with the ledger data itself. But JDBC is
not always convenient, so in Corda 4 we are additionally exposing the *Java Persistence Architecture*, for
object-relational mapping. The new ``ServiceHub.withEntityManager`` API lets you load and persist entity
beans inside your flows and services.

Please do write apps that read and write directly to tables running alongside the node's own tables. Using
SQL is a convenient and robust design pattern for accessing data on or off the ledger.

.. important:: Please do not attempt to write to tables starting with ``node_`` or ``contract_`` as those
   are maintained by the node. Additionally, the ``node_`` tables are private to Corda and should not be
   directly accessed at all. Tables starting with ``contract_`` are generated by apps and are designed to
   be queried by end users, GUIs, tools etc.

Security upgrades
+++++++++++++++++

**Sealing.** Sealed JARs are a security upgrade that ensures JARs cannot define classes in each other's packages,
thus ensuring Java's package-private visibility feature works. The Gradle plugins now seal your JARs
by default.

**BelongsToContract annotation.** CorDapps are currently expected to verify that the right contract
is named in each state object. This manual step is easy to miss, which would make the app less secure
in a network where you trade with potentially malicious counterparties. The platform now handles this
for you by allowing you to annotate states with which contract governs them. If states are inner
classes of a contract class, this association is automatic. See :doc:`api-contract-constraints` for more information.

**Two-sided FinalityFlow and SwapIdentitiesFlow.** The previous ``FinalityFlow`` API was insecure because
nodes would accept any finalised transaction, outside of the context of a containing flow. This would
allow transactions to be sent to a node bypassing things like business network membership checks. The
same applies for the ``SwapIdentitiesFlow`` in the confidential-identities module. A new API has been
introduced to allow secure use of this flow.

**Package namespace ownership.** Corda 4 allows app developers to register their keys and Java package namespaces
with the zone operator. Any JAR that defines classes in these namespaces will have to be signed by those keys.
This is an opt-in feature designed to eliminate potential confusion that could arise if a malicious
developer created classes in other people's package namespaces (e.g. an attacker creating a state class
called ``com.megacorp.exampleapp.ExampleState``). Whilst Corda's attachments feature would stop the
core ledger getting confused by this, tools and formats that connect to the node may not be designed to consider
attachment hashes or signing keys, and rely more heavily on type names instead. Package namespace ownership
allows tool developers to assume that if a class name appears to be owned by an organisation, then the
semantics of that class actually *were* defined by that organisation, thus eliminating edge cases that
might otherwise cause confusion.


Network parameters in transactions
++++++++++++++++++++++++++++++++++

Transactions created under a Corda 4+ node will have the currently valid signed ``NetworkParameters``
file attached to each transaction. This will allow future introspection of states to ascertain what was
the accepted global state of the network at the time they were notarised. Additionally, new signatures must
be working with the current globally accepted parameters. The notary signing a transaction will check that
it does indeed reference the current in-force network parameters, meaning that old (and superseded) network
parameters can not be used to create new transactions.

RPC upgrades
++++++++++++

**AMQP/1.0** is now default serialization framework across all of Corda (checkpointing aside), swapping the RPC
framework from using the older Kryo implementation. This means Corda open source and Enterprise editions are
now RPC wire compatible and either client library can be used. We previously started using AMQP/1.0 for the
peer to peer protocol in Corda 3.

**Class synthesis.** The RPC framework supports the "class carpenter" feature. Clients can now freely
download and deserialise objects, such as contract states, for which the defining class files are absent
from their classpath. Definitions for these classes will be synthesised on the fly from the binary schemas
embedded in the messages. The resulting dynamically created objects can then be fed into any framework that
uses reflection, such as XML formatters, JSON libraries, GUI construction toolkits, scripting engines and so on.
This approach is how the :doc:`blob-inspector` tool works - it simply deserialises a message and then feeds
the resulting synthetic class graph into a JSON or YAML serialisation framework.

Class synthesis will use interfaces that are implemented by the original objects if they are found on the
classpath. This is designed to enable generic programming. For example, if your industry has standardised
a thin Java API with interfaces that expose JavaBean style properties (get/is methods), then you can have
that JAR on the classpath of your tool and cast the deserialised objects to those interfaces. In this way
you can work with objects from apps you aren't aware of.

**SSL**. The Corda RPC infrastructure can now be configured to utilise SSL for additional security. The
operator of a node wishing to enable this must of course generate and distribute a certificate in
order for client applications to successfully connect. This is documented here :doc:`tutorial-clientrpc-api`

Preview of the deterministic DJVM
+++++++++++++++++++++++++++++++++

It is important that all nodes that process a transaction always agree on whether it is valid or not.
Because transaction types are defined using JVM byte code, this means that the execution of that byte
code must be fully deterministic. Out of the box a standard JVM is not fully deterministic, thus we must
make some modifications in order to satisfy our requirements.

This version of Corda introduces a standalone :doc:`key-concepts-djvm`. It isn't yet integrated with
the rest of the platform. It will eventually become a part of the node and enforce deterministic and
secure execution of smart contract code, which is mobile and may propagate around the network without
human intervention.

Currently, it is released as an evaluation version. We want to give developers the ability to start
trying it out and get used to developing deterministic code under the set of constraints that we
envision will be placed on contract code in the future. There are some instructions on
how to get started with the DJVM command-line tool, which allows you to run code in a deterministic
sandbox and inspect the byte code transformations that the DJVM applies to your code. Read more in
":doc:`key-concepts-djvm`".

Configurable flow responders
++++++++++++++++++++++++++++

In Corda 4 it is possible for flows in one app to subclass and take over flows from another. This allows you to create generic, shared
flow logic that individual users can customise at pre-agreed points (protected methods). For example, a site-specific app could be developed
that causes transaction details to be converted to a PDF and sent to a particular printer. This would be an inappropriate feature to put
into shared business logic, but it makes perfect sense to put into a user-specific app they developed themselves.

If your flows could benefit from being extended in this way, read ":doc:`flow-overriding`" to learn more.

Target/minimum versions
+++++++++++++++++++++++

Applications can now specify a **target version** in their JAR manifest. The target version declares
which version of the platform the app was tested against. By incrementing the target version, app developers
can opt in to desirable changes that might otherwise not be entirely backwards compatible. For example
in a future release when the deterministic JVM is integrated and enabled, apps will need to opt in to
determinism by setting the target version to a high enough value.

Target versioning has a proven track record in both iOS and Android of enabling platforms to preserve
strong backwards compatibility, whilst also moving forward with new features and bug fixes. We recommend
that maintained applications always try and target the latest version of the platform. Setting a target
version does not imply your app *requires* a node of that version, merely that it's been tested against
that version and can handle any opt-in changes.

Applications may also specify a **minimum platform version**. If you try to install an app in a node that
is too old to satisfy this requirement, the app won't be loaded. App developers can set their min platform
version requirement if they start using new features and APIs.

Dependency upgrades
+++++++++++++++++++

We've raised the minimum JDK to |java_version|, needed to get fixes for certain ZIP compression bugs.

We've upgraded to Kotlin |kotlin_version| so your apps can now benefit from the new features in this language release.

We've upgraded to Gradle 4.10.1.

Changes for administrators in Corda 4
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Official Docker images
++++++++++++++++++++++

Corda 4 adds an :doc:`docker-image` for starting the node. It's based on Ubuntu and uses the Azul Zulu
spin of Java 8. Other tools will have Docker images in future as well.

Auto-acceptance for network parameters updates
++++++++++++++++++++++++++++++++++++++++++++++

Changes to the parameters of a compatibility zone require all nodes to opt in before a flag day.

Some changes are trivial and very unlikely to trigger any disagreement. We have added auto-acceptance
for a subset of network parameters, negating the need for a node operator to manually run an accept
command on every parameter update. This behaviour can be turned off via the node configuration.
See :doc:`network-map`.

Automatic error codes
+++++++++++++++++++++

Errors generated in Corda are now hashed to produce a unique error code that can be
used to perform a lookup into a knowledge base. The lookup URL will be printed to the logs when an error
occur. Here's an example:

.. code-block:: none

    [ERROR] 2018-12-19T17:18:39,199Z [main] internal.NodeStartupLogging.invoke - Exception during node startup: The name 'O=Wawrzek Test C4, L=London, C=GB' for identity doesn't match what's in the key store: O=Wawrzek Test C4, L=Ely, C=GB [errorCode=wuxa6f, moreInformationAt=https://errors.corda.net/OS/4.0/wuxa6f]

The hope is that common error conditions can quickly be resolved and opaque errors explained in a more
user friendly format to facilitate faster debugging and trouble shooting.

At the moment, Stack Overflow is that knowledge base, with the error codes being converted
to a URL that redirects either directly to the answer or to an appropriate search on Stack Overflow.

Standardisation of command line argument handling
+++++++++++++++++++++++++++++++++++++++++++++++++

In Corda 4 we have ported the node and all our tools to use a new command line handling framework. Advantages for you:

* Improved, coloured help output.
* Common options have been standardised to use the same name and behaviour everywhere.
* All programs can now generate bash/zsh auto completion files.

You can learn more by reading our :doc:`CLI user experience guidelines <cli-ux-guidelines>` document.

Liquibase for database schema upgrades
++++++++++++++++++++++++++++++++++++++

We have open sourced the Liquibase schema upgrade feature from Corda Enterprise. The node now uses Liquibase to
bootstrap and update itself automatically. This is a transparent change with pre Corda 4 nodes seamlessly
upgrading to operate as if they'd been bootstrapped in this way. This also applies to the finance CorDapp module.

.. important:: If you're upgrading a node from Corda 3 to Corda 4 and there is old data in the vault, this upgrade may take some time, depending on the number of unconsumed states in the vault.

Ability to pre-validate configuration files
+++++++++++++++++++++++++++++++++++++++++++

A new command has been added that lets you verify a config file is valid without starting up the rest of the node::

    java -jar corda-4.0.jar validate-configuration

Flow control for notaries
+++++++++++++++++++++++++

Notary clusters can now exert backpressure on clients, to stop them from being overloaded. Nodes will be ordered
to back off if a notary is getting too busy, and app flows will pause to give time for the load spike to pass.
This change is transparent to both developers and administrators.

Retirement of non-elliptic Diffie-Hellman for TLS
+++++++++++++++++++++++++++++++++++++++++++++++++

The TLS_DHE_RSA_WITH_AES_128_GCM_SHA256 family of ciphers is retired from the list of allowed ciphers for TLS
as it is a legacy cipher family not supported by all native SSL/TLS implementations. We anticipate that this
will have no impact on any deployed configurations.

Miscellaneous changes
~~~~~~~~~~~~~~~~~~~~~

To learn more about smaller changes, please read the :doc:`changelog`.

Finally, we have added some new jokes. Thank you and good night!
