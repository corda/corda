# IAS proxy

The Intel Attestation Service proxy's responsibility is simply to forward requests to and from the IAS.

The reason we need this proxy is because Intel requires us to do Mutual TLS with them for each attestation round trip.
For this we need an R3 maintained private key, and as we want third parties to be able to do attestation we need to
store this private key in these proxies.

Alternatively we may decide to circumvent this mutual TLS requirement completely by distributing the private key with
the host containers.