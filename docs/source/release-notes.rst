Release notes
=============

.. _release_notes_v3_3:

Release 3.4
-----------

In response to the recently released [`Corda 4.0 <https://docs.corda.net/releases/release-V4.0/release-notes.html>`_] Corda 3.4 brings a
number of fixes that address several interoperability issues we've discovered between nodes. These should make the deployment of mixed
version networks far smoother and the upgrade path from 3 to 4 easier.

The majority of these fixes surround the serialization and class synthesis frameworks where receiving states and messages from newer (Version 4) nodes highlighted
a few edge cases, bugs, and performance enhancements we could make.

Issues Fixed
~~~~~~~~~~~~

* Don't fail deserialization with carpentry errors if the carpented types would be discarded through evolution [`CORDA-2704 <https://r3-cev.atlassian.net/browse/CORDA-2704>`_]
* RPC Vault query incompatibility between Corda 3.3 and Corda 4 [`CORDA-2687 <https://r3-cev.atlassian.net/browse/CORDA-2687>`_]
* V3 node unable to record finalised transaction containing a V3 FungibleAsset state created by a V4 node [`CORDA-2422 <https://r3-cev.atlassian.net/browse/CORDA-2422>`_]
* V3 node is unable to evolve serialised type that has introduced a property that is of an unknown type [`CORDA-2314 <https://r3-cev.atlassian.net/browse/CORDA-2314>`_]
* ClassCastException during initiate Contract Upgrade [`CORDA-2109 <https://r3-cev.atlassian.net/browse/CORDA-2109>`_]

.. _release_notes_v3_3:

Release 3.3
-----------

Corda 3.3 brings together many small improvements, fixes, and community contributions to deliver a stable and polished release
of Corda. Where both the 3.1 and 3.2 releases delivered a smaller number of critical bug fixes addressing immediate and impactful error conditions, 3.3
addresses a much greater number of issues, both small and large, that have been found and fixed since the release of 3.0 back in March. Rolling up a great
many improvements and polish to truly make the Corda experience just that much better.

In addition to work undertaken by the main Corda development team, we've taken the opportunity in 3.3 to bring back many of the contributions made
by community members from master onto the currently released stable branch. It has been said many times before, but the community and its members
are the real life-blood of Corda and anyone who takes the time to contribute is a star in our eyes. Bringing that code into the current version we hope
gives people the opportunity to see their work in action, and to help their fellow community members by having these contributions available in a
supported release.

Changes of Note
~~~~~~~~~~~~~~~

* **Serialization fixes**

  Things "in the lab" always work so much better than they do in the wild, where everything you didn't think of is thrown at your code and a mockery
  is made of some dearly held assumptions.  A great example of this is the serialization framework which delivers Corda's wire stability guarantee
  that was introduced in 3.0 and has subsequently been put to a rigorous test by our users. Corda 3.3 consolidates a great many fixes in that framework,
  both programmatically in terms of fixing bugs, but also in the documentation, hopefully making things clearer and easier to work with.

* **Certificate Hierarchy**

  After consultation, collaboration, and discussion with industry experts, we have decided to alter the default Certificate Hierarchy (PKI) utilized by
  Corda and the Corda Network. To facilitate this, the nodes have had their certificate path verification logic made much more flexible. All existing
  certificate hierarchy, certificates, and networks will remain valid. The possibility now exists for nodes to recognize a deeper certificate chain and
  thus Compatibility Zone operators can deploy and adhere to the PKI standards they expect and are comfortable with.

  Practically speaking, the old code assumed a 3-level hierarchy of Root -> Intermediate CA (Doorman) -> Node, and this was hard coded. From 3.3 onward an
  arbitrary depth of certificate chain is supported. For the Corda Network, this means the introduction of an intermediate layer between the root and the
  signing certificates (Network Map and Doorman). This has the effect of allowing the root certificate to *always* be kept offline and never retrieved or
  used. Those new intermediate certificates can be used to generate, if ever needed, new signing certs without risking compromise of the root key.

Special Thanks
~~~~~~~~~~~~~~

The Corda community is a vibrant and exciting ecosystem that spreads far outside the virtual walls of the
R3 organisation. Without that community, and the most welcome contributions of its members, the Corda project
would be a much poorer place.

