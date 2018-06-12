# Design Decision: End-to-end encryption

## Background / Context

End-to-end encryption is a desirable potential design feature for the [float](../design.md).

## Options Analysis

### 1. No end-to-end encryption

#### Advantages

1.    Least effort
2.    Easier to fault find and manage

#### Disadvantages

1.    With no placeholder, it is very hard to add support later and maintain wire stability.
2.    May not get past security reviews of Float.

### 2. Placeholder only

#### Advantages

1. Allows wire stability when we have agreed an encrypted approach
2. Shows that we are serious about security, even if this isn’t available yet.
3. Allows later encrypted version to be an enterprise feature that can interoperate with OS versions.

#### Disadvantages

1. Doesn’t actually provide E2E, or define what an encrypted payloadlooks like.
2. Doesn’t address any crypto features that target protecting the AMQP headers.

### 3. Implement end-to-end encryption

1. Will protect the sensitive data fully.

#### Disadvantages

1. Lots of work.
2. Difficult to get right.
3. Re-inventing TLS.

## Recommendation and justification

Proceed with Option 2: Placeholder

## Decision taken

Proceed with Option 2 - Add placeholder, subject to more detailed design proposal (RGB, JC, MH agreed)

.. toctree::

   drb-meeting-20171116.md

