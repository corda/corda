# Checkpoint tests

Restoring checkpoints require certain [JDK modules to be open](../node/capsule/src/main/resources/node-jvm-args.txt) (due to the use of
reflection). This isn't an issue for the node, as we can open up these modules via the Capsule and so doesn't impact the user in anyway. For
client code that connects to the node, or uses the Corda API outside of the node, we would rather not mandate that users also have to do
this. So, to ensure we don't accidently do that, we don't add these flags to our tests.

This module exists for those tests which are not using the out-of-process node driver, but need to test checkpoint deserialisation. The same
node JVM args are used, and so replicates the exact behaviour of checkpoint restoration as the node.
