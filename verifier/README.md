This is the external verifier process, which the node kicks off when it needs to verify transactions which itself can't. This will be mainly
due to differences in the Kotlin version used in the transaction contract compared to the Kotlin version used by the node.

This module is built with Kotlin 1.2 and so is only able to verify transactions which have contracts compiled with Kotlin 1.2. It relies on
specially compiled versions of `core`  and `serialization` also compiled with Kotlin 1.2 (`core-1.2` and `serialization-1.2` respectively)
to ensure compatibility.
