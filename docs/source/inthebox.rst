What's included?
================

The Corda prototype currently includes:

* A peer to peer network with message persistence and delivery retries.
* Key data structures for defining contracts and states.
* Smart contracts:
    * Cash
    * Cash obligations
    * Interest rate swaps
    * Commercial paper (implemented in both Java and Kotlin for comparison)
* Algorithms that work with them, such as serialising, hashing, signing, and verification of the signatures.
* API documentation and tutorials (what you're reading).
* A business process orchestration framework.
* Notary infrastructure for precise timestamping, and elimination of double spending without a blockchain.
* A simple REST API.

Some things it does not currently include but should gain later are:

* Sandboxing, distribution or publication of smart contract code
* Database persistence
* A user interface for administration
* Many other things

The prototype's goal is rapid exploration of ideas. Therefore in places it takes shortcuts that a production system
would not in order to boost productivity:

* It uses an object graph serialization framework instead of a well specified, vendor neutral protocol.
* It uses the default, out of the box Apache Artemis MQ protocol instead of AMQP/1.0 (although switching should be easy)
* There is no inter-node SSL or other encryption yet.

Contracts
---------

The primary goal of this prototype is to implement various kinds of contracts and verify that useful business logic
can be expressed with the data model, developing and refining an API along the way. To that end there are currently
four contracts in the repository:

1. Cash
2. Commercial paper
3. Nettable obligations
4. Interest rate swaps

``Cash`` implements the idea of a claim on some quantity of deposits at some institutional party, denominated in some currency,
identified by some *deposit reference*. A deposit reference is an opaque byte array which is usable by
the issuing party for internal bookkeeping purposes.

Cash states are *fungible* with each other (can be merged and split arbitrarily) if they use the same currency,
party and deposit reference.

``CommercialPaper`` implements an asset with a *face value* denominated in a certain currency, which may be redeemed at
the issuing party after a certain time. Commercial paper states define the face value (e.g. $1000) and the time
at which they may be redeemed. The contract allows the paper to be issued, traded and redeemed. The commercial paper
contract is implemented twice, once in Java and once in a language called Kotlin.

``InterestRateSwap`` implements a vanilla OTC same currency bilateral fixed / floating leg swap. For further details,
see :doc:`irs`

``Obligation`` implements a bilaterally or multi-laterally nettable, fungible obligation that can default.

Each contract comes with unit tests.

Kotlin
------

Corda is written in a language called `Kotlin <https://kotlinlang.org/>`_. Kotlin is a language that targets the JVM
and can be thought of as a simpler Scala, with much better Java interop. It is developed by and has commercial support
from JetBrains, the makers of the IntelliJ IDE and other popular developer tools.

As Kotlin is very new, without a doubt you have not encountered it before. Don't worry: it is designed as a better
Java for industrial use and as such, the syntax was carefully designed to be readable even to people who don't know
the language, after only a few minutes of introduction.

Due to the seamless Java interop the use of Kotlin to extend the platform is *not* required and the tutorial shows how
to write contracts in both Kotlin and Java. You can `read more about why Kotlin is a potentially strong successor to Java here <https://medium.com/@octskyward/why-kotlin-is-my-next-programming-language-c25c001e26e3>`_.