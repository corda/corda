Versioning and upgrades
=======================

| *Change is inevitable, except from a vending machine*
| -- Abraham Lincoln
|

This section of the guide covers how CorDapps are versioned and how to manage upgrades in a decentralised network. It should be read when
you're at a stage of your development that requires planning for post-launch iteration. You will learn:

* How the ledger expresses to what extent business logic can be changed and by who.
* How change is managed in a world where there are no privileged administrators who can force upgrades.

It's worth planning for versioning and upgrades from the start, especially if you plan for your CorDapp to itself provide APIs to other
apps. Apps extending the platform with industry-specific data models is a common case, and ensuring you can evolve your data model as
the world changes is a key part of any professionally built software.

.. toctree::
   :maxdepth: 1

   api-stability-guarantees
   versioning
   upgrading-cordapps
   cordapp-constraint-migration
   cordapp-upgradeability
