Release notes
=============

Here are release notes for each snapshot release from M9 onwards. This includes guidance on how to upgrade code from
the previous milestone release.

Milestone 9
-----------

This release focuses on improvements to resiliency of the core infrastructure, with highlights including a Byzantine
fault tolerant (BFT) decentralised notary, based on the BFT-SMaRT protocol and isolating the web server from the
Corda node.

With thanks to open source contributor Thomas Schroeter for providing the BFT notary, Corda can now resist malicious
attacks by members of a distributed notary service. If your notary has five members, two can become hacked or malicious
simultaneously and the system continues unaffected!
.. You can read more about this new feature <here>.

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

For a full list of changes please see :doc:`change-log`.