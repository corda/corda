# Intel(R) EPID SDK

The Intel(R) Enhanced Privacy ID Software Development Kit

Intel(R) EPID SDK enables adding Intel(R) EPID support to applications
and platforms.

Intel(R) EPID is a cryptographic protocol which enables the remote
authentication of a trusted platform whilst preserving the user's
privacy.

* For a given public key there are many (e.g., millions) of private
  keys. The key holders form a group.

* Any key holder may sign against the one public key.

* No one can tell which private key signed the data. This is the
  privacy property.

You can use Intel(R) EPID as a foundational building block for a
multitude of security solutions.


## Prerequisites

* [Python](http://www.python.org)

* [SCons](http://www.scons.org/)

* [Parts](https://bitbucket.org/sconsparts/parts)

* A C/C++ compiler supported by Parts


## What's New in This Release

See [CHANGELOG.md](CHANGELOG.md).


## Documentation

See [doc/index.html](doc/index.html) to browse the html
documentation.


## License

See [LICENSE.txt](LICENSE.txt).


## Math Primitives

The source code used for math primitives in the Intel(R) EPID SDK is a
subset of the Intel(R) IPP Cryptography library (v9.0.3) written in
C. For higher performance, you can use the commercial version of the
IPP Cryptography libraries, which are available at
https://software.intel.com/articles/download-ipp-cryptography-libraries.
