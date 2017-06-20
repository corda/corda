Release notes
=============

Here are release notes for each snapshot release from M9 onwards.

Unreleased
----------

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
