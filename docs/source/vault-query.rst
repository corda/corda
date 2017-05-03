Vault Query
===========

Corda has been architected from the ground up to encourage usage of industry standard, proven query frameworks and libraries for accessing RDBMS backed transactional stores (including the Vault).

Corda provides a number of flexible query mechanisms for accessing the Vault:

- Vault Query API
- custom JPA_/JPQL_ queries
- custom 3rd party Data Access frameworks such as `Spring Data <http://projects.spring.io/spring-data>`_

The majority of query requirements can be satified by using the Vault Query API, which is exposed via the ``VaultService`` for use directly by flows:

.. literalinclude:: ../../core/src/main/kotlin/net/corda/core/node/services/Services.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryAPI
    :end-before: DOCEND VaultQueryAPI

and via ``CordaRPCOps`` for use by RPC client applications:

.. literalinclude:: ../../core/src/main/kotlin/net/corda/core/messaging/CordaRPCOps.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryAPI
    :end-before: DOCEND VaultQueryAPI

The API provides both static (snapshot) and dynamic (snapshot with streaming updates) methods for a defined set of filter criteria.

- Use ``queryBy`` to obtain a only current snapshot of data (for a given ``QueryCriteria``)
- Use ``trackBy`` to obtain a both a current snapshot and a future stream of updates (for a given ``QueryCriteria``)

Simple pagination (page number and size) and sorting (directional ordering, null handling, custom property sort) is also specifiable.
Defaults are defined for Paging (pageNumber = 0, pageSize = 200) and Sorting (direction = ASC, nullHandling = NULLS_LAST).

The ``QueryCriteria`` interface provides a flexible mechanism for specifying different filtering criteria, including and/or composition and a rich set of logical operators. There are four implementations of this interface which can be chained together to define advanced filters.

	1. ``VaultQueryCriteria`` provides filterable criteria on attributes within the Vault states table: status (UNCONSUMED, CONSUMED), state reference(s), contract state type(s), notaries, soft locked states, timestamps (RECORDED, CONSUMED).

	   .. note:: Sensible defaults are defined for frequently used attributes (status = UNCONSUMED, includeSoftlockedStates = true).

	2. ``FungibleAssetQueryCriteria`` provides filterable criteria on attributes defined in the Corda Core ``FungibleAsset`` contract state interface, used to represent assets that are fungible, countable and issued by a specific party (eg. ``Cash.State`` and ``CommodityContract.State`` in the Corda finance module). 
	   
	   .. note:: Contract states that extend the ``FungibleAsset`` interface now automatically persist associated state attributes. 

	3. ``LinearStateQueryCriteria`` provides filterable criteria on attributes defined in the Corda Core ``LinearState`` and ``DealState`` contract state interfaces, used to represent entities that continuously supercede themselves, all of which share the same *linearId* (eg. trade entity states such as the ``IRSState`` defined in the SIMM valuation demo)
	   
	   .. note:: Contract states that extend the ``LinearState`` or ``DealState`` interfaces now automatically persist associated state attributes. 

	4. ``VaultIndexedQueryCriteria`` provides the means to specify one or many arbitrary expressions on attributes defined by a custom contract state that implements its own schema as described in the Persistence_ documentation and associated examples. Specifically, index queryable attributes must be explicitly mapped to the ``GenericVaultIndexSchema`` internal table when implementing the ``generateMappedObject`` schema mapping function of ``QueryableState`` in your custom Contract state.
	   
	An example snippet of code extracted from the ``CommercialPaper`` contract is presented here:

.. literalinclude:: ../../finance/src/main/kotlin/net/corda/contracts/CommercialPaper.kt
	:language: kotlin
	:start-after: DOCSTART VaultIndexedQueryCriteria
	:end-before: DOCEND VaultIndexedQueryCriteria

In a future iteration of this API, we may adopt an annotation based approach, whereby custom contract state attributes are directly annotated using ``@CordaVaultIndex`` in their schema definition as shown in the following snippet::

    class PersistentCommericalPaperState(
            @Column(name = "issuance_key")
            var issuanceParty: String,

            @Column(name = "issuance_ref")
            var issuanceRef: ByteArray,

            @Column(name = "owner_key")
            var owner: String,

            @CordaVaultIndex(mapToTableName= "GenericVaultIndexSchemaV1.PersistentGenericVaultIndexSchemaState", mapToColumn = "timeIndex1")
            @Column(name = "maturity_instant")
            var maturity: Instant,

            @CordaVaultIndex(mapToTableName= "GenericVaultIndexSchemaV1.PersistentGenericVaultIndexSchemaState", mapToColumn = "longIndex1")
            @Column(name = "face_value")
            var faceValue: Long,

            @CordaVaultIndex(mapToTableName= "GenericVaultIndexSchemaV1.PersistentGenericVaultIndexSchemaState", mapToColumn = "stringIndex1")
            @Column(name = "ccy_code", length = 3)
            var currency: String,

            @Column(name = "face_value_issuer_key")
            var faceValueIssuerParty: String,

            @Column(name = "face_value_issuer_ref")
            var faceValueIssuerRef: ByteArray
    ) : PersistentState()	
	
