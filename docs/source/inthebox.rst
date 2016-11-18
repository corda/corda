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