We're therefore happy to extend thanks to the following members of that community for their contributions

  * `Dan Newton <https://github.com/lankydan>`_ for a fix to cleanup node registration in the test framework. The changes can be found `here <https://github.com/corda/corda/commit/599aa709dd025a56e2c295cc9225ba2ee5b0fc9c>`_.
  * `Tushar Singh Bora <https://github.com/kid101>`_ for a number of `documentation tweaks <https://github.com/corda/corda/commit/279b8deaa6e1045fa4890ef179ee9a41c8a6406b>`_. In addition, some updates to the tutorial documentation `here <https://github.com/corda/corda/commit/37656a58f5fd6cad7a2fa1c08e887777b375cedd>`_.
  * `Jiachuan Li <https://github.com/lijiachuan1982>`_ for a number of corrections to our documentation. Those contributions can be found `here <https://github.com/corda/corda/commit/83a09885172f22ad4e03909d942b473bccb4e228>`_ and `here <https://github.com/corda/corda/commit/f23f2ee6966cf46a3f8b598e868393f9f2e610e7>`_.
  * `Yogesh <https://github.com/acetheultimate>`_ for a documentation tweak that can be see `here <https://github.com/corda/corda/commit/07e3ff502f620d5201a29cf12f686b50cd1cb17c>`_.
  * `Roman Plášil <https://github.com/Quiark>`_ for speeding up node shutdown when connecting to an http network map. This fix can be found `here <https://github.com/corda/corda/commit/ec1e40109d85d495b84cf4307fb8a7e34536f1d9>`_.
  * `renlulu <https://github.com/renlulu>`_ for a small `PR <https://github.com/corda/corda/commit/cda7c292437e228bd8df5c800f711d45a3d743e1>`_ to optimize some of the imports.
  * `cxyzhang0 <https://github.com/cxyzhang0>`_ for making the ``IdentitySyncFlow`` more useful. See `here <https://github.com/corda/corda/commit/a86c79e40ca15a8b95380608be81fe338d82b141>`_.
  * `Venelin Stoykov <https://github.com/vstoykov>`_ with updates to the `documentation <https://github.com/corda/corda/commit/4def8395b3bd100b2b0a3d2eecef5e20f0ec7f47>`_ around the progress tracker.
  * `Mohamed Amine Legheraba <https://github.com/MohamedLEGH>`_ for updates to the Azure documentation that can be seen `here <https://github.com/corda/corda/commit/14e9bf100d0b0236f65ee4ffd778f32307b9e519>`_.
  * `Stanly Johnson <https://github.com/stanly-johnson>`_ with a `fix <https://github.com/corda/corda/commit/f9a9bb19a7cc6d202446890e4e11bebd4a118cf3>`_ to the network bootstrapper.
  * `Tittu Varghese <https://github.com/tittuvarghese>`_ for adding a favicon to the docsite. This commit can be found `here <https://github.com/corda/corda/commit/cd8988865599261db45505060735880c3066792e>`_

Issues Fixed
~~~~~~~~~~~~

