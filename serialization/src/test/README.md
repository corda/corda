# Adding tests to the Serialization module

Any tests that do not require further Corda dependencies (other than `core`) should be added to this module, anything that requires additional 
Corda dependencies needs to go into `serialization-tests`.

The Corda Serialization module should be self-contained and compilable to Java 8 (for the DJVM) bytecode when using a Java 11 compiler. 
Prior to this change, it was impossible to use a Java 11 compiler to compile this module to Java 8 bytecode due to its dependencies on other
modules compiled to Java 11 (`node-driver` and transitive dependencies including: `test-utils`, `node`, `test-common`, `common-logging`, `node-api`, 
`client-mock`. `tools-cliutils`). 
Therefore, any tests that require further Corda dependencies need to be defined in the module `serialization-tests`, which has the full set 
of dependencies including `node-driver`.