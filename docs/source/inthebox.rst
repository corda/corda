What's included?
================

This Corda early access preview includes:

* A collection of samples, for instance a web app demo that uses it to implement IRS trading.
* A template app you can use to get started, and tutorial app that teaches you the basics.
* A peer to peer network with message persistence and delivery retries.
* Key data structures for defining contracts and states.
* Smart contracts, which you can find in the :doc:`contract-catalogue`.
* API documentation and tutorials (what you're reading).
* A business process workflow framework.
* Notary infrastructure for precise timestamping, and elimination of double spending without a blockchain.
* A simple RPC API.
* A user interface for administration.

Some things it does not currently include but should gain later are:

* Sandboxing, distribution and publication of smart contract code.
* A well specified wire protocol.
* An identity framework.

The open source version of Corda is designed for developers exploring how to write apps. It is not intended to
be production grade software. For example it uses an embedded SQL database and doesn't yet have connectivity
support for mainstream SQL vendors (Oracle, Postgres, MySQL, SQL Server etc). It hasn't been security audited
and the APIs change in every release.

Source tree layout
------------------

The Corda repository comprises the following folders:

* **buildSrc** contains necessary gradle plugins to build Corda.
* **client** contains libraries for connecting to a node, working with it remotely and binding server-side data to JavaFX UI.
* **config** contains logging configurations and the default node configuration file.
* **core** containing the core Corda libraries such as crypto functions, types for Corda's building blocks: states,
  contracts, transactions, attachments, etc. and some interfaces for nodes and protocols.
* **docs** contains the Corda docsite in restructured text format as well as the built docs in html. The docs can be
  accessed via ``/docs/index.html`` from the root of the repo.
* **finance** defines a range of elementary contracts (and associated schemas) and protocols, such as abstract fungible
  assets, cash, obligation and commercial paper.
* **gradle** contains the gradle wrapper which you'll use to execute gradle commands.
* **gradle-plugins** contains some additional plugins which we use to deploy Corda nodes.
* **lib** contains some dependencies.
* **node** contains the core code of the Corda node (eg: node driver, node services, messaging, persistence).
* **node-api** contains data structures shared between the node and the client module, e.g. types sent via RPC.
* **node-schemas** contains entity classes used to represent relational database tables.
* **samples** contains all our Corda demos and code samples.
* **test-utils** contains some utilities for unit testing contracts ( the contracts testing DSL) and protocols (the
  mock network) implementation.
* **tools** contains the explorer which is a GUI front-end for Corda, and also the DemoBench which is a GUI tool that allows you to run Corda nodes locally for demonstrations.
* **verifier** allows out-of-node transaction verification, allowing verification to scale horizontally.
* **webserver** is a servlet container for CorDapps that export HTTP endpoints. This server is an RPC client of the node.
