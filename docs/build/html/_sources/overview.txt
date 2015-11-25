Overview
========

This article covers the data model: how *states*, *transactions* and *code contracts* interact with each other and
how they are represented in the code. It doesn't attempt to give detailed design rationales or information on future
design elements: please refer to the R3 wiki for background information.

Data model
----------

We begin with the idea of a global ledger. In our model, although the ledger is shared, it is not always the case that
transactions and ledger entries are globally visible. In cases where a set of transactions stays within a small subgroup of
users it should be possible to keep the relevant data purely within that group.

To ensure consistency in a global, shared system where not all data may be visible to all participants, we rely
heavily on secure hashes like SHA-256 to identify things. The ledger is defined as a set of immutable **states**, which
are created and destroyed by digitally signed **transactions**. Each transaction points to a set of states that it will
consume/destroy, these are called **inputs**, and contains a set of new states that it will create, these are called
**outputs**.

States contain arbitrary data, but they always contain at minimum a pointer to the bytecode of a
**code contract**, which is a program expressed in some byte code that runs sandboxed inside a virtual machine. Code
contracts (or just "contracts" in the rest of this document) are globally shared pieces of business logic. Contracts
define a **verify function**, which is a pure function of the entire transaction.

To be considered valid, the transaction must be **accepted** by the verify function of every contract pointed to by the
input and output states. Beyond inputs and outputs, transactions may also contain **commands**, small data packets that
the platform does not interpret itself, but which can parameterise execution of the contracts. They can be thought of as
arguments to the verify function.

A transaction has one or more **signatures** attached to it. The signatures do not mean anything by themselves, rather,
their existence is given as input to the contract which can then decide which set of signatures it demands (if any).
Signatures may be from an arbitrary, random **public key** that has no identity attached. A public key may be
well known, that is, appears in some sort of public identity registry. In this case we say the key is owned by an
**institution**, which is defined (for now) as being merely a (public key, name) pair.

A transaction may also be **timestamped**. A timestamp is a (hash, datetime, signature) triple from a
*timestamping authority* (TSA). The notion of a TSA is not ledger specific and is defined by
`IETF RFC 3161 <https://www.ietf.org/rfc/rfc3161.txt>`_ which defines the internet standard Timestamping Protocol (TSP).
The purpose of the TSA is to attach a single, globally agreed upon time which a contract may use to enforce certain
types of time-based logic. The TSA's do not need to know about the contents of the transaction in order to provide a
timestamp, and they are therefore never exposed to private data.

.. note:: In the current code, use of TSAs is not implemented.

As the same terminology often crops up in different distributed ledger designs, let's compare this to other
distributed ledger systems you may be familiar with.

Comparison with Bitcoin
^^^^^^^^^^^^^^^^^^^^^^^

Similarities:

* The basic notion of immutable states that are consumed and created by transactions is the same.
* The notion of transactions having multiple inputs and outputs is the same. Bitcoin sometimes refers to the ledger
  as the unspent transaction output set (UTXO set) as a result.
* Like in Bitcoin, a contract is pure function. Contracts do not have storage or the ability to interact with anything.
  Given the same transaction, a contract's accept function always yields exactly the same result.
* Bitcoin output scripts are parameterised by the input scripts in the spending transaction. This is somewhat similar
  to our notion of a *command*.
* Bitcoin transactions, like ours, refer to the states they consume by using a (txhash, index) pair. The Bitcoin
  protocol calls these "outpoints". In our prototype code they are known as ``StateRefs`` but the concept is identical.
* Bitcoin transactions have an associated timestamp (the time at which they are mined).

Differences:

* A Bitcoin transaction has a single, rigid data format. A "state" in Bitcoin is always a (quantity of bitcoin, script)
  pair and cannot hold any other data. Some people have been known to try and hack around this limitation by embedding
  data in semi-standardised places in the contract code so the data can be extracted through pattern matching, but this
  is a poor approach. Our states can include arbitrary typed data.