* Refactoring ``DigitalSignatureWithCertPath`` for more performant storing of the certificate chain. [`CORDA-1995 <https://r3-cev.atlassian.net/browse/CORDA-1995>`_]
* The serializers class carpenter fails when superclass has double-size primitive field. [`Corda-1945 <https://r3-cev.atlassian.net/browse/Corda-1945>`_]
* If a second identity is mistakenly created the node will not start. [`CORDA-1811 <https://r3-cev.atlassian.net/browse/CORDA-1811>`_]
* Demobench profile load fails with stack dump. [`CORDA-1948 <https://r3-cev.atlassian.net/browse/CORDA-1948>`_]
* Deletes of NodeInfo can fail to propagate leading to infinite retries. [`CORDA-2029 <https://r3-cev.atlassian.net/browse/CORDA-2029>`_]
* Copy all the certificates from the network-trust-store.jks file to the node's trust store. [`CORDA-2012 <https://r3-cev.atlassian.net/browse/CORDA-2012>`_]
* Add SNI (Server Name Indication) header to TLS connections. [`CORDA-2001 <https://r3-cev.atlassian.net/browse/CORDA-2001>`_]
* Fix duplicate index declaration in the Cash schema. [`CORDA-1952 <https://r3-cev.atlassian.net/browse/CORDA-1952>`_]
* Hello World Tutorial Page mismatch between code sample and explanatory text. [`CORDA-1950 <https://r3-cev.atlassian.net/browse/CORDA-1950>`_]
* Java Instructions to Invoke Hello World CorDapp are incorrect. [`CORDA-1949 <https://r3-cev.atlassian.net/browse/CORDA-1949>`_]
* Add ``VersionInfo`` to the ``NodeInfo`` submission request to the network map element of the Compatibility Zone. [`CORDA-1938 <https://r3-cev.atlassian.net/browse/CORDA-1938>`_]
* Rename current INTERMEDIATE_CA certificate role to DOORMAN_CA certificate role. [`CORDA-1934 <https://r3-cev.atlassian.net/browse/CORDA-1934>`_]
* Make node-side network map verification agnostic to the certificate hierarchy. [`CORDA-1932 <https://r3-cev.atlassian.net/browse/CORDA-1932>`_]
* Corda Shell incorrectly deserializes generic types as raw types. [`CORDA-1907 <https://r3-cev.atlassian.net/browse/CORDA-1907>`_]
* The Corda web server does not support asynchronous servlets. [`CORDA-1906 <https://r3-cev.atlassian.net/browse/CORDA-1906>`_]
* Amount<T> is deserialized from JSON and YAML as Amount<Currency>, for all values of T. [`CORDA-1905 <https://r3-cev.atlassian.net/browse/CORDA-1905>`_]
* ``NodeVaultService.loadStates`` queries without a ``PageSpecification`` property set. This leads to issues with large transactions. [`CORDA-1895 <https://r3-cev.atlassian.net/browse/CORDA-1895>`_]
* If a node has two flows, where one's name is a longer version of the other's, they cannot be started [`CORDA-1892 <https://r3-cev.atlassian.net/browse/CORDA-1892>`_]
* Vault Queries across ``LinearStates`` and ``FungibleState`` tables return incorrect results. [`CORDA-1888 <https://r3-cev.atlassian.net/browse/CORDA-1888>`_]
* Checking the version of the Corda jar file by executing the jar with the ``--version`` flag without specifying a valid node configuration file causes an exception to be thrown. [`CORDA-1884 <https://r3-cev.atlassian.net/browse/CORDA-1884>`_]
* RPC deadlocks after a node restart. [`CORDA-1875 <https://r3-cev.atlassian.net/browse/CORDA-1875>`_]
* Vault query fails to find a state if it extends some class (``ContractState``) and it is that base class that is used as the predicate (``vaultService.queryBy<I>()``). [`CORDA-1858 <https://r3-cev.atlassian.net/browse/CORDA-1858>`_]
* Missing unconsumed states from linear id when querying vault caused by a the previous transaction failing with an SQL exception. [`CORDA-1847 <https://r3-cev.atlassian.net/browse/CORDA-1847>`_]
* Inconsistency in how a web path is written. [`CORDA-1841 <https://r3-cev.atlassian.net/browse/CORDA-1841>`_]
* Cannot use ``TestIdentities`` with same organization name in ``net.corda.testing.driver.Driver``. [`CORDA-1837 <https://r3-cev.atlassian.net/browse/CORDA-1837>`_]
* Docs page typos. [`CORDA-1834 <https://r3-cev.atlassian.net/browse/CORDA-1834>`_]
* Adding flexibility to the serialization frameworks unit tests support and utility code. [`CORDA-1808 <https://r3-cev.atlassian.net/browse/CORDA-1808>`_]
* Cannot use ``--initial-registration`` with the ``networkServices`` configuration option in place of the older ``compatibilityzone`` option within ``node.conf``. [`CORDA-1789 <https://r3-cev.atlassian.net/browse/CORDA-1789>`_]
* Document more clearly the supported version of both IntelliJ and the IntelliJ Kotlin Plugins. [`CORDA-1727 <https://r3-cev.atlassian.net/browse/CORDA-1727>`_]
* DemoBench's "Launch Explorer" button is not re-enabled when you close Node Explorer. [`CORDA-1686 <https://r3-cev.atlassian.net/browse/CORDA-1686>`_]
* It is not possible to run ``stateMachinesSnapshot`` from the shell. [`CORDA-1681 <https://r3-cev.atlassian.net/browse/CORDA-1681>`_]
* Node won't start if CorDapps generate states prior to deletion [`CORDA-1663 <https://r3-cev.atlassian.net/browse/CORDA-1663>`_]
* Serializer Evolution breaks with Java classes adding nullable properties. [`CORDA-1662 <https://r3-cev.atlassian.net/browse/CORDA-1662>`_]
* Add Java examples for the creation of proxy serializers to complement the existing kotlin ones. [`CORDA-1641 <https://r3-cev.atlassian.net/browse/CORDA-1641>`_]
* Proxy serializer documentation isn't clear on how to write a proxy serializer. [`CORDA-1640 <https://r3-cev.atlassian.net/browse/CORDA-1640>`_]
* Node crashes in ``--initial-registration`` polling mode if doorman returns a transient HTTP error. [`CORDA-1638 <https://r3-cev.atlassian.net/browse/CORDA-1638>`_]
* Nodes started by gradle task are not stopped when the gradle task exits. [`CORDA-1634 <https://r3-cev.atlassian.net/browse/CORDA-1634>`_]
* Notarizations time out if notary doesn't have up-to-date network map. [`CORDA-1628 <https://r3-cev.atlassian.net/browse/CORDA-1628>`_]
* Node explorer: Improve error handling when connection to nodes cannot be established. [`CORDA-1617 <https://r3-cev.atlassian.net/browse/CORDA-1617>`_]
* Validating notary fails to resolve an attachment. [`CORDA-1588 <https://r3-cev.atlassian.net/browse/CORDA-1588>`_]
* Out of process nodes started by the driver do not log to file. [`CORDA-1575 <https://r3-cev.atlassian.net/browse/CORDA-1575>`_]
* Once ``--initial-registration`` has been passed to a node, further restarts should assume that mode until a cert is collected. [`CORDA-1572 <https://r3-cev.atlassian.net/browse/CORDA-1572>`_]
* An array of primitive byte arrays (an array of arrays) won't deserialize in a virgin factory (i.e. one that didn't build the serializer for serialization). [`CORDA-1545 <https://r3-cev.atlassian.net/browse/CORDA-1545>`_]
* Ctrl-C in the shell fails to aborts the flow. [`CORDA-1542 <https://r3-cev.atlassian.net/browse/CORDA-1542>`_]
* One transaction with two identical cash outputs cannot be save in the vault. [`CORDA-1535 <https://r3-cev.atlassian.net/browse/CORDA-1535>`_]
* The unit tests for the enum evolver functionality cannot be regenerated. This is because verification logic added after their initial creation has a bug that incorrectly identifies a cycle in the graph. [`CORDA-1498 <https://r3-cev.atlassian.net/browse/CORDA-1498>`_]
* Add in a safety check that catches flow checkpoints from older versions. [`CORDA-1477 <https://r3-cev.atlassian.net/browse/CORDA-1477>`_]
* Buggy ``CommodityContract`` issuance logic. [`CORDA-1459 <https://r3-cev.atlassian.net/browse/CORDA-1459>`_]
* Error in the process-id deletion process allows multiple instances of the same node to be run. [`CORDA-1455 <https://r3-cev.atlassian.net/browse/CORDA-1455>`_]
* Node crashes if network map returns HTTP 50X error. [`CORDA-1414 <https://r3-cev.atlassian.net/browse/CORDA-1414>`_]
* Delegate Property doesn't serialize, throws an erroneous type mismatch error. [`CORDA-1403 <https://r3-cev.atlassian.net/browse/CORDA-1403>`_]
* If a vault query throws an exception, the stack trace is swallowed. [`CORDA-1397 <https://r3-cev.atlassian.net/browse/CORDA-1397>`_]
* Node can fail to fully start when a port conflict occurs, no useful error message is generated when this occurs. [`CORDA-1394 <https://r3-cev.atlassian.net/browse/CORDA-1394>`_]
* Running the ``deployNodes`` gradle task back to back without a clean doesn't work. [`CORDA-1389 <https://r3-cev.atlassian.net/browse/CORDA-1389>`_]
* Stripping issuer from Amount<Issued<T>> does not preserve ``displayTokenSize``. [`CORDA-1386 <https://r3-cev.atlassian.net/browse/CORDA-1386>`_]
* ``CordaServices`` are instantiated multiple times per Party when using ``NodeDriver``. [`CORDA-1385 <https://r3-cev.atlassian.net/browse/CORDA-1385>`_]
* Out of memory errors can be seen when using Demobench + Explorer. [`CORDA-1356 <https://r3-cev.atlassian.net/browse/CORDA-1356>`_]
* Reduce the amount of classpath scanning during integration tests execution. [`CORDA-1355 <https://r3-cev.atlassian.net/browse/CORDA-1355>`_]
* SIMM demo throws "attachment too big" errors. [`CORDA-1346 <https://r3-cev.atlassian.net/browse/CORDA-1346>`_]
* Fix vault query paging example in ``ScheduledFlowTests``. [`CORDA-1344 <https://r3-cev.atlassian.net/browse/CORDA-1344>`_]
* The shell doesn't print the return value of a started flow. [`CORDA-1342 <https://r3-cev.atlassian.net/browse/CORDA-1342>`_]
* Provide access to database transactions for CorDapp developers. [`CORDA-1341 <https://r3-cev.atlassian.net/browse/CORDA-1341>`_]
* Error with ``VaultQuery`` for entity inheriting from ``CommonSchemaV1.FungibleState``. [`CORDA-1338 <https://r3-cev.atlassian.net/browse/CORDA-1338>`_]
* The ``--network-root-truststore`` command line option not defaulted. [`CORDA-1317 <https://r3-cev.atlassian.net/browse/CORDA-1317>`_]
* Java example in "Upgrading CorDapps" documentation is wrong. [`CORDA-1315 <https://r3-cev.atlassian.net/browse/CORDA-1315>`_]
* Remove references to ``registerInitiatedFlow`` in testing documentation as it is not needed. [`CORDA-1304 <https://r3-cev.atlassian.net/browse/CORDA-1304>`_]
* Regression: Recording a duplicate transaction attempts second insert to vault. [`CORDA-1303 <https://r3-cev.atlassian.net/browse/CORDA-1303>`_]
* Columns in the Corda database schema should have correct NULL/NOT NULL constraints. [`CORDA-1297 <https://r3-cev.atlassian.net/browse/CORDA-1297>`_]
* MockNetwork/Node API needs a way to register ``@CordaService`` objects. [`CORDA-1292 <https://r3-cev.atlassian.net/browse/CORDA-1292>`_]
* Deleting a ``NodeInfo`` from the additional-node-infos directory should remove it from cache. [`CORDA-1093 <https://r3-cev.atlassian.net/browse/CORDA-1093>`_]
* ``FailNodeOnNotMigratedAttachmentContractsTableNameTests`` is sometimes failing with database constraint "Notary" is null. [`CORDA-1976 <https://r3-cev.atlassian.net/browse/CORDA-1976>`_]
* Revert keys for DEV certificates. [`CORDA-1661 <https://r3-cev.atlassian.net/browse/CORDA-1661>`_]
* Node Info file watcher should block and load ``NodeInfo`` when node startup. [`CORDA-1604 <https://r3-cev.atlassian.net/browse/CORDA-1604>`_]
* Improved logging of the network parameters update process. [`CORDA-1405 <https://r3-cev.atlassian.net/browse/CORDA-1405>`_]
* Ensure all conditions in cash selection query are tested. [`CORDA-1266 <https://r3-cev.atlassian.net/browse/CORDA-1266>`_]
* ``NodeVaultService`` bug. Start node, issue cash, stop node, start node, ``getCashBalances()`` will not show any cash
* A Corda node doesn't re-select cluster from HA Notary.
* Event Horizon is not wire compatible with older network parameters objects.
* Notary unable to resolve Party after processing a flow from same Party.
* Misleading error message shown when a node is restarted after a flag day event.

