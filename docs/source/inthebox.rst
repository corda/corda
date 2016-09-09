What's included?
================

The Corda prototype currently includes:

* A peer to peer network with message persistence and delivery retries.
* Key data structures for defining contracts and states.
* Smart contracts, which you can find in the :doc:`contract-catalogue`.
* Algorithms that work with them, such as serialising, hashing, signing, and verification of the signatures.
* API documentation and tutorials (what you're reading).
* A business process orchestration framework.
* Notary infrastructure for precise timestamping, and elimination of double spending without a blockchain.
* A simple REST API, and a web app demo that uses it to present a frontend for IRS trading.

Some things it does not currently include but should gain later are:

* Sandboxing, distribution or publication of smart contract code
* A user interface for administration

The prototype's goal is rapid exploration of ideas. Therefore in places it takes shortcuts that a production system
would not in order to boost productivity:

* It uses an object graph serialization framework instead of a well specified, vendor neutral protocol.
* There's currently no permissioning framework.
* Some privacy techniques aren't implemented yet.
* It uses an embedded SQL database and doesn't yet have connectivity support for mainstream SQL vendors (Oracle,
  Postgres, MySQL, SQL Server etc).

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