* A Bitcoin transaction's acceptance is controlled only by the contract code in the consumed input states. In practice
  this has proved limiting. Our transactions invoke not only input contracts but also the contracts of the outputs.
* A Bitcoin script can only be given a fixed set of byte arrays as the input. This means there's no way for a contract
  to examine the structure of the entire transaction, which severely limits what contracts can do.
* Our contracts are Turing-complete and can be written in any ordinary programming language that targets the JVM.
* Our transactions and contracts have get their time from an attached timestamp rather than a block chain. This is
  important given that we are currently considering block-free conflict resolution algorithms.
* We use the term "contract" to refer to a bundle of business logic that may handle various different tasks, beyond
  transaction verification. For instance, currently our contracts also include code for creating valid transactions
  (this is often called "wallet code" in Bitcoin).

Comparison with Ethereum
^^^^^^^^^^^^^^^^^^^^^^^^

Similarities:

* Like Ethereum, code runs inside a relatively powerful virtual machine and can contain complex logic. Non-assembly
  based programming languages can be used for contract programming.
* They are both intended for the modelling of many different kinds of financial contract.

Differences:

* The term "contract" in Ethereum refers to an *instantiation* of a program that is replicated and maintained by
  every participating node. This instantiation is very much like an object in an OO program: it can receive and send
  messages, update local storage and so on. In contrast, use the term "contract" to refer to a set of functions, only
  one of which is a part of keeping the system synchronised (the verify function). That function is pure and
  stateless i.e. it may not interact with any other part of the system whilst executing.
* There is no notion of an "account", as there is in Ethereum.
* As contracts don't have any kind of mutable storage, there is no notion of a "message" as in Ethereum.
* Ethereum claims to be a platform not only for financial logic, but literally any kind of application at all. Our
  platform considers non-financial applications to be out of scope.


Contracts
---------

The primary goal of this prototype is to implement various kinds of contracts and verify that useful business logic
can be expressed with the data model, developing and refining an API along the way. To that end there are currently
two contracts in the repository:

1. Cash
2. Commercial paper

``Cash`` implements the idea of a claim on some quantity of deposits at some institution, denominated in some currency,
identified by some *deposit reference*. A deposit reference is an opaque byte array which is usable by
the issuing institution for internal bookkeeping purposes.

Cash states are *fungible* with each other (can be merged and split arbitrarily) if they use the same currency,
institution and deposit reference.

``CommercialPaper`` implements an asset with a *face value* denominated in a certain currency, which may be redeemed at
the issuing institution after a certain time. Commercial paper states define the face value (e.g. $1000) and the time
at which they may be redeemed. The contract allows the paper to be issued, traded and redeemed. The commercial paper
contract is implemented twice, once in Java and once in a language called Kotlin.

Each contract comes with unit tests.

Kotlin
------

The prototype is written in a language called `Kotlin <https://kotlinlang.org/>`_. Kotlin is a language that targets the JVM
and can be thought of as a simpler Scala, with much better Java interop. It is developed by and has commercial support
from JetBrains, the makers of the IntelliJ IDE and other popular developer tools.

As Kotlin is very new, without a doubt you have not encountered it before. Don't worry: it is designed as a better
Java for industrial use and as such, the syntax was carefully designed to be readable even to people who don't know
the language, after only a few minutes of introduction.

Due to the seamless Java interop the use of Kotlin to extend the platform is *not* required and the tutorial shows how
to write contracts in both Kotlin and Java. You can `read more about why Kotlin is a potentially strong successor to Java here <https://medium.com/@octskyward/why-kotlin-is-my-next-programming-language-c25c001e26e3>`_.

Kotlin programs use the regular Java standard library and ordinary Java frameworks. Frameworks used at this time are:

* JUnit for unit testing
* Kryo for serialisation (this is not intended to be permanent)
* Gradle for the build
* Guava for a few utility functions
