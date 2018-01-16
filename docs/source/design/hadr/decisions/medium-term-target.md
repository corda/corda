![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

------

# Design Decision: Medium-term target for node HA

## Background / Context

Designing for high availability is a complex task which can only be delivered over an operationally-significant timeline. It is therefore important to determine whether an intermediate state design (deliverable for around March 2018) is desirable as a precursor to longer term outcomes.



## Options Analysis

### 1. Hot-warm as interim state (see [HA design doc](../design.md))

#### Advantages

1. Simpler master/slave election logic
2. Less edge cases with respect to messages being consumed by flows.
3. Naive solution of just stopping/starting the node code is simple to implement.

#### Disadvantages

1. Still probably requires the Artemis MQ outside of the node in a cluster.
2. May actually turn out more risky than hot-hot, because shutting down code is always prone to deadlocks and resource leakages.
3. Some work would have to be thrown away when we create a full hot-hot solution.

### 2. Progress immediately to Hot-hot (see [HA design doc](../design.md))

#### Advantages

1. Horizontal scalability is what all our customers want.
2. It simplifies many deployments as nodes in a cluster are all equivalent.

#### Disadvantages

1. More complicated especially regarding message routing.
2. Riskier to do this big-bang style.
3. Might not meet deadlines.

## Recommendation and justification

Proceed with Option 1: Hot-warm as interim state.

## Decision taken

**[DRB meeting, 16/11/2017:](./drb-meeting-20171116.md)** Adopt option 1: Medium-term target: Hot Warm (RGB, JC, MH agreed)

