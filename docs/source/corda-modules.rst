Corda modules
=============

Corda artifacts can be required from Java 9 Jigsaw modules.
From within a ``module-info.java``, you can reference one of the modules e.g., ``requires net.corda.core;``.

.. warning:: while Corda artifacts can be required from ``module-info.java`` files, they are still not proper Jigsaw modules,
because they rely on the automatic module mechanism and declare no module descriptors themselves. We plan to integrate Jigsaw more thoroughly in the future.

Corda stable modules
--------------------

The following modules have a stable API we commit not to break in following releases, unless an incompatible change is required for security reasons:

* **net.corda.core**: core Corda libraries such as crypto functions, types for Corda's building blocks: states, contracts, transactions, attachments, etc. and some interfaces for nodes and protocols
* **net.corda.client.rpc**: client RPC
* **net.corda.client.jackson**: JSON support for client applications

Corda incubating modules
------------------------

The following modules don't yet have a completely stable API, but we will do our best to minimise disruption to
developers using them until we are able to graduate them into the public API:

* **net.corda.node.driver**: test utilities to run nodes programmatically
* **net.corda.confidential.identities**: experimental support for confidential identities on the ledger
* **net.corda.node.test.utils**: generic test utilities
* **net.corda.client.jfx**: support for Java FX UI
* **net.corda.client.mock**: client mock utilities
* **net.corda.sandbox-creator**: sandbox utilities
* **net.corda.quasar.hook**: agent to hook into Quasar and provide types exclusion lists

Corda unstable modules
----------------------

The following modules are available but we do not commit to their stability or continuation in any sense:

* **net.corda.buildSrc**: necessary gradle plugins to build Corda
* **net.corda.finance**: a range of elementary contracts (and associated schemas) and protocols, such as abstract fungible assets, cash, obligation and commercial paper
* **net.corda.node**: core code of the Corda node (eg: node driver, node services, messaging, persistence)
* **net.corda.node.api**: data structures shared between the node and the client module, e.g. types sent via RPC
* **net.corda.samples.network.visualiser**: a network visualiser that uses a simulation to visualise the interaction and messages between nodes on the Corda network
* **net.corda.samples.demos.attachment**: demonstrates sending a transaction with an attachment from one to node to another, and the receiving node accessing the attachment
* **net.corda.samples.demos.bankofcorda**: simulates the role of an asset issuing authority (eg. central bank for cash)
* **net.corda.samples.demos.irs**: demonstrates an Interest Rate Swap agreement between two banks
* **net.corda.samples.demos.notary**: a simple demonstration of a node getting multiple transactions notarised by a distributed (Raft or BFT SMaRt) notary
* **net.corda.samples.demos.simmvaluation**: See our [main documentation site](https://docs.corda.net/initial-margin-agreement.html) regarding the SIMM valuation and agreement on a distributed ledger
* **net.corda.samples.demos.trader**: demonstrates four nodes, a notary, an issuer of cash (Bank of Corda), and two parties trading with each other, exchanging cash for a commercial paper
* **net.corda.node.smoke.test.utils**: test utilities for smoke testing
* **net.corda.node.test.common**: common test functionality
* **net.corda.tools.demobench**: a GUI tool that allows to run Corda nodes locally for demonstrations
* **net.corda.tools.explorer**: a GUI front-end for Corda
* **net.corda.tools.graphs**: utilities to infer project dependencies
* **net.corda.tools.loadtest**: Corda load tests
* **net.corda.verifier**: allows out-of-node transaction verification, allowing verification to scale horizontally
* **net.corda.webserver**: is a servlet container for CorDapps that export HTTP endpoints. This server is an RPC client of the node