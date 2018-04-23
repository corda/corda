Remote Attestation Phase 2
==========================

Phase 2 contains the following components:
- `ias-proxy`: This is the ISV, and is an authorised client of the Intel Attestation Service (IAS).
- `attestation-host`: This is a WAR running on an SGX-capable host with an enclave, and a client of `ias-proxy`.
- `attestation-challenger`: This is an executable JAR, and client of `attestation-host`.

Building Instructions
---------------------

- Ensure that your user ID belongs to the `docker` group. This will enable you to run Docker as an unprivileged user.

- Source the `environment` file:
```bash
$ . sgx-jvm/environment
```

- Build the Docker container:
```bash
$ cd sgx-jvm/containers/core
$ make
```

- Build the SGX SDK:
```bash
$ sx build linux-sgx clean all
```

- Build the SGX enclave:
```bash
$ sx build [-hp] remote-attestation/enclave clean all
```
Add the `-hp` options to build in "pre-release" mode for SGX hardware.

- Build the Attestation Host WAR:
```bash
$ sx build remote-attestation/attestation-host
```

- Build the JNI library for the Attestation Host:
```bash
$ sx build [-hp] remote-attestation/attestation-host/native clean all
```
Add the `-hp` options to build in "pre-release" mode for SGX hardware. This setting
must match the setting used to build the SGX enclave, or they will be incompatible
at runtime.

- Install our private key for Mutual-TLS with IAS:
```bash
$ cp client.key sgx-jvm/remote-attestation/ias-proxy/src/main/ssl/intel-ssl
```

- Build the IAS Proxy:
```bash
$ cd sgx-jvm/remote-attestation
$ gradlew ias-proxy:build
```

- Build the Attestation Challenger:
```bash
$ cd sgx-jvm/remote-attestation
$ gradlew attestation-challenger:installDist
```

Execution Instructions
----------------------

- To launch the Attestation Host:
```bash
$ sx exec
$ cd sgx-jvm/remote-attestation/attestation-host
$ nohup ../gradlew [-Phardware=true] startHost >& OUT &
$ tail -f build/logs/attestation-host.log
```
This can be shutdown again using:
```bash
$ ../gradlew stopHost
```

- To launch the IAS Proxy:
```bash
$ cd sgx-jvm/remote-attestation/ias-proxy
$ nohup ../gradlew startISV >& OUT &
$ tail -f build/logs/ias-proxy.log
```
This can be shutdown again using:
```bash
$ ../gradlew stopISV
```

- To execute the Attestation Challenger:
```bash
$ cd sgx-jvm/remote-attestation/attestation-challenger/build/install/attestation-challenger
$ bin/attestation-challenger
```
Use this executable's `--help` option for more information.

When all of the components are working correctly, you should expect the challenger
to output something like:
```bash
$ bin/attestation-challenger
Report ID:    197283916372863387388037565359257649452
Quote Status: OK
Timestamp:    2017-12-20T15:06:37.222956
Secret provisioned successfully.
```
