Release notes
=============

Here are brief summaries of what's changed between each snapshot release.

Unreleased
----------

* Smart contracts have been redesigned around reusable components, referred to as "clauses". The cash, commercial paper
  and obligation contracts now share a common issue clause.

Milestone 1
-----------

Highlights of this release:

* Event scheduling. States in the ledger can now request protocols to be invoked at particular times, for states
  considered relevant by the wallet.
* Upgrades to the notary/consensus service support:

    * There is now a way to change the notary controlling a state.
    * You can pick between validating and non-validating notaries, these let you select your privacy/robustness tradeoff.

* A new obligation contract that supports bilateral and multilateral netting of obligations, default tracking and
  more.
* Improvements to the financial type system, with core classes and contracts made more generic.
* Switch to a better digital signature algorithm: ed25519 instead of the previous JDK default of secp256r1.
* A new integration test suite.
* A new Java unit testing DSL for contracts, similar in spirit to the one already developed for Kotlin users (which
  depended on Kotlin specific features).
* An experimental module, where developers who want to work with the latest Corda code can check in contracts/cordapp
  code before it's been fully reviewed. Code in this module has compiler warnings suppressed but we will still make
  sure it compiles across refactorings.
* Persistence improvements: transaction data is now stored to disk and automatic protocol resume is now implemented.
* Many smaller bug fixes, cleanups and improvements.

We have new documentation on:

* :doc:`event-scheduling`
* :doc:`transaction-data-types`
* :doc:`consensus`

Summary of API changes (not exhaustive):

* Notary/consensus service:

    * ``NotaryService`` is now extensible.
    * Every ``ContractState`` now has to specify a *participants* field, which is a list of parties that are able to
      consume this state in a valid transaction. This is used for e.g. making sure all relevant parties obtain the updated
      state when changing a notary.
    * Introduced ``TransactionState``, which wraps ``ContractState``, and is used when defining a transaction output.
      The notary field is moved from ``ContractState`` into ``TransactionState``.
    * Every transaction now has a *type* field, which specifies custom build & validation rules for that transaction type.
      Currently two types are supported: General (runs the default build and validation logic) and NotaryChange (
      contract code is not run during validation, checks that the notary field is the only difference between the
      inputs and outputs).
      ``TransactionBuilder()`` is now abstract, you should use ``TransactionType.General.Builder()`` for building transactions.

* The cash contract has moved from ``com.r3corda.contracts`` to ``com.r3corda.contracts.cash``
* ``Amount`` class is now generic, to support non-currency types such as physical assets. Where you previously had just
  ``Amount``, you should now use ``Amount<Currency>``.
* Refactored the Cash contract to have a new FungibleAsset superclass, to model all countable assets that can be merged
  and split (currency, barrels of oil, etc.)
* Messaging:

    * ``addMessageHandler`` now has a different signature as part of error handling changes.
    * If you want to return nothing to a protocol, use ``Ack`` instead of ``Unit`` from now on.

* In the IRS contract, dateOffset is now an integer instead of an enum.
* In contracts, you now use ``tx.getInputs`` and ``tx.getOutputs`` instead of ``getInStates`` and ``getOutStates``. This is
  just a renaming.
* A new ``NonEmptySet`` type has been added for cases where you wish to express that you have a collection of unique
  objects which cannot be empty.
* Please use the global ``newSecureRandom()`` function rather than instantiating your own SecureRandom's from now on, as
  the custom function forces the use of non-blocking random drivers on Linux.

Milestone 0
-----------

This is the first release, which includes:

* Some initial smart contracts: cash, commercial paper, interest rate swaps
* An interest rate oracle
* The first version of the protocol/orchestration framework
* Some initial support for pluggable consensus mechanisms
* Tutorials and documentation explaining how it works
* Much more ...
