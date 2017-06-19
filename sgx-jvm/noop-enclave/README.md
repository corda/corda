What is this?
===

This project contains a noop enclave with a single ECALL that does
nothing. Its purpose is to demonstrate our ability to create a signed
enclave and to test the signature process through an HSM.

How to run
===

The following Makefile targets execute different steps in the signing process and output into build/


* `make unsigned` will build the unsigned enclave and extract the unsigned SIGSTRUCT blob to sign (noop\_enclave.unsigned.so, noop\_enclave\_blob\_to\_sign.bin).


The following targets use OpenSSL instead of the HSM:

* `make signed-openssl` will sign the unsigned enclave with openssl using selfsigning.pem (noop\_enclave.signed.openssl.so).

* `make sigstruct-openssl` will extract the SIGSTRUCT into a blob as well as a pretty printed txt from the openssl signed enclave (noop\_enclave.sigstruct.openssl.bin, noop\_enclave.sigstruct-pretty.openssl.txt).


The following targets use the HSM. They require an extra `PROFILE=[dev|prod]` argument to indicate whether to use a local HSM simulator or the real thing.

* `make generate-key-hsm PROFILE=[dev|prod] [OVERWRITE=true]` will generate a fresh key for the profile. By default this will not overwrite an existing key, for that pass in OVERWRITE=true.

* `make signed-hsm PROFILE=[dev|prod]` will sign the unsigned enclave with the HSM. This target requires authentication (noop\_enclave.signed.hsm.so).

* `make sigstruct-hsm PROFILE=[dev|prod]` will extract the SIGSTRUCT into a blob as well as a pretty printed txt from the HSM signed enclave (noop\_enclave.sigstruct.hsm.bin, noop\_enclave.sigstruct-pretty.hsm.txt).


* `make noop_test` will create a test binary that loads an enclave and runs the noop ECALL inside it. For example:

  `./build/noop_test ./build/noop_enclave.signed.openssl.so`

  will run the noop ECALL using the openssl signed enclave.


See IntelWhitelistFormInstructions.md for details on how to use this
project to fill the enclave specific parts of Intel's whitelisting form.
