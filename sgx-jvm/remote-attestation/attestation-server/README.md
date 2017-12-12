Remote Attestation ISV: Proof-Of-Concept
----------------------------------------

This initial version of the ISV expects to communicate with the [Attestation Host](../host), which should run on hardware
with a SGX enclave. The ISV also communicates with the Intel Attestation Service (IAS) over HTTPS with mutual TLS, which
requires it to contain our development private key. (We have already shared this key's public key with Intel, and IAS
expects to use it to authenticate us.)

Please install this private key in PEM formt as `src/main/ssl/intel-ssl/client.key`.

This ISV runs as a WAR file within Tomcat8, and implements the message flow as described in Intel's [end-to-end example](https://software.intel.com/en-us/articles/intel-software-guard-extensions-remote-attestation-end-to-end-example)
using JSON and HTTP. The use of HTTP here is mere convenience for our proof-of-concept; we anticipate using
something completely different when we integrate with Corda.

Gradle/Tomcat integration is achieved using the [Gretty plugin](https://github.com/akhikhl/gretty).

You will need OpenSSL installed so that Gradle can generate the keystores and truststores required by HTTPS and Mutual TLS.

## Building the ISV

From this project directory, execute the command:

```bash
$ ../gradlew build integrationTest
```

## Running the ISV

To launch the ISV as a daemon process listening on TCP/8080, execute:

```bash
$ nohup ../gradlew startISV &
```

The ISV can then be shutdown using:

```bash
$ ../gradlew stopISV
```

It will log messages to `build/logs/attestation-server.log`.

