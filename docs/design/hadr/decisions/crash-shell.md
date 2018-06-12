# Design Decision: Node starting & stopping

## Background / Context

The potential use of a crash shell is relevant to high availability capabilities of nodes.

## Options Analysis

### 1. Use crash shell

#### Advantages

1.    Already built into the node.
2.    Potentially add custom commands.

#### Disadvantages

1.    Won’t reliably work if the node is in an unstable state
2.    Not practical for running hundreds of nodes as our customers arealready trying to do.
3.    Doesn’t mesh with the user access controls of the organisation.
4.    Doesn’t interface to the existing monitoring andcontrol systems i.e. Nagios, Geneos ITRS, Docker Swarm, etc.

### 2. Delegate to external tools

#### Advantages

1. Doesn’t require change from our customers
2. Will work even if node is completely stuck
3. Allows scripted node restart schedules
4. Doesn’t raise questions about access controllists and audit

#### Disadvantages

1. More uncertainty about what customers do.
2. Might be more requirements on us to interact nicely with lots of different products.
3. Might mean we get blamed for faults in other people’s control software.
4. Doesn’t coordinate with the node for graceful shutdown.
5. Doesn’t address any crypto features that target protecting the AMQP headers.

## Recommendation and justification

Proceed with Option 2: Delegate to external tools

## Decision taken

Restarts should be handled by polite shutdown, followed by a hard clear. (RGB, JC, MH agreed)

.. toctree::

   drb-meeting-20171116.md
