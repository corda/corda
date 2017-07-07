API: Vault Query
================

Corda has been architected from the ground up to encourage usage of industry standard, proven query frameworks and libraries for accessing RDBMS backed transactional stores (including the Vault).

Corda provides a number of flexible query mechanisms for accessing the Vault:

- Vault Query API
- custom JPA_/JPQL_ queries
- custom 3rd party Data Access frameworks such as `Spring Data <http://projects.spring.io/spring-data>`_

The majority of query requirements can be satisfied by using the Vault Query API, which is exposed via the ``VaultQueryService`` for use directly by flows:

.. literalinclude:: ../../core/src/main/kotlin/net/corda/core/node/services/Services.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryAPI
    :end-before: DOCEND VaultQueryAPI

and via ``CordaRPCOps`` for use by RPC client applications:

.. literalinclude:: ../../core/src/main/kotlin/net/corda/core/messaging/CordaRPCOps.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryByAPI
    :end-before: DOCEND VaultQueryByAPI

.. literalinclude:: ../../core/src/main/kotlin/net/corda/core/messaging/CordaRPCOps.kt
    :language: kotlin
    :start-after: DOCSTART VaultTrackByAPI
    :end-before: DOCEND VaultTrackByAPI

Helper methods are also provided with default values for arguments:

.. literalinclude:: ../../core/src/main/kotlin/net/corda/core/messaging/CordaRPCOps.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryAPIHelpers
    :end-before: DOCEND VaultQueryAPIHelpers

.. literalinclude:: ../../core/src/main/kotlin/net/corda/core/messaging/CordaRPCOps.kt
    :language: kotlin
    :start-after: DOCSTART VaultTrackAPIHelpers
    :end-before: DOCEND VaultTrackAPIHelpers

The API provides both static (snapshot) and dynamic (snapshot with streaming updates) methods for a defined set of filter criteria.

- Use ``queryBy`` to obtain a only current snapshot of data (for a given ``QueryCriteria``)
- Use ``trackBy`` to obtain a both a current snapshot and a future stream of updates (for a given ``QueryCriteria``)
  
.. note:: Streaming updates are only filtered based on contract type and state status (UNCONSUMED, CONSUMED, ALL)

Simple pagination (page number and size) and sorting (directional ordering using standard or custom property attributes) is also specifiable.
Defaults are defined for Paging (pageNumber = 1, pageSize = 200) and Sorting (direction = ASC).

The ``QueryCriteria`` interface provides a flexible mechanism for specifying different filtering criteria, including and/or composition and a rich set of operators to include: binary logical (AND, OR), comparison (LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL), equality (EQUAL, NOT_EQUAL), likeness (LIKE, NOT_LIKE), nullability (IS_NULL, NOT_NULL), and collection based (IN, NOT_IN). Standard SQL-92 aggregate functions (SUM, AVG, MIN, MAX, COUNT) are also supported.

There are four implementations of this interface which can be chained together to define advanced filters.

1. ``VaultQueryCriteria`` provides filterable criteria on attributes within the Vault states table: status (UNCONSUMED, CONSUMED), state reference(s), contract state type(s), notaries, soft locked states, timestamps (RECORDED, CONSUMED).

	.. note:: Sensible defaults are defined for frequently used attributes (status = UNCONSUMED, includeSoftlockedStates = true).

2. ``FungibleAssetQueryCriteria`` provides filterable criteria on attributes defined in the Corda Core ``FungibleAsset`` contract state interface, used to represent assets that are fungible, countable and issued by a specific party (eg. ``Cash.State`` and ``CommodityContract.State`` in the Corda finance module). Filterable attributes include: participants(s), owner(s), quantity, issuer party(s) and issuer reference(s).
	   
	.. note:: All contract states that extend the ``FungibleAsset`` now automatically persist that interfaces common state attributes to the **vault_fungible_states** table.

3. ``LinearStateQueryCriteria`` provides filterable criteria on attributes defined in the Corda Core ``LinearState`` and ``DealState`` contract state interfaces, used to represent entities that continuously supercede themselves, all of which share the same *linearId* (eg. trade entity states such as the ``IRSState`` defined in the SIMM valuation demo). Filterable attributes include: participant(s), linearId(s), dealRef(s).
	   
	.. note:: All contract states that extend ``LinearState`` or ``DealState`` now automatically persist those interfaces common state attributes to the **vault_linear_states** table.

