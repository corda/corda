# Design doc template

## Overview

We wish to move the `PersistentIdentityService` away from using `PublicKey.hash.toHexString()` to using the correct method
`PublicKey.toStringShort()`

This requires modifying the `PersistentIdentityService` and an accompanying Database Migration. 

## Background

In Corda4 we introduced an ability to map a given `PublicKey` to a UUID. Internally this builds a database table which maintains a mapping 
between `H(PublicKey)` -> UUID. Where `H()` is `PublicKey.toStringShort()`. 

There is a reasonable requirement that for a given `UUID` you would want to find all the keys that are associated with that UUID. 
To do this, we would need to join the `PublicKeyHashToExternalId` table with the `PersistentIdentity` table. 

This is currently impossible due to the fact that the two tables use different hashes. 

## Goals

* Migrate `PersistentIdentityService` to use `PublicKey.toStringShort()`
* Migrate the existing stored data

## Timeline

* This would be required for usage of Accounts. Therefore, it would need to be included in any release that is intended for clients using Accounts. 

## Requirements

* It must be possible to join the `PublicKeyHashToExternalId` table with the `PersistentIdentity` table. 
* Existing Identities must be safely migrated to using the new hashing approach. 

## Design Decisions

* We will use Liquibase to perform the migration

## Design

The intention is to remove the use of `SecureHash` as an intermediary step in hashing a `PublicKey` and instead directly invoke `toStringShort()`. 
This will ensure that for the same PK, all the tables within the node share a joinable column.

Luckily within the `PersistentIdentity` table, we store the full `PartyAndCertificate` which allows us to load the previously stored record, 
and obtain the correct hash. We can then use the same `PartyAndCertificate` to calculate the originally stored value, 
and perform a simple `UPDATE RECORD SET RECORD.PK_HASH = <new_value> WHERE RECORD.PK_HASH = <old_value>` to safely migrate the data.

### Testing

It is possible to write a simple Unit Test to insert records into the table using the old hashing mechanism, execute the migration. 
and then check that the expected value is present within the updated rows. This will give a level of confidence that the migration is safe. 

For more extensive testing, we propose to start a node using an unfixed C4 version, insert some Identities (both well known and confidential)
shutdown the node, place a fixed version and then check that the identities are resolvable and present in the database with the correct form of hash.

For enterprise, the testing performed with H2 (start, shutdown, migrate, restart) must be performed for all the supported database engines 
both using the DbMigrationTool and by allowing the node to migrate itself. 


To see a reference implementation of this design check out: https://github.com/corda/corda/pull/5217/files