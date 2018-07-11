Corda repo layout
=================

The Corda repository comprises the following folders:

* **buildSrc** contains necessary gradle plugins to build Corda
* **client** contains libraries for connecting to a node, working with it remotely and binding server-side data to
  JavaFX UI
* **confidential-identities** contains experimental support for confidential identities on the ledger
* **config** contains logging configurations and the default node configuration file
* **core** containing the core Corda libraries such as crypto functions, types for Corda's building blocks: states,
  contracts, transactions, attachments, etc. and some interfaces for nodes and protocols
* **docs** contains the Corda docsite in restructured text format
* **experimental** contains platform improvements that are still in the experimental stage
* **finance** defines a range of elementary contracts (and associated schemas) and protocols, such as abstract fungible
  assets, cash, obligation and commercial paper
* **gradle** contains the gradle wrapper which you'll use to execute gradle commands
* **lib** contains some dependencies
* **node** contains the core code of the Corda node (eg: node driver, node services, messaging, persistence)
* **node-api** contains data structures shared between the node and the client module, e.g. types sent via RPC
* **samples** contains all our Corda demos and code samples
* **testing** contains some utilities for unit testing contracts (the contracts testing DSL) and flows (the
  mock network) implementation
* **tools** contains the explorer which is a GUI front-end for Corda, and also the DemoBench which is a GUI tool that
  allows you to run Corda nodes locally for demonstrations
* **webserver** is a servlet container for CorDapps that export HTTP endpoints. This server is an RPC client of the node