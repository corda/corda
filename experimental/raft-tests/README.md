# Raft tests

Testing the `RaftUniquenessProvider` provider requires the `java.nio` packaage to be open (the atomix library does reflection into
`java.nio.Bits`). This module has this package opened up to allow mock and unit tests to work. This is preferred over having `java.nio` open
in every module as this is an experimental feature.
