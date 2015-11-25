What's included?
================

The current prototype consists of a small amount of code that defines:

* Key data structures
* Algorithms that work with them, such as serialising, hashing, signing, and verification of the signatures.
* Two smart contracts that implement a notion of a cash claim, and a notion of commercial paper. These are simplified
  versions of the real things.
* Unit tests that check the algorithms do what is expected, and which verify the behaviour of the smart contracts.
* API documentation and tutorials (what you're reading)

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