# Intel(R) EPID SDK ChangeLog                                   {#ChangeLog}

## [3.0.0]

### New in This Release

* Support for verification of EPID 1.1 members.

* Make-based build system support.

* Sample material includes compressed keys.

* Enhanced documentation, including step-by-step walkthroughs of
  example applications.

* Validated on additional IoT platforms.

  - Ostro Linux

  - Snappy Ubuntu Core


### Changes

* A new verifier API has been added to set the basename to be used for
  verification.  Verifier APIs that used to accept basenames now use
  the basename set via EpidVerifierSetBasename.

* The verifier pre-computation structure has been changed to include
  the group ID to allow detection of errors that result from providing
  a pre-computation blob from a different group to EpidVerifierCreate.


### Fixes

* The kEpidxxxRevoked enums have been renamed to be consistent with
  other result return values.


### Known Issues

* SHA-512/256 hash algorithm is not supported.


## [2.0.0] - 2016-07-20

### New in This Release

* Signed binary issuer material support.

  - Binary issuer material validation APIs.

  - Updated sample issuer material.

  - Updated samples that parse signed binary issuer material.

* Compressed member private key support.

* Validated on additional IoT platforms.

  - Windows 10 IoT Core

  - WindRiver IDP


### Changes

* The default hash algorithm has changed. It is now SHA-512.

* Functions that returned `EpidNullPtrErr` now return `EpidBadArgErr`
  instead.


### Fixes

* Updated build flags to work around GCC 4.8.5 defect.


## [1.0.0] - 2016-03-03

### New in This Release

* Basic sign and verify functionality

* Dynamic join support for member

* Apache 2.0 License
