# Adding tests to the Core module

**TL;DR**: Any tests that do not require further dependencies should be added to this module, anything that 
requires additional Corda dependencies needs to go into `core-tests`.
 
The Corda core module defines a lot of types and helpers that can only be exercised, and therefore tested, in
the context of a node. However, as everything else depends on the core module, we cannot pull the node into
this module. Therefore, any tests that require further Corda dependencies need to be defined in the module
 `core-tests`, which has the full set of dependencies including `node-driver`.

# ZipBomb tests

There is a unit test that checks the zip bomb detector in `net.corda.core.internal.utilities.ZipBombDetector` works correctly. 
This test (`core/src/test/kotlin/net/corda/core/internal/utilities/ZipBombDetectorTest.kt`) uses real zip bombs, provided by `https://www.bamsoftware.com/hacks/zipbomb/`.
As it is undesirable to have unit test depends on external internet resources we do not control, those files are included as resources in
`core/src/test/resources/zip/`, however some Windows antivirus software correctly identifies those files as zip bombs, 
raising an alert to the user. To mitigate this, those files have been obfuscated using `net.corda.core.obfuscator.XorOutputStream` 
(which simply XORs every byte of the file with the previous one, except for the first byte that is XORed with zero) 
to prevent antivirus software from detecting them as zip bombs and are de-obfuscated on the fly in unit tests using 
`net.corda.core.obfuscator.XorInputStream`. 

There is a dedicated Gradle task to re-download and re-obfuscate all the test resource files named `writeTestResources`, 
its source code is in `core/src/obfuscator/kotlin/net/corda/core/internal/utilities/TestResourceWriter.kt` 
 