.. _release_notes_v3_2:

Release 3.2
-----------

As we see more Corda deployments in production this minor release of the open source platform brings
several fixes that make it easier for a node to join Corda networks broader than those used when
operating as part of an internal testing deployment. This will ensure Corda nodes will be free to interact
with upcoming network offerings from R3 and others who may make broad-access Corda networks available.

* **The Corda Network Builder**

To make it easier to create more dynamic, flexible, networks for testing and deployment,
with the 3.2 release of Corda we are shipping a graphical network bootsrapping tool (see :doc:`network-builder`)
to facilitate the simple creation of more dynamic ad hoc dev-mode environments.

Using a graphical interface you can dynamically create and alter Corda test networks, adding
nodes and CorDapps with the click of a button! Additionally, you can leverage its integration
with Azure cloud services for remote hosting of Nodes and Docker instances for local testing.

* **Split Compatibility Zone**

Prior to this release Compatibility Zone membership was denoted with a single configuration setting

.. sourcecode:: shell

    compatibilityZoneURL : "http://<host>(:<port>)"

That would indicate both the location of the Doorman service the node should use for registration
of its identity as well as the Network Map service where it would publish its signed Node Info and
retrieve the Network Map.

Compatibility Zones can now, however, be configured with the two disparate services, Doorman and
Network Map, running on different URLs. If the Compatibility Zone your node is connecting to
is configured in this manner, the new configuration looks as follows.

