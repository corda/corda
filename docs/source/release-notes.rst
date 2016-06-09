Release notes
=============

Here are brief summaries of what's changed between each snapshot release.

Unreleased
----------

Here are changes in git master that haven't yet made it to a snapshot release:

* The cash contract has moved from com.r3corda.contracts to com.r3corda.contracts.cash.
* Amount class is now generic, to support non-currency types (such as assets, or currency with additional information).
* Refactored the Cash contract to have a new FungibleAsset superclass, to model all countable assets that can be merged
  and split (currency, barrels of oil, etc.)
* Switched to the ed25519 elliptic curve from secp256r1. Note that this introduces a new external lib dependency.

Milestone 0
-----------

This is the first release, which includes:

* Some initial smart contracts: cash, commercial paper, interest rate swaps
* An interest rate oracle
* The first version of the protocol/orchestration framework
* Some initial support for pluggable consensus mechanisms
* Tutorials and documentation explaining how it works
* Much more ...
