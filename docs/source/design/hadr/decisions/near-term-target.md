![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Decision: Near-term target for node HA
============================================

## Background / Context

Designing for high availability is a complex task which can only be delivered over an operationally-significant timeline. It is therefore important to determine the target state in the near term as a precursor to longer term outcomes.



## Options Analysis

### 1. No HA

#### Advantages

1.    Reduces developer distractions.

#### Disadvantages

1.    No backstop if we miss our targets for fuller HA.
2.    No answer at all for simple DR modes.

### 2. Hot-cold (see [HA design doc](../design.md))

#### Advantages

1. Flushes out lots of basic deployment issues that will be of benefit later.
2. If stuff slips we at least have a backstop position with hot-cold.
3. For now, the only DR story we have is essentially a continuation of this mode
4. The intent of decisions such as using a loadbalancer is to minimise code changes

#### Disadvantages

1. Distracts from the work for more complete forms of HA.
2. Involves creating a few components that are not much use later, for instance the mutual exclusion lock.

## Recommendation and justification

Proceed with Option 2: Hot-cold.

## Decision taken

**[DRB meeting, 16/11/2017:](./drb-meeting-20171116.md)** Adopt option 2: Near-term target: Hot Cold (RGB, JC, MH agreed)
