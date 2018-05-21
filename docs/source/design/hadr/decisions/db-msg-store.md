# Design Decision: Message storage

## Background / Context

Storage of messages by the message broker has implications for replication technologies which can be used to ensure both
[high availability](../design.md) and disaster recovery of Corda nodes.

## Options Analysis

### 1. Storage in the file system

#### Advantages

1.    Out of the box configuration.
2.    Recommended Artemis setup
3.    Faster
4.    Less likely to have interaction with DB Blob rules

#### Disadvantages

1.    Unaligned capture time of journal data compared to DB checkpointing.
2.    Replication options on Azure are limited. Currently we may be forced to the ‘Azure Files’ SMB mount, rather than the ‘Azure Data Disk’ option. This is still being evaluated

### 2. Storage in node database

#### Advantages

1. Single point of data capture and backup
2. Consistent solution between VM and physical box solutions

#### Disadvantages

1. Doesn’t work on H2, or SQL Server. From my own testing LargeObject support is broken. The current Artemis code base does allow somepluggability, but not of the large object implementation, only of the SQLstatements. We should lobby for someone to fix the implementations for SQLServer and H2.
2. Probably much slower, although this needs measuring.

## Recommendation and justification

Continue with Option 1: Storage in the file system

## Decision taken

Use storage in the file system (for now)

.. toctree::

   drb-meeting-20171116.md
