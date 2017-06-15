This is a small guide on how to generate the required files for Intel's
Whitelisting form.

To generate the production key
===

At this point the HSM should be set up with the appropriate groups and
permissions.

This step should be done on a separate clean machine, with no internet
connection, only connected to the HSM. The hsm-tool should be used directly,
this way the only dependency is a working JDK.

To generate the key:

`java -jar hsm-tool.jar --mode=GenerateKey --profile=prod`


This will require two separate smartcard authentications. The generation
will fail if there is already an existing production key in the HSM.


To generate a production enclave signature
===

This may be done from a dev machine with an SGX device.

To generate the signature and related files:

```
make clean
make sigstruct-hsm PROFILE=prod
```

This will require two separate smartcard authentications.

Running the above will produce the following files in `build/`:

* `noop_enclave.unsigned.so`: The unsigned enclave

* `noop_enclave_blob_to_sign.bin`: The unsigned SIGSTRUCT blob to sign.

* `noop_enclave.signed.hsm.so`: The signed enclave(= the unsigned enclave + signed blob).

* `noop_enclave.sigstruct.hsm.bin`: The signed SIGSTRUCT blob extracted from the signed enclave.

* `noop_enclave.sigstruct-pretty.hsm.txt`: The pretty printed SIGSTRUCT.

To sanity check the signed enclave:

```
make noop_test
./build/noop_test ./build/noop_enclave.signed.hsm.so
```

The above should return cleanly.

Intel's whitelisting form requires the MRSIGNER value in hexadecimal
from `noop_enclave.sigstruct-pretty.hsm.txt`, furthermore we need to attach
`noop_enclave.sigstruct.hsm.bin`.
