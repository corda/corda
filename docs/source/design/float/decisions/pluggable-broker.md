# Design Decision: Pluggable Broker prioritisation

## Background / Context

A decision on when to prioritise implementation of a pluggable broker has implications for delivery of key messaging
components including the [float](../design.md).

## Options Analysis

### 1. Deliver pluggable brokers now

#### Advantages

1.    Meshes with business opportunities from HPE and Solace Systems.
2.    Would allow us to interface to existing Bank middleware.
3.    Would allow us to switch away from Artemis if we need higher performance.
4.    Makes our AMQP story stronger.

#### Disadvantages

1.    More up-front work.
2.    Might slow us down on other priorities.

### 2. Defer development of pluggable brokers until later

#### Advantages

1. Still gets us where we want to go, just later.
2. Work can be progressed as resource is available, rather than right now.

#### Disadvantages

1. Have to take care that we have sufficient abstractions that things like CORE connections can be replaced later.
2. Leaves HPE and Solace hanging even longer.


### 3. Never enable pluggable brokers

#### Advantages

1. What we already have.

#### Disadvantages

1. Ties us to ArtemisMQ development speed.

2. Not good for our relationship with HPE and Solace.

3. Probably limits our maximum messaging performance longer term.


## Recommendation and justification

Proceed with Option 2 (defer development of pluggable brokers until later)

## Decision taken

```eval_rst
.. toctree::

   drb-meeting-20171116.md
```

Proceed with Option 2 - Defer support for pluggable brokers until later, except in the event that a requirement to do so emerges from higher priority float / HA work. (RGB, JC, MH agreed)