.. sourcecode:: shell

    networkServices {
        doormanURL: "http://<host>(:<port>)"
        networkMapURL: "http://<host>(:<port>)"
    }

.. note:: The ``compatibilityZoneURL`` setting should be considered deprecated in favour of the new
    ``networkServices`` settings group.

* **The Blob Inspector**

The blob inspector brings the ability to unpack serialized Corda blobs at the
command line, giving a human readable interpretation of the encoded date.

.. note:: This tool has been shipped as a separate Jar previously. We are now including it
    as part of an official release.

Documentation on its use can be found here :doc:`blob-inspector`

* **The Event Horizon**

One part of joining a node to a Corda network is agreeing to the rules that govern that network as set out
by the network operator. A node's membership of a network is communicated to other nodes through the network
map, the service to which the node will have published its Node Info, and through which it receives the
set of NodeInfos currently present on the network. Membership of that list is a finite thing determined by
the network operator.

Periodically a node will republish its NodeInfo to the Network Map service. The Network Map uses this as a
heartbeat to determine the status of nodes registered with it. Those that don't "beep" within the
determined interval are removed from the list of registered nodes. The ``Event Horizon`` network parameter
sets the upper limit within which a node must respond or be considered inactive.

.. important:: This does not mean a node is unregistered from the Doorman, only that its NodeInfo is
    removed from the Network Map. Should the node come back online it will be re-added to the published
    set of NodeInfos

