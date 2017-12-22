# Remote Attestation

![Flow between Challenger, Host, ISV and IAS](challenger-flow.png "Remote Attestation Flow")

## Project Organisation

  * **Enclave**

    The enclave (`enclave/`) is responsible for initialising and coordinating
    the remote attestation process from the client side, and will eventually
    operate on a secret provisioned from the challenger (once successfully
    attested by Intel's Attestation Service).

  * **Host**

    The host JVM (`host/`) is running in an untrusted environment and
    facilitates the communication between the challenger and its enclave.
    To coordinate with the enclave, the host uses a native JNI library (in
    `host/native/`)

  * **Challenger**

    The challenger JVM does not require SGX-enabled hardware and is essentially
    the party asking the host to prove that it has spun up a program in an
    enclave on trusted hardware (that cannot be tampered with), so that
    consequently, it can provision an encrypted secret to said enclave.

  * **IAS Proxy**

    The proxy is responsible for talking to the Intel Attestation Service over
    mutual TLS to verify attestation evidence received from the host. The proxy
    needs a client certificate and a service provider identifier (SPID) issued
    by Intel. In turn, it will forward any received proof from Intel to the
    host and challenger, making it possible for the challenger to trust the
    host and thus provision the secret. The proof is signed with Intel's root
    certificate.

## Getting Started

To get started, run the following commands in `sgx-jvm`:

```bash
> source environment
> sx help
```

Further documentation is available in `sgx-jvm/tools/sx/README.md`.
