Roadmap
=======

The canonical place to learn about pending tasks is the `R3 JIRA <https://r3-cev.atlassian.net/>`_ site. This
page gives some examples of tasks that we wish to explore in future milestones:

M1 is this release.

Milestone 2
-----------

Contracts API:

* Example implementations of more advanced use cases, possibly an interest rate swap.
* Support for lifting transaction sub-graphs out of the global ledger and evolving them privately within a subgroup
  of users (helpful for privacy, scalability).
* An improved unit test DSL.

Platform:

* Storage of states to disk and initial support for network synchronisation (does not have to be the final network
  layer: just something good enough to get us to the next stage of prototyping).
* Dynamic loading and first-pass sandboxing of contract code.
* Simple test/admin user interface for performing various kinds of trades.

