# Design Decision: Broker separation

## Background / Context

A decision of whether to extract the Artemis message broker as a separate component has implications for the design of
[high availability](../design.md) for nodes.

## Options Analysis

### 1. No change (leave broker embedded)

#### Advantages

1. Least change

#### Disadvantages

1. Means that starting/stopping Corda is tightly coupled to starting/stopping Artemis instances.
2. Risks resource leaks from one system component affecting other components.
3. Not pluggable if we wish to have an alternative broker.

### 2. External broker

#### Advantages

1. Separates concerns
2. Allows future pluggability and standardisation on AMQP
3. Separates life cycles of the components
4. Makes Artemis deployment much more out of the box.
5. Allows easier tuning of VM resources for Flow processing workloads vs broker type workloads.
6. Allows later encrypted version to be an enterprise feature that can interoperate with OS versions.

#### Disadvantages

1. More work
2. Requires creating a protocol to control external bridge formation.

## Recommendation and justification

Proceed with Option 2: External broker

## Decision taken

The broker should only be separated if required by other features (e.g. the float), otherwise not. (RGB, JC, MH agreed).

.. toctree::

   drb-meeting-20171116.md
