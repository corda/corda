This is the source of the external verifier process, which the node kicks off when it needs to verify transactions which itself can't. This
is due to differences in the Kotlin (and Java) version used in the transaction contracts compared to the Kotlin version used by the node.

The node has one external verifier for each legacy contract generation (see `net.corda.core.internal.verification.ContractGeneration`),
each built from this source with that generation's version of Kotlin:

* `verifier-1.2` verifies Corda 4.11 (or earlier) transactions, i.e. those with contracts compiled with Kotlin 1.2. It relies on
  specially compiled versions of `core` and `serialization` also compiled with Kotlin 1.2 (`core-1.2` and `serialization-1.2`).
* `verifier-1.9` verifies Corda 4.12 to 4.15 transactions, i.e. those with contracts compiled with Kotlin 1.9, using `core-1.9` and
  `serialization-1.9`.

All the verifiers run on the same JDK as the node. This source must therefore compile with every legacy Kotlin version.
