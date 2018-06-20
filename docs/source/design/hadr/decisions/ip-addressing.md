# Design Decision: IP addressing mechanism (near-term)

## Background / Context

End-to-end encryption is a desirable potential design feature for the [high availability support](../design.md).

## Options Analysis

### 1. Via load balancer

#### Advantages

1.    Standard technology in banks and on clouds, often for non-HA purposes.
2.    Intended to allow us to wait for completion of network map work.

#### Disadvantages

1.    We do need to support multiple IP address advertisements in network map long term.
2.    Might involve small amount of code if we find Artemis doesn’t like the health probes. So far though testing of the Azure Load balancer doesn’t need this.
3.    Won’t work over very large data centre separations, but that doesn’t work for HA/DR either

### 2. Via IP list in Network Map

#### Advantages

1. More flexible
2. More deployment options
3. We will need it one day

#### Disadvantages

1. Have to write code to support it.
2. Configuration more complicated and now the nodesare non-equivalent, so you can’t just copy the config to the backup.
3. Artemis has round robin and automatic failover, so we may have to expose a vendor specific config flag in the network map.

## Recommendation and justification

Proceed with Option 1: Via Load Balancer

## Decision taken

The design can allow for optional load balancers to be implemented by clients. (RGB, JC, MH agreed)

.. toctree::

   drb-meeting-20171116.md