Additional notes

	.. note:: the ``GenericVaultIndexSchema`` entity is versioned as per other contract schemas (to enable extensibility) and provides a set of re-usable index attribute field definitions for primary types (eg. String, Instant, Long).

	.. note:: Custom contract states that implement the ``Queryable`` interface may now extend the ``FungiblePersistentState``, ``LinearPersistentState`` or ``DealPersistentState`` classes when implementing their ``MappedSchema``. Previously, all custom contracts extended the root ``PersistentState`` class and defined repeated mappings of ``FungibleAsset``, ``LinearState`` and ``DealState`` attributes.

Examples of these ``QueryCriteria`` objects are presented below for Kotlin and Java.

The Vault Query API leverages the rich semantics of the underlying Requery_ persistence framework adopted by Corda.

.. _Requery: https://github.com/requery/requery/wiki
.. _Persistence: https://docs.corda.net/persistence.html

.. note:: Permissioning at the database level will be enforced at a later date to ensure authenticated, role-based, read-only access to underlying Corda tables.

.. note:: API's now provide ease of use calling semantics from both Java and Kotlin.

.. note:: Current queries by ``Party`` specify only a party name as the underlying identity framework is being re-designed. In future it may be possible to query on specific elements of a parties identity such as a ``CompositeKey`` hierarchy (parent and child nodes, weightings). 

Example usage
-------------

Kotlin
^^^^^^

**General snapshot queries using** ``VaultQueryCriteria``

Query for all unconsumed states:

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

Query for all states with pagination specification:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample7
    :end-before: DOCEND VaultQueryExample7

**LinearState and DealState queries using** ``LinearStateQueryCriteria``

Query for unconsumed linear states for given linear ids:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample8
    :end-before: DOCEND VaultQueryExample8

.. note:: This example was previously executed using the deprecated extension method:

	.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
	    :language: kotlin
	    :start-after: DOCSTART VaultDeprecatedQueryExample1
	    :end-before: DOCEND VaultDeprecatedQueryExample1

Query for all linear states associated with a linear id:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample9
    :end-before: DOCEND VaultQueryExample9

.. note:: This example was previously executed using the deprecated method:

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

Query for fungible assets for a given currency and minimum quantity:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample13
    :end-before: DOCEND VaultQueryExample13

Query for fungible assets for a specifc issuer party:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample14
    :end-before: DOCEND VaultQueryExample14

Query for consumed fungible assets with a specific exit key:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample15
    :end-before: DOCEND VaultQueryExample15

**Indexed custom contract state queries using** ``VaultIndexedQueryCriteria``

Query for custom attributes (mapped explicitly to the ``GenericVaultIndexSchema`` table as described above) of the Commercial paper contract:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample16
    :end-before: DOCEND VaultQueryExample16

Java examples
^^^^^^^^^^^^^

Query for all consumed contract states:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultJavaQueryExample1
    :end-before: DOCEND VaultJavaQueryExample1

.. note:: This example was previously executed using the deprecated method:

	.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
	    :language: java
	    :start-after: DOCSTART VaultDeprecatedJavaQueryExample1
	    :end-before: DOCEND VaultDeprecatedJavaQueryExample1

Query for all deal states:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultJavaQueryExample2
    :end-before: DOCEND VaultJavaQueryExample2

.. note:: This example was previously executed using the deprecated method:

	.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
	    :language: java
	    :start-after: DOCSTART VaultDeprecatedJavaQueryExample2
	    :end-before: DOCEND VaultDeprecatedJavaQueryExample2

**Dynamic queries** (also using ``VaultQueryCriteria``) are an extension to the snapshot queries by returning an additional ``QueryResults`` return type in the form of an ``Observable<Vault.Update>``. Refer to `ReactiveX Observable <http://reactivex.io/documentation/observable.html>`_ for a detailed understanding and usage of this type. 

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

   Now::

.. container:: codeset

   .. sourcecode:: kotlin

		val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
		val iouStates = serviceHub.vaultService.queryBy<IOUState>(criteria).states

		val iouToSettle = iouStates.singleOrNull() ?: throw Exception("IOUState with linearId $linearId not found.")
   
3. RPC usage was limited to using the ``vaultAndUpdates`` RPC method, which returned a snapshot and streaming updates as an Observable. 
   In many cases, queries were not interested in the streaming updates. 

   Previously::

.. container:: codeset

   .. sourcecode:: kotlin

		val iouStates = services.vaultAndUpdates().first.filter { it.state.data is IOUState }

   Now::

.. container:: codeset

   .. sourcecode:: kotlin

		val iouStates = services.vaultQueryBy<IOUState>()
 
