This is a small guide on how to generate the required files for Intel's
Whitelisting form.

To build the hsm-tool.jar
===
Build the JAR inside the build container (using the `sx` tool):

```
sx exec ./gradlew sgx-jvm/hsm-tool:jar
```

To generate the production key
===

At this point the HSM should be set up with the appropriate groups and
permissions.

This step should be done on a separate clean machine, with no internet
connection, only connected to the HSM. The hsm-tool should be used directly,
this way the only dependency is a working JDK.

To generate the key:

```
java -jar sgx-jvm/hsm-tool/build/libs/sgx-jvm/hsm-tool-1.0-SNAPSHOT.jar --mode=GenerateSgxKey --profile=prod
```


This will require two separate smartcard authentications. The generation
will fail if there is already an existing production key in the HSM.


To generate a production enclave signature
===

This step requires the outer sgx-jvm to be built.

First build the unsigned enclave within the docker image:

```
sx build ../noop-enclave unsigned
```

Now sign the enclave outside of the image:

```
java -jar sgx-jvm/hsm-tool/build/libs/sgx-jvm/hsm-tool-1.0-SNAPSHOT.jar --mode=Sign --source=sgx-jvm/noop-enclave/build/noop_enclave_blob_to_sign.bin --signature=sgx-jvm/noop-enclave/build/noop_enclave.signature.hsm.sha256 --pubkey=sgx-jvm/noop-enclave/build/hsm.public.pem --profile=prod
```

Now assemble the signed enclave and extract the SIGSTRUCT within the docker image.

```
sx build ../noop-enclave sigstruct-hsm
```

Running the above steps will produce the following files in `sgx-jvm/noop-enclave/build/`:

* `noop_enclave.unsigned.so`: The unsigned enclave

* `noop_enclave_blob_to_sign.bin`: The unsigned SIGSTRUCT blob to sign.

* `noop_enclave.signed.hsm.so`: The signed enclave(= the unsigned enclave + signed blob).

* `noop_enclave.sigstruct.hsm.bin`: The signed SIGSTRUCT blob extracted from the signed enclave.

* `noop_enclave.sigstruct-pretty.hsm.txt`: The pretty printed SIGSTRUCT.

* `hsm.public.pem`: The public key corresponding to the signature of the HSM.


Intel's whitelisting form requires the MRSIGNER value in hexadecimal
from `noop_enclave.sigstruct-pretty.hsm.txt`, furthermore we need to attach
`noop_enclave.sigstruct.hsm.bin`.

To sanity check the signed enclave you need to build the test, load the isgx kernel module and load the aesmd systemd unit, then run the test itself. Note that this requires the kernel module to be built in `linux-sgx-driver`, and `aesm_service` to be built in `linux-sgx`.

```
sx build ../noop-enclave noop_test
sx ./sgx-jvm/noop-enclave/build/noop_test sgx-jvm/noop-enclave/build/noop_enclave.signed.hsm.so
```

The above should return cleanly.
