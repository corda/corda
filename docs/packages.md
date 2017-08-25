# Package net.corda.client.jackson

Utilities and serialisers for working with JSON representations of basic types. This adds Jackson support for
the java.time API, some core types, and Kotlin data classes.

# Package net.corda.client.jfx.model

Data models for binding data feeds from Corda nodes into a JavaFX user interface, by presenting the data as [javafx.beans.Observable]
types.

# Package net.corda.client.jfx.utils

Utility classes (i.e. data classes) used by the Corda JavaFX client.

# Package net.corda.client.mock

Tools used by the client to produce mock data for testing purposes.

# Package net.corda.client.rpc

RPC client interface to Corda, for use both by user-facing client and integration with external systems.

# Package net.corda.core.concurrent

Provides a simplified [java.util.concurrent.Future] class that allows registration of a callback to execute when the future
is complete.

# Package net.corda.core.contracts

Base data types for smarts contracts implemented in Corda. To implement a new contract start with [Contract], or see the examples in [net.corda.finance.contracts].

# Package net.corda.core.crypto

Cryptography data and utility classes used for signing, verifying, key management and data integrity checks.

# Package net.corda.core.crypto.composite

Composite key and signature classes, which are used to represent the signing requirements for multisignature scenarios such as RAFT notary services.

# Package net.corda.core.flows

Corda flows are a tool for modelling the interactions between two or more nodes as they negotiate a workflow. This can range from a simple case of completing a trade which has been agreed upon externally, to more complex processes such as handling fixing of interest rate swaps.

See [FlowLogic] for the basic class all flows extend, or [CollectSignaturesFlow] for a simple example flow. Flows are started via a node's service hub.

# Package net.corda.core.identity

Data classes which model different forms of identity (potentially with supporting evidence) for legal entities and services.

# Package net.corda.core.internal

Internal, do not use. These APIs and implementations which are currently being revised and are subject to future change.

# Package net.corda.core.node.services

Services which run within a Corda node and provide various pieces of functionality such as identity management, transaction storage, etc.

# Package net.corda.core.node.services.vault

Supporting data types for the vault services.

# Package net.corda.core.transactions

Base data types for transactions which modify contract state on the distributed ledger.

The core transaction on the ledger is [WireTransaction], which is constructed by [TransactionBuilder]. Once signed a transaction is stored
in [SignedTransaction] which encapsulates [WireTransaction]. Finally there is a special-case [LedgerTransaction] which is used by contracts
validating transactions, and is built from the wire transaction by resolving all references into their underlying data (i.e. inputs are
actual states rather than state references).

# Package net.corda.finance.utils

A collection of utilities for summing financial states, for example, summing obligations to get total debts.

# Package net.corda.jackson

Support classes for integrating the Jackson JSON serializer/deserializer with Corda.

# Package net.corda.node.internal

Internal, do not use. These APIs and implementations which are currently being revised and are subject to future change.

# Package net.corda.node.services

Implementations of services used by Corda nodes.

# Package net.corda.node.services.api

Non-core node service APIs; typically these are not used outside of the node itself.

