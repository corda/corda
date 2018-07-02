# Discovery

In order to understand enclave discovery and routing we first need to understand the mappings between CPUs, VMs and 
enclave hosts.

The cloud provider manages a number of physical machines (CPUs), each of those machines hosts a hypervisor which in 
turn hosts a number of guest VMs. Each VM in turn may host a number of enclave host containers (together with required 
supporting software like aesmd) and the sgx device driver. Each enclave host in turn may host several enclave instances.
For the sake of simplicity let's assume that an enclave host may only host a single enclave instance per measurement.

We can figure out the identity of the CPU the VM is running on by using a dedicated enclave to derive a unique ID 
specific to the CPU. For this we can use EGETKEY with pre-defined inputs to derive a seal key sealed to MRENCLAVE. This
provides a 128bit value reproducible only on the same CPU in this manner. Note that this is completely safe as the 
value won't be used for encryption and is specific to the measurement doing this. With this ID we can reason about 
physical locality of enclaves without looping in the cloud provider.
Note: we should set OWNEREPOCH to a static value before doing this.

We don't need an explicit handle on the VM's identity, the mapping from VM to container will be handled by the
orchestration engine (Kubernetes).

Similarly to VM identity, the specific host container's identity(IP address/DNS A) is also tracked by Kubernetes,
however we do need access to this identity in order to implement discovery.

When an enclave instance seals a secret that piece of data is tied to the measurement+CPU combo. The secret can only be
revealed to an enclave with the same measurement running on the same CPU. However the management of this secret is 
tied to the enclave host container, which we may have several of running on the same CPU, possibly all of them hosting
enclaves with the same measurement.

To solve this we can introduce a *sealing identity*. This is basically a generated ID/namespace for a collection of
secrets belonging to a specific CPU. It is generated when a fresh enclave host starts up and subsequently the host will 
store sealed secrets under this ID. These secrets should survive host death, so they will be persisted in etcd (together
with the associated active channel sets). Every host owns a single sealing identity, but not every sealing identity may
have an associated host (e.g. in case the host died).

## Mapping to Kubernetes

The following mapping of the above concepts to Kubernetes concepts is not yet fleshed out and requires further
investigation into Kubernetes capabilities.

VMs correspond to Nodes, and enclave hosts correspond to Pods. The host's identity is the same as the Pod's, which is
the Pod's IP address/DNS A record. From Kubernetes's point of view enclave hosts provide a uniform stateless Headless
Service. This means we can use their scaling/autoscaling features to  provide redundancy across hosts (to balance load).

However we'll probably need to tweak their (federated?) ReplicaSet concept in order to provide redundancy across CPUs
(to be tolerant of CPU failures), or perhaps use their anti-affinity feature somehow, to be explored.

The concept of a sealing identity is very close to the stable identity of Pods in Kubernetes StatefulSets. However I
couldn't find a way to use this directly as we need to tie the sealing identity to the CPU identity, which in Kubernetes
would translate to a requirement to pin stateful Pods to Nodes based on a dynamically determined identity. We could
however write an extension to handle this metadata.

## Registration

When an enclave host is started it first needs to establish its sealing identity. To this end first it needs to check
whether there are any sealing identities available for the CPU it's running on. If not it can generate a fresh one and
lease it for a period of time (and update the lease periodically) and atomically register its IP address in the process.
If an existing identity is available the host can take over it by leasing it. There may be existing Kubernetes
functionality to handle some of this.

Non-enclave services (like blob storage) could register similarly, but in this case we can take advantage of Kubernetes'
existing discovery infrastructure to abstract a service behind a Service cluster IP. We do need to provide the metadata
about supported channels though.

## Resolution

The enclave/service discovery problem boils down to:
"Given a channel, my trust model and my identity, give me an enclave/service that serves this channel, trusts me, and I
trust them".

This may be done in the following steps:

1. Resolve the channel to a set of measurements supporting it
2. Filter the measurements to trusted ones and ones that trust us
3. Pick one of the measurements randomly
4. Find an alive host that has the channel in its active set for the measurement

1 may be done by maintaining a channel -> measurements map in etcd. This mapping would effectively define the enclave
deployment and would be the central place to control incremental roll-out or rollbacks.

2 requires storing of additional metadata per advertised channel, namely a datastructure describing the enclave's trust
predicate. A similar datastructure is provided by the discovering entity - these two predicates can then be used to
filter measurements based on trust.

3 is where we may want to introduce more control if we want to support incremental roll-out/canary deployments.

4 is where various (non-MVP) optimisation considerations come to mind. We could add a loadbalancer, do autoscaling based
on load (although Kubernetes already provides support for this), could have a preference for looping back to the same
host to allow local attestation, or ones that have the enclave image cached locally or warmed up.