4. ``VaultCustomQueryCriteria`` provides the means to specify one or many arbitrary expressions on attributes defined by a custom contract state that implements its own schema as described in the :doc:`Persistence </api-persistence>` documentation and associated examples. Custom criteria expressions are expressed using one of several type-safe ``CriteriaExpression``: BinaryLogical, Not, ColumnPredicateExpression, AggregateFunctionExpression. The ``ColumnPredicateExpression`` allows for specification arbitrary criteria using the previously enumerated operator types. The ``AggregateFunctionExpression`` allows for the specification of an aggregate function type (sum, avg, max, min, count) with optional grouping and sorting. Furthermore, a rich DSL is provided to enable simple construction of custom criteria using any combination of ``ColumnPredicate``. See the ``Builder`` object in ``QueryCriteriaUtils`` for a complete specification of the DSL. 

    .. note:: It is a requirement to register any custom contract schemas to be used in Vault Custom queries in the associated `CordaPluginRegistry` configuration for the respective CorDapp using the ``requiredSchemas`` configuration field (which specifies a set of `MappedSchema`)

An example of a custom query is illustrated here:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample20
    :end-before: DOCEND VaultQueryExample20

All ``QueryCriteria`` implementations are composable using ``and`` and ``or`` operators, as also illustrated above.

All ``QueryCriteria`` implementations provide an explicitly specifiable ``StateStatus`` attribute which defaults to filtering on UNCONSUMED states.
	   
.. note:: Custom contract states that implement the ``Queryable`` interface may now extend common schemas types ``FungiblePersistentState`` or, ``LinearPersistentState``.  Previously, all custom contracts extended the root ``PersistentState`` class and defined repeated mappings of ``FungibleAsset`` and ``LinearState`` attributes. See ``SampleCashSchemaV2`` and ``DummyLinearStateSchemaV2`` as examples.

Examples of these ``QueryCriteria`` objects are presented below for Kotlin and Java.

.. note:: When specifying the Contract Type as a parameterised type to the QueryCriteria in Kotlin, queries now include all concrete implementations of that type if this is an interface. Previously, it was only possible to query on Concrete types (or the universe of all Contract States).

The Vault Query API leverages the rich semantics of the underlying JPA Hibernate_ based :doc:`Persistence </api-persistence>` framework adopted by Corda.

.. _Hibernate: https://docs.jboss.org/hibernate/jpa/2.1/api/

.. note:: Permissioning at the database level will be enforced at a later date to ensure authenticated, role-based, read-only access to underlying Corda tables.

.. note:: API's now provide ease of use calling semantics from both Java and Kotlin. However, it should be noted that Java custom queries are significantly more verbose due to the use of reflection fields to reference schema attribute types.

An example of a custom query in Java is illustrated here:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultJavaQueryExample3
    :end-before: DOCEND VaultJavaQueryExample3

.. note:: Current queries by ``Party`` specify the ``AbstractParty`` which may be concrete or anonymous. In the later case, where an anonymous party does not have an associated X500Name, then no query results will ever be produced. For performance reasons, queries do not use PublicKey as search criteria. Ongoing design work on identity manangement is likely to enhance identity based queries (including composite key criteria selection).

Pagination
----------
The API provides support for paging where large numbers of results are expected (by default, a page size is set to 200 results).
Defining a sensible default page size enables efficient checkpointing within flows, and frees the developer from worrying about pagination where
result sets are expected to be constrained to 200 or fewer entries. Where large result sets are expected (such as using the RPC API for reporting and/or UI display), it is strongly recommended to define a ``PageSpecification`` to correctly process results with efficient memory utilistion. A fail-fast mode is in place to alert API users to the need for pagination where a single query returns more than 200 results and no ``PageSpecification``
has been supplied.

Example usage
-------------

Kotlin
^^^^^^

**General snapshot queries using** ``VaultQueryCriteria``

Query for all unconsumed states (simplest query possible):

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample1
    :end-before: DOCEND VaultQueryExample1

Query for unconsumed states for some state references:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample2
    :end-before: DOCEND VaultQueryExample2

