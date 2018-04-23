The build
=========

Prerequisites
-------------

* Install gcc/g++(6), autoconf, automake, ocaml, opendjk(8), libtool, python(2.7)
* Make sure JAVA_HOME points to your OpenJDK 8 installation
* Make sure CXX points to g++ (the project does NOT compile with other compilers like clang!)
* If your hardware supports SGX and you want to use it directly you need to install and load the sgx kernel module (verify by running `lsmod | grep isgx`) and have the sgx service running (on a systemd setup verify by running `systemctl status aesmd`). Note that this is only required for actually running the binary, the build should work fine without.
* The SGX SDK has a simulation mode that doesn't require hardware support. To use this edit `sgx-jvm/jvm-enclave/common/CMakeLists.txt` and change `set(SGX_USE_HARDWARE TRUE)` to `FALSE`

Toplevel Makefile targets
-------------------------

* `make` will download all other dependencies and build the sgx\_standalone\_verify binary, residing at `sgx-jvm/jvm-enclave/standalone/build/sgx\_standalone\_verify`, as well as a JNI .so residing at `sgx-jvm/jvm-enclave/jni/build/untrusted_corda_sgx.so`
* `make clean` will clean all build targets.
* `make distclean` will clean all build targets and downloaded dependencies. Ordinarily you shouldn't need to run this.

Each project has its own build that may be run individually (check the toplevel Makefile to see how to invoke these)

At this point I suggest running `make` before reading further, it takes a while to download all dependencies.

Some reading
============

Before delving into the code it's strongly recommended to read up on SGX. Some links:

* Short high-level paper on the attestation design: https://software.intel.com/sites/default/files/article/413939/hasp-2013-innovative-technology-for-attestation-and-sealing.pdf
* Medium length description of an example attestation protocol: https://software.intel.com/en-us/articles/intel-software-guard-extensions-remote-attestation-end-to-end-example
* Lengthy programmer's reference including description of SGX specific instructions: https://software.intel.com/sites/default/files/managed/48/88/329298-002.pdf
* Lengthy low-level paper disecting the SGX design, going into hardware details: https://eprint.iacr.org/2016/086.pdf
* Lengthy SDK reference: https://download.01.org/intel-sgx/linux-1.7/docs/Intel_SGX_SDK_Developer_Reference_Linux_1.7_Open_Source.pdf


Corda SGX
=========

The high level goal of the SGX work in Corda is to provide a secure way of verifying transactions. In order to do this we need to be able to run a JVM inside an enclave capable of running contract code. The design decision that contract verification code is without side-effects is imperative here.

The dream is to have a functioning JVM running inside SGX with as few limitations as possible. Clients would then be able to connect to the enclave, the TCB would attest that it is running the JVM image on secure hardware, after which the client can safely submit signed JARs for execution.

Corda would then be able to use this to submit contract code and transactions to run the contract code on.

This is the first iteration of the work, with a lot of limitations. The current JVM is based on Avian which can produce a standalone statically linked binary. The build statically links the enclavelet JAR into the static enclave binary (`sgx-jvm/jvm-enclave/build/enclave/cordaenclave.so`) which is then loaded and run by `jvm/jvm-enclave/build/sgx\_experiments`.

Breakdown of the build
======================

The current SGX work in Corda is based on 4 semi-distinct projects:

* The Avian JVM (in the `sgx-jvm/avian` subtree. Note this is our own fork)
* The SGX linux sdk (in the `sgx-jvm/linux-sgx` subtree. Note this is our own fork)
* The JVM enclave code itself, residing in `sgx-jvm/jvm-enclave`. This includes the untrusted and trusted part of the SGXified JVM, mostly C++.
* Finally the Corda enclavelet. This is the JAR that will be loaded and run inside the enclave. (built by `./gradlew verify-enclave:jar`

Avian
-----

Avian has a code layout perfectly suited for SGX hacking. Each target platform (originally `posix` or `windows`) needs to implement a fairly straight-forward `System` interface providing OS-specific functionality like threading/synchronisation/memory/filesystem primitives. Check `sgx-jvm/avian/src/system` for code. We use this to implement an SGX "platform", which is basically a stripped down OS environment. Some additional #ifndef-ing was needed to strip some non-os-specific avian functionality that assumed the existence of a filesystem or networking. This work is maintained in a private fork, it is instructive to read through the diff, see https://bitbucket.org/R3-CEV/avian-sgx/.

SGX SDK
-------

There are some modifications in the upstream SGX SDK that we require to run the JVM. An example would be the ability to make the heap executable for JIT compilation, or exposing hooks into malloc to detect OOM conditions. All of these should be mergeable, but we maintain a fork to speed up development on our side.

Corda Enclavelet
----------------

This is the JAR that will be run inside the enclave. Check `verify-enclave/src/../Enclavelet.kt` for the code.

Currently the JAR is not loaded at runtime, but is rather embedded statically into the enclave itself using Avian's binaryToObject utility. This basically does an objcopy and lets the linker do the embedding later. This will later be changed to dynamic loading of signed JARs.

The JVM enclave
---------------

This consists of two parts: the untrusted code that loads the enclave and provides the OCALLs (see `sgx-jvm/jvm-enclave/main.cpp`), and the trusted enclave that constructs the JVM using JNI and runs the enclavelet class. (see `sgx-jvm/jvm-enclave/enclave/enclave.cpp`).

Dynamic loading, linkage
------------------------

Avian by default loads some JVM specific code dynamically, and looks up these symbols at runtime. We link these symbols statically and provide a simple binary search lookup at runtime to find the symbols corresponding to symbol name strings. To see how this is done check `sgx-jvm/jvm-enclave/enclave/gen_dispatch_table.py`.

Avian also statically links against system libraries providing usual OS functionality. We deal with this by stubbing all of the undefined symbols and implementing/mocking them as needed. The stub generation simply greps for undefined symbols when running make, check `sgx-jvm/jvm-enclave/enclave/gen-stubsyms.sh` for this. The implemented/mocked OS functions reside in `sgx-jvm/jvm-enclave/enclave/os_support.cpp`
