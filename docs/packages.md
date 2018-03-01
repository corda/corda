# Package net.corda.client.jackson

Utilities and serialisers for working with JSON representations of basic types. This adds Jackson support for
the java.time API, some core types, and Kotlin data classes.

# Package net.corda.client.rpc

RPC client interface to Corda, for use both by user-facing client and integration with external systems.

# Package net.corda.client.rpc.internal

Internal, do not use. These APIs and implementations which are currently being revised and are subject to future change.

# Package net.corda.core

Exception types and some utilities for working with observables and futures.

# Package net.corda.core.concurrent

Provides a simplified [java.util.concurrent.Future] class that allows registration of a callback to execute when the future
is complete.

# Package net.corda.core.contracts

This package contains the base data types for smarts contracts implemented in Corda. To implement a new contract start
with [Contract], or see the examples in `net.corda.finance.contracts`.

Corda smart contracts are a combination of state held on the distributed ledger, and verification logic which defines
which transformations of state are valid.

# Package net.corda.core.cordapp

This package contains the interface to CorDapps from within a node. A CorDapp can access it's own context by using
the CordappProvider.getAppContext() class. These classes are not intended to be constructed manually and no interface
to do this will be provided. 

# Package net.corda.core.crypto

Cryptography data and utility classes used for signing, verifying, key management and data integrity checks.

# Package net.corda.core.flows

Base data types and abstract classes for implementing Corda flows. To implement a new flow start with [FlowLogic], or
see [CollectSignaturesFlow] for a simple example flow. Flows are started via a node's [ServiceHub].

Corda flows are a tool for modelling the interactions between two or more nodes as they negotiate a workflow.
This can range from a simple case of completing a trade which has been agreed upon externally, to more complex
processes such as handling fixing of interest rate swaps.

# Package net.corda.core.identity

Data classes which model different forms of identity (potentially with supporting evidence) for legal entities and services.

# Package net.corda.core.messaging

Data types used by the Corda messaging layer to manage state of messaging and sessions between nodes.

# Package net.corda.core.node.services

Services which run within a Corda node and provide various pieces of functionality such as identity management, transaction storage, etc.

# Package net.corda.core.node.services.vault

Supporting data types for the vault services.

# Package net.corda.core.schemas

Data types representing database schemas for storing Corda data via an object mapper such as Hibernate. Modifying Corda
state in the database directly is not a supported approach, however these can be used to read state for integrations with
external systems.

# Package net.corda.core.serialization

Supporting data types and classes for serialization of Corda data types.

# Package net.corda.core.transactions

Base data types for transactions which modify contract state on the distributed ledger.

The core transaction on the ledger is [WireTransaction], which is constructed by [TransactionBuilder]. Once signed a transaction is stored
in [SignedTransaction] which encapsulates [WireTransaction]. Finally there is a special-case [LedgerTransaction] which is used by contracts
validating transactions, and is built from the wire transaction by resolving all references into their underlying data (i.e. inputs are
actual states rather than state references).

# Package net.corda.core.utilities

Corda utility classes, providing a broad range of functionality to help implement both Corda nodes and CorDapps.


# Package net.corda.finance

Some simple testing utilities like pre-defined top-level values for common currencies. Mostly useful for
writing unit tests in Kotlin.

WARNING: NOT API STABLE.

# Package net.corda.finance.utils

A collection of utilities for summing financial states, for example, summing obligations to get total debts.

WARNING: NOT API STABLE.

# Package net.corda.finance.contracts

Various types for common financial concepts like day roll conventions, fixes, etc. 

WARNING: NOT API STABLE.

# Package net.corda.finance.contracts.asset

Cash states, obligations and commodities. 

WARNING: NOT API STABLE. 

# Package net.corda.finance.contracts.asset.cash.selection

Provisional support for pluggable cash selectors, needed for different database backends. 

WARNING: NOT API STABLE. 

# Package net.corda.finance.contracts.math

Splines and interpolation.

WARNING: NOT API STABLE. 

# Package net.corda.finance.flows

Cash payments and issuances. Two party "delivery vs payment" atomic asset swaps.

WARNING: NOT API STABLE. 

# Package net.corda.finance.plugin

JSON/Jackson plugin for business calendars.

WARNING: NOT API STABLE.

# Package net.corda.finance.schemas

JPA (Java Persistence Architecture) schemas for the financial state types.

WARNING: NOT API STABLE.

# Package net.corda.testing.core

Generic test utilities for contracts and flows

# Package net.corda.testing.node

Test utilites to help running nodes programmatically for tests

# Package net.corda.testing.driver

Test utilites to help running nodes programmatically for tests

# Package net.corda.testing.dsl

A simple DSL for building pseudo-transactions (not the same as the wire protocol) for testing purposes
 
# Package net.corda.testing.contracts

Dummy state and contracts for testing purposes

# Package net.corda.testing.services

Mock service implementations for testing purposes

# Package net.corda.testing.http

A small set of utilities for working with http calls. 

WARNING: NOT API STABLE.