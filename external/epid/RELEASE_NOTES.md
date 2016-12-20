# Intel(R) EPID SDK Release Notes                                   {#ChangeLog}

# 2.0.0

## New in This Release

* Signed binary issuer material support.

  - Binary issuer material validation APIs.

  - Updated sample issuer material.

  - Updated samples that parse signed binary issuer material.

* Compressed member private key support.

* Validated on additional IoT platforms.

	- Windows 10 IoT Core

	- WindRiver IDP


## Changes

* The default hash algorithm has changed. It is now SHA-512.

* Functions that returned `EpidNullPtrErr` now return `EpidBadArgErr`
  instead.


## Fixes

* Updated build flags to work around GCC 4.8.5 defect.


## Known Issues

* SHA-512/256 hash algorithm is not supported.

* Compressed key sample material is not included in the package.
