# Adding tests to the Core module

**TL;DR**: Any tests that do not require further dependencies should be added to this module, anything that 
requires additional Corda dependencies needs to go into `core-tests`.
 
The Corda core module defines a lot of types and helpers that can only be exercised, and therefore tested, in
the context of a node. However, as everything else depends on the core module, we cannot pull the node into
this module. Therefore, any tests that require further Corda dependencies need to be defined in the module
 `core-tests`, which has the full set of dependencies including `node-driver`.
 

 