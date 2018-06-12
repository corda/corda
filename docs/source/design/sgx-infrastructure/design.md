# SGX Infrastructure design

.. important:: This design document describes a feature of Corda Enterprise.

This document is intended as a design description of the infrastructure around the hosting of SGX enclaves, interaction
with enclaves and storage of encrypted data. It assumes basic knowledge of SGX concepts, and some knowledge of
Kubernetes for parts specific to that.

## High level description

The main idea behind the infrastructure is to provide a highly available cluster of enclave services (hosts) which can
serve enclaves on demand. It provides an interface for enclave business logic that's agnostic with regards to the
infrastructure, similar to [serverless architectures](details/serverless.md). The enclaves will use an opaque reference
to other enclaves or services in the form of [enclave channels](details/channels.md). Channels hides attestation details
and provide a loose coupling between enclave/non-enclave functionality and specific enclave images/services implementing
it. This loose coupling allows easier upgrade of enclaves, relaxed trust (whitelisting), dynamic deployment, and
horizontal scaling as we can spin up enclaves dynamically on demand when a channel is requested.

## Infrastructure components

Here are the major components of the infrastructure. Note that this doesn't include business logic specific
infrastructure pieces (like ORAM blob storage for Corda privacy model integration).

* [**Distributed key-value store**](details/kv-store.md):
  Responsible for maintaining metadata about enclaves, hosts, sealed secrets and CPU locality.
  
* [**Discovery service**](details/discovery.md)
  Responsible for resolving an enclave channel to a specific enclave image and a host that can serve it using the
  metadata in the key-value store.

* [**Enclave host**](details/host.md):
  This is a service capable of serving enclaves and driving the underlying traffic. Third party components like Intel's
  SGX driver and aesmd also belong here.

* [**Enclave storage**](details/enclave-storage.md):
  Responsible for serving enclave images to hosts. This is a simple static content server.

* [**IAS proxy**](details/ias-proxy.md):
  This is an unfortunate necessity because of Intel's requirement to do mutual TLS with their services.

## Infrastructure interactions

* **Enclave deployment**:
  This includes uploading of the enclave image/container to enclave storage and adding of the enclave metadata to the
  key-value store.

* **Enclave usage**:
  This includes using the discovery service to find a specific enclave image and a host to serve it, then connecting to
  the host, authenticating(attestation) and proceeding with the needed functionality.

* **Ops**:
  This includes management of the cluster (Kubernetes/Kubespray) and management of the metadata relating to discovery to
  control enclave deployment (e.g. canary, incremental, rollback).

## Decisions to be made

* [**Strategic roadmap**](decisions/roadmap.md)
* [**CPU certification method**](decisions/certification.md)
* [**Enclave language of choice**](decisions/enclave-language.md)
* [**Key-value store**](decisions/kv-store.md)

## Further details

* [**Attestation**](details/attestation.md)
* [**Calendar time for data at rest**](details/time.md)
* [**Enclave deployment**](details/enclave-deployment.md)

## Example deployment

This is an example of how two Corda parties may use the above infrastructure. In this example R3 is hosting the IAS 
proxy and the enclave image store and the parties host the rest of the infrastructure, aside from Intel components.

Note that this is flexible, the parties may decide to host their own proxies (as long as they whitelist their keys) or
the enclave image store (although R3 will need to have a repository of the signed enclaves somewhere).
We may also decide to go the other way and have R3 host the enclave hosts and the discovery service, shared between
parties (if e.g. they don't have access to/want to maintain SGX capable boxes).

![Example SGX deployment](Example%20SGX%20deployment.png)