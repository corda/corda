Release notes
=============

Here are release notes for each snapshot release from M9 onwards. This includes guidance on how to upgrade code from
the previous milestone release.

Unreleased
----------

Work has continued on confidential identities, introducing code to enable the Java standard libraries to work with
composite key signatures. This will form the underlying basis of future work to standardise the public key and signature
formats to enable interoperability with other systems, as well as enabling the use of composite signatures on X.509
certificates to prove association between transaction keys and identity keys.

We have updated DemoBench so that it is installed as "Corda DemoBench" for both Windows and MacOSX. The original version was installed as just "DemoBench", and so will not be overwritten automatically by the new version.

Milestone 10
------------

Special thank you to `Qian Hong <https://github.com/fracting>`_, `Marek Skocovsky <https://github.com/marekdapps>`_, `Karel Hajek <https://github.com/polybioz>`_, and `Jonny Chiu <https://github.com/johnnyychiu>`_ for their contributions to Corda in M10.

A new interactive **Corda Shell** has been added to the node. The shell lets developers and node administrators
easily command the node by running flows, RPCs and SQL queries. It also provides a variety of commands to monitor
the node. The Corda Shell is based on the popular `CRaSH project <http://www.crashub.org/>`_ and new commands can
be easily added to the node by simply dropping Groovy or Java files into the node's ``shell-commands`` directory.
We have many enhancements planned over time including SSH access, more commands and better tab completion.

The new "DemoBench" makes it easy to configure and launch local Corda nodes. It is a standalone desktop app that can be bundled with its own JRE and packaged as either EXE (Windows), DMG (MacOS) or RPM (Linux-based). It has the following features:
 #. New nodes can be added at the click of a button. Clicking "Add node" creates a new tab that lets you edit the most important configuration properties of the node before launch, such as its legal name and which CorDapps will be loaded.
 #. Each tab contains a terminal emulator, attached to the pseudoterminal of the node. This lets you see console output.
 #. You can launch an Corda Explorer instance for each node at the click of a button. Credentials are handed to the Corda Explorer so it starts out logged in already.
 #. Some basic statistics are shown about each node, informed via the RPC connection.
 #. Another button launches a database viewer in the system browser.
 #. The configurations of all running nodes can be saved into a single ``.profile`` file that can be reloaded later.

Soft Locking is a new feature implemented in the vault to prevent a node constructing transactions that attempt to use the same input(s) simultaneously.
Such transactions would result in naturally wasted effort when the notary rejects them as double spend attempts.
Soft locks are automatically applied to coin selection (eg. cash spending) to ensure that no two transactions attempt to spend the same fungible states.

The basic Amount API has been upgraded to have support for advanced financial use cases and to better integrate with currency reference data.

We have added optional out-of-process transaction verification. Any number of external verifier processes may be attached to the node which can handle loadbalanced verification requests.

We have also delivered the long waited Kotlin 1.1 upgrade in M10! The new features in Kotlin allow us to write even more clean and easy to manage code, which greatly increases our productivity.

This release contains a large number of improvements, new features, library upgrades and bug fixes. For a full list of changes please see :doc:`changelog`.

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