Query for unconsumed states for several contract state types:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample3
    :end-before: DOCEND VaultQueryExample3

Query for unconsumed states for a given notary:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample4
    :end-before: DOCEND VaultQueryExample4

.. note:: We are using the notaries X500Name as our search identifier.

Query for unconsumed states for a given set of participants:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample5
    :end-before: DOCEND VaultQueryExample5

Query for unconsumed states recorded between two time intervals:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample6
    :end-before: DOCEND VaultQueryExample6

.. note:: This example illustrates usage of a Between ColumnPredicate.

Query for all states with pagination specification (10 results per page):

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample7
    :end-before: DOCEND VaultQueryExample7

.. note:: The result set metadata field `totalStatesAvailable` allows you to further paginate accordingly.

**LinearState and DealState queries using** ``LinearStateQueryCriteria``

Query for unconsumed linear states for given linear ids:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample8
    :end-before: DOCEND VaultQueryExample8

This example was previously executed using the deprecated extension method:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultDeprecatedQueryExample1
    :end-before: DOCEND VaultDeprecatedQueryExample1

Query for all linear states associated with a linear id:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample9
    :end-before: DOCEND VaultQueryExample9

This example was previously executed using the deprecated method:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultDeprecatedQueryExample2
    :end-before: DOCEND VaultDeprecatedQueryExample2

Query for unconsumed deal states with deals references:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample10
    :end-before: DOCEND VaultQueryExample10

Query for unconsumed deal states with deals parties:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample11
    :end-before: DOCEND VaultQueryExample11

**FungibleAsset and DealState queries using** ``FungibleAssetQueryCriteria``

Query for fungible assets for a given currency:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample12
    :end-before: DOCEND VaultQueryExample12

Query for fungible assets for a minimum quantity:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample13
    :end-before: DOCEND VaultQueryExample13

.. note:: This example uses the builder DSL.

Query for fungible assets for a specifc issuer party:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample14
    :end-before: DOCEND VaultQueryExample14

**Aggregate Function queries using** ``VaultCustomQueryCriteria``

.. note:: Query results for aggregate functions are contained in the `otherResults` attribute of a results Page.

Aggregations on cash using various functions:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample21
    :end-before: DOCEND VaultQueryExample21

.. note:: `otherResults` will contain 5 items, one per calculated aggregate function.

Aggregations on cash grouped by currency for various functions:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample22
    :end-before: DOCEND VaultQueryExample22

.. note:: `otherResults` will contain 24 items, one result per calculated aggregate function per currency (the grouping attribute - currency in this case - is returned per aggregate result).

Sum aggregation on cash grouped by issuer party and currency and sorted by sum:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample23
    :end-before: DOCEND VaultQueryExample23

.. note:: `otherResults` will contain 12 items sorted from largest summed cash amount to smallest, one result per calculated aggregate function per issuer party and currency (grouping attributes are returned per aggregate result).

**Dynamic queries** (also using ``VaultQueryCriteria``) are an extension to the snapshot queries by returning an additional ``QueryResults`` return type in the form of an ``Observable<Vault.Update>``. Refer to `ReactiveX Observable <http://reactivex.io/documentation/observable.html>`_ for a detailed understanding and usage of this type. 

Track unconsumed cash states:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample15
    :end-before: DOCEND VaultQueryExample15

Track unconsumed linear states:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample16
    :end-before: DOCEND VaultQueryExample16

.. note:: This will return both Deal and Linear states.
    
Track unconsumed deal states:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample17
    :end-before: DOCEND VaultQueryExample17

.. note:: This will return only Deal states.

Java examples
^^^^^^^^^^^^^

Query for all unconsumed linear states:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultJavaQueryExample0
    :end-before: DOCEND VaultJavaQueryExample0

This example was previously executed using the deprecated method:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultDeprecatedJavaQueryExample0
    :end-before: DOCEND VaultDeprecatedJavaQueryExample0

Query for all consumed cash states:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultJavaQueryExample1
    :end-before: DOCEND VaultJavaQueryExample1

This example was previously executed using the deprecated method:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultDeprecatedJavaQueryExample1
    :end-before: DOCEND VaultDeprecatedJavaQueryExample1

