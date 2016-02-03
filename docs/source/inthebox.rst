What's included?
================

The current prototype consists of a small amount of code that defines:

* Key data structures
* Algorithms that work with them, such as serialising, hashing, signing, and verification of the signatures.
* Three smart contracts that implement a notion of a cash claim, a basic commercial paper and a crowdfunding contract.
  These are simplified versions of the real things.
* Unit tests that check the algorithms do what is expected, and which verify the behaviour of the smart contracts.
* API documentation and tutorials (what you're reading)
* A simple standalone node that uses an embedded message queue broker as its P2P messaging layer
* A trading demo that runs the node in either a listening/buying mode, or a connecting/selling mode, and swaps some
  fake commercial paper assets for some self-issued IOU cash.

Some things it does not currently include but should gain later are:

* Sandboxing, distribution or publication of smart contract code
* A peer to peer network
* Database persistence
* An API for integrating external software
* A user interface for administration
* Many other things

You can browse `the JIRA bug tracker <https://r3-cev.atlassian.net/>`_.

The prototype's goal is rapid exploration of ideas. Therefore in places it takes shortcuts that a production system
would not in order to boost productivity:

* It uses a serialization framework instead of a well specified, vendor neutral protocol.
* It uses secp256r1, an obsolete elliptic curve.

Contracts
---------

The primary goal of this prototype is to implement various kinds of contracts and verify that useful business logic
can be expressed with the data model, developing and refining an API along the way. To that end there are currently
two contracts in the repository:

1. Cash
2. Commercial paper

``Cash`` implements the idea of a claim on some quantity of deposits at some institutional party, denominated in some currency,
identified by some *deposit reference*. A deposit reference is an opaque byte array which is usable by
the issuing party for internal bookkeeping purposes.

Cash states are *fungible* with each other (can be merged and split arbitrarily) if they use the same currency,
party and deposit reference.

``CommercialPaper`` implements an asset with a *face value* denominated in a certain currency, which may be redeemed at
the issuing party after a certain time. Commercial paper states define the face value (e.g. $1000) and the time
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