Issues Fixed
~~~~~~~~~~~~

* Update Jolokia to a more secure version [`CORDA-1744 <https://r3-cev.atlassian.net/browse/CORDA-1744>`_]
* Add the Blob Inspector [`CORDA-1709 <https://r3-cev.atlassian.net/browse/CORDA-1709>`_]
* Add support for the ``Event Horizon`` Network Parameter [`CORDA-866 <https://r3-cev.atlassian.net/browse/CORDA-866>`_]
* Add the Network Bootstrapper [`CORDA-1717 <https://r3-cev.atlassian.net/browse/CORDA-1717>`_]
* Fixes for the finance CordApp[`CORDA-1711 <https://r3-cev.atlassian.net/browse/CORDA-1711>`_]
* Allow Doorman and NetworkMap to be configured independently [`CORDA-1510 <https://r3-cev.atlassian.net/browse/CORDA-1510>`_]
* Serialization fix for generics when evolving a class [`CORDA-1530  <https://r3-cev.atlassian.net/browse/CORDA-1530>`_]
* Correct typo in an internal database table name [`CORDA-1499 <https://r3-cev.atlassian.net/browse/CORDA-1499>`_] and [`CORDA-1804 <https://r3-cev.atlassian.net/browse/CORDA-1804>`_]
* Hibernate session not flushed before handing over raw JDBC session to user code [`CORDA-1548 <https://r3-cev.atlassian.net/browse/CORDA-1548>`_]
* Fix Postgres db bloat issue [`CORDA-1812  <https://r3-cev.atlassian.net/browse/CORDA-1812>`_]
* Roll back flow transaction on exception [`CORDA-1790 <https://r3-cev.atlassian.net/browse/CORDA-1790>`_]

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