Query for consumed deal states or linear ids, specify a paging specification and sort by unique identifier:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultJavaQueryExample2
    :end-before: DOCEND VaultJavaQueryExample2

**Aggregate Function queries using** ``VaultCustomQueryCriteria``

Aggregations on cash using various functions:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryJavaTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultJavaQueryExample21
    :end-before: DOCEND VaultJavaQueryExample21

Aggregations on cash grouped by currency for various functions:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryJavaTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultJavaQueryExample22
    :end-before: DOCEND VaultJavaQueryExample22

Sum aggregation on cash grouped by issuer party and currency and sorted by sum:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryJavaTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultJavaQueryExample23
    :end-before: DOCEND VaultJavaQueryExample23

Track unconsumed cash states:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultJavaQueryExample4
    :end-before: DOCEND VaultJavaQueryExample4

Track unconsumed deal states or linear states (with snapshot including specification of paging and sorting by unique identifier):

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultJavaQueryExample4
    :end-before: DOCEND VaultJavaQueryExample4

Other use case scenarios
------------------------

For advanced use cases that require sophisticated pagination, sorting, grouping, and aggregation functions, it is recommended that the CorDapp developer utilise one of the many proven frameworks that ship with this capability out of the box. Namely, implementations of JPQL (JPA Query Language) such as **Hibernate** for advanced SQL access, and **Spring Data** for advanced pagination and ordering constructs.

The Corda Tutorials provide examples satisfying these additional Use Cases:

     1. Template / Tutorial CorDapp service using Vault API Custom Query to access attributes of IOU State
     2. Template / Tutorial CorDapp service query extension executing Named Queries via JPQL_
     3. `Advanced pagination <https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/repository/PagingAndSortingRepository.html>`_ queries using Spring Data JPA_
        
 .. _JPQL: http://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#hql
 .. _JPA: https://docs.spring.io/spring-data/jpa/docs/current/reference/html

Upgrading from previous releases
---------------------------------

Here follows a selection of the most common upgrade scenarios:

1. ServiceHub usage to obtain Unconsumed states for a given contract state type
   
   Previously:

.. container:: codeset

   .. sourcecode:: kotlin

		val yoStates = b.vault.unconsumedStates<Yo.State>()

This query returned an ``Iterable<StateAndRef<T>>`` 

   Now:

.. container:: codeset

   .. sourcecode:: kotlin

		val yoStates = b.vault.queryBy<Yo.State>().states

The query returns a ``Vault.Page`` result containing:

	- states as a ``List<StateAndRef<T : ContractState>>`` sized according to the default Page specification of ``DEFAULT_PAGE_NUM`` (0) and ``DEFAULT_PAGE_SIZE`` (200).
	- states metadata as a ``List<Vault.StateMetadata>`` containing Vault State metadata held in the Vault states table.
	- the ``PagingSpecification`` used in the query
	- a ``total`` number of results available. This value can be used issue subsequent queries with appropriately specified ``PageSpecification`` (according to your paging needs and/or maximum memory capacity for holding large data sets). Note it is your responsibility to manage page numbers and sizes.

2. ServiceHub usage obtaining linear heads for a given contract state type
   
   Previously:

.. container:: codeset

   .. sourcecode:: kotlin

		val iouStates = serviceHub.vaultService.linearHeadsOfType<IOUState>()
		val iouToSettle = iouStates[linearId] ?: throw Exception("IOUState with linearId $linearId not found.")

   Now:

.. container:: codeset

   .. sourcecode:: kotlin

		val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
		val iouStates = serviceHub.vaultService.queryBy<IOUState>(criteria).states

		val iouToSettle = iouStates.singleOrNull() ?: throw Exception("IOUState with linearId $linearId not found.")
   
3. RPC usage was limited to using the ``vaultAndUpdates`` RPC method, which returned a snapshot and streaming updates as an Observable. 
   In many cases, queries were not interested in the streaming updates. 

   Previously:

.. container:: codeset

   .. sourcecode:: kotlin

		val iouStates = services.vaultAndUpdates().first.filter { it.state.data is IOUState }

   Now:

.. container:: codeset

   .. sourcecode:: kotlin

		val iouStates = services.vaultQueryBy<IOUState>()
 
