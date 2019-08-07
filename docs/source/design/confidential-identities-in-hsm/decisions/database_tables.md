# Design Decision: Database tables

## Background / Context

In order to retain backwards compatibility, we will need to store both existing keys (of the old format) and create new keys (with the new format).
This will introduce the following deviation in the flow of the node:
* When a new confidential identity is created, the node will need to decide based on its configuration where and how the new identity's keys should be stored.
* When a signature needs to be created for an existing public key, the node will need to identify the type of this key. Depending on the key, the node will need to query the database in the appropriate way and then use the proper APIs to complete the signing. The first part is relevant in the context if this section. 
There are 2 different options to achieve that, either having a single database table with a generified schema that can support both formats or have multiple tables each one with a different schema. 

## Options Analysis

### A. Single table

#### Advantages

1. Fewer tables to manage.


#### Disadvantages

1. Additional DDL scripts will be required in order to alter the initial schema to the generified one. This will make the upgrade process more complicated and error-prone.
2. Retrieval of a confidential identity entry from the database will get more complicated. Either a new ORM entity type needs to be created that encompasses the superset of all properties corresponding to the generified schema or hand-crafted SQL/HQL queries need to be performed to extract the proper entities from the database, depending on the value of a *"flag"* column.
3. As a result of the above, additional DML scripts will be required to alter the existing keys during an upgrade, so that they contain the appropriate value for the *"flag"* column.

### B. Multiple tables

#### Advantages

1. ​The existing table does not need to be altered, only a new table needs to be created. This provides a simpler upgrade path.
2. ​Cleaner code. One ORM entity for each table can be used without the need for hand-crafted SQL/HQL queries.

#### Disadvantages

1. More tables to manage. Also, potentially a table residing in the database that will not actually be used for deployments that have started directly using the new approach.
2. When retrieving a confidential identity by its public key, both tables will need to be queried. Given tables are fronted by a cache, the performance impact will be minimized. Specifically, in cases where the node does not have any legacy confidential identities, then the first table will be empty. As a result, proper ordering of these lookup operations can ensure that a single lookup will be performed in the majority of cases. If need be, the lookups can also be parallelized to improve latency further.

## Recommendation and justification

Proceed with option B, because it provides a simpler upgrade path and encourages simpler code logic.
 