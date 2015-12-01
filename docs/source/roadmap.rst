Roadmap
=======

The canonical place to learn about pending tasks is the `R3 JIRA <https://r3-cev.atlassian.net/>`_ site. This
page gives some examples of tasks that we wish to explore in future milestones as part of proving (or disproving)
our core thesis

Data distribution and management:

* Introduce a pluggable network messaging backend with a mock implementation for testing, and an Apache Kafka based
  implementation for bringing up first networking capability. Using Kafka as a message routing/storage layer is not
  necessarily the final approach or suitable for P2P WAN messaging, but it should be a good next step for prototyping
  and may even be a useful for internal deployments.
* Flesh out the core code enough to have a server that downloads and verifies transactions as they are uploaded to the
  cluster. At this stage all transactions are assumed to be public to the network (this will change later). Some basic
  logging/JMX/monitoring dashboard should be present to see what the node is doing.
* Experimentation with block-free conflict/double spend resolution using a voting pool of *observers* with lazy consensus.
  Logic for rolling back losing transaction subgraphs when a conflict is resolved, reporting these events to observer
  APIs and so on.
* Support a pluggable storage layer for recording seen transactions and their validity states.

Contracts API:

* Upgrades to the composability of contracts: demonstrate how states can require the presence of other states as a way
  to mix in things like multi-signature requirements.
* Demonstrate how states can be separated into two parts, the minimum necessary for conflict resolution (e.g. owner keys)
  and a separated part that contains data useful for auditing and building confidence in the validity of a transaction
  (e.g. amounts).
* Explorations of improved time handling, and how best to express temporal logic in the contract API/DSL.

JVM adaptations:

* Implement a sandbox and packaging system for contract logic. Contracts should be distributable through the network
  layer.
* Experiment with modifications to HotSpot to allow for safely killing threads (i.e. fixing the issues that make
  Thread.stop() unsafe to use), and to measure and enforce runtime limits to handle runaway code.

