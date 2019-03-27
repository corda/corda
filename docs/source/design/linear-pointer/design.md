# StatePointer

## Background

Occasionally there is a need to create a link from one `ContractState` to another. This has the effect of creating a uni-directional "one-to-one" relationship between a pair of `ContractState`s.

There are two ways to do this.

### By `StateRef`

Link one `ContractState` to another by including a `StateRef` or a `StateAndRef<T>` as a property inside another `ContractState`:

```kotlin
// StateRef.
data class FooState(val ref: StateRef) : ContractState
// StateAndRef.
data class FooState(val ref: StateAndRef<BarState>) : ContractState
```

Linking to a `StateRef` or `StateAndRef<T>` is only recommended if a specific version of a state is required in perpetuity. Clearly, adding a `StateAndRef` embeds the data directly. This type of pointer is compatible with any `ContractState` type.

But what if the linked state is updated? The `StateRef` will be pointing to an older version of the data and this could be a problem for the `ContractState` which contains the pointer.

### By `linearId`

To create a link to the most up-to-date version of a state, instead of linking to a specific `StateRef`, a `linearId` which references a `LinearState` can be used. This is because all `LinearState`s contain a `linearId` which refers to a particular lineage of `LinearState`. The vault can be used to look-up the most recent state with the specified `linearId`.

```kotlin
// Link by LinearId.
data class FooState(val ref: UniqueIdentifier) : ContractState
```

This type of pointer only works with `LinearState`s.

### Resolving pointers

The trade-off with pointing to data in another state is that the data being pointed to cannot be immediately seen. To see the data contained within the pointed-to state, it must be "resolved".

## Design

Introduce a `StatePointer` interface and two implementations of it; the `StaticPointer` and the `LinearPointer`. The `StatePointer` is defined as follows:

```kotlin
interface StatePointer {
    val pointer: Any
    fun resolve(services: ServiceHub): StateAndRef<ContractState>
}
```

The `resolve` method facilitates the resolution of the `pointer` to a `StateAndRef`.

The `StaticPointer` type requires developers to provide a `StateRef` which points to a specific state.

```kotlin
class StaticPointer(override val pointer: StateRef) : StatePointer {
    override fun resolve(services: ServiceHub): StateAndRef<ContractState> {
        val transactionState = services.loadState(pointer)
        return StateAndRef(transactionState, pointer)
    }
}
```

The `LinearPointer` type contains the `linearId` of the `LinearState` being pointed to and a `resolve` method. Resolving a `LinearPointer` returns a `StateAndRef<T>` containing the latest version of the `LinearState` that the node calling `resolve` is aware of.

```kotlin
class LinearPointer(override val pointer: UniqueIdentifier) : StatePointer {
    override fun resolve(services: ServiceHub): StateAndRef<LinearState> {
        val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(pointer))
        val result = services.vaultService.queryBy<LinearState>(query).states
        check(result.isNotEmpty()) { "LinearPointer $pointer cannot be resolved." }
        return result.single()
    }
}
```

### Bi-directional link

Symmetrical relationships can be modelled by embedding a `LinearPointer` in the pointed-to `LinearState` which points in the "opposite" direction. **Note:** this can only work if both states are `LinearState`s.

## Use-cases

It is important to note that this design only standardises a pattern which is currently possible with the platform. In other words, this design does not enable anything new.

### Tokens

Uncoupling token type definitions from the notion of ownership. Using the `LinearPointer`, `Token` states can include an `Amount` of some pointed-to type. The pointed-to type can evolve independently from the `Token` state which should just be concerned with the question of ownership.

## Issues and resolutions

Some issue to be aware of and their resolutions:

| Problem                                                      | Resolution                                                   |
| :----------------------------------------------------------- | ------------------------------------------------------------ |
| If the node calling `resolve` has not seen the specified `StateRef`, then `resolve` will return `null`. Here, the node calling `resolve` might be missing some crucial data. | Use data distribution groups. Assuming the creator of the `ContractState` publishes it to a data distribution group, subscribing to that group ensures that the node calling resolve will eventually have the required data. |
| The node calling `resolve` has seen and stored transactions containing a `LinearState` with the specified `linearId`. However, there is no guarantee the `StateAndRef<T>` returned by `resolve` is the most recent version of the `LinearState`. | Embed the pointed-to `LinearState` in transactions containing the `LinearPointer` as a reference state. The reference states feature will ensure the pointed-to state is the latest version. |
| The creator of the pointed-to `ContractState` exits the state from the ledger. If the pointed-to state is included a reference state then notaries will reject transactions containing it. | Contract code can be used to make a state un-exitable.       |

All of the noted resolutions rely on additional paltform features:

* Reference states which will be available in V4
* Data distribution groups which are not currently available. However, there is an early prototype
* Additional state interface

### Additional concerns and responses

#### Embedding reference states in transactions

**Concern:** Embedding reference states for pointed-to states in transactions could cause transactions to increase by some unbounded size. 

**Response:** The introduction of this feature doesn't create a new platform capability. It merely formalises a pattern which is currently possible. Futhermore, there is a possibility that _any_ type of state can cause a transaction to increase by some un-bounded size. It is also worth remembering that the maximum transaction size is 10MB.

#### `StatePointer`s are not human readable

**Concern:** Users won't know what sits behind the pointer.

**Response:** When the state containing the pointer is used in a flow, the pointer can be easily resolved. When the state needs to be displayed on a UI, the pointer can be resolved via vault query.

#### This feature adds complexity to the platform

**Concern:** This all seems quite complicated.

**Response:** It's possible anyway. Use of this feature is optional.

#### Coinselection will be slow.

**Concern:** We'll need to join on other tables to perform coinselection, making it slower. This is when a `StatePointer` is used as a `FungibleState` or `FungibleAsset` type.

**Response:** This is probably not true in most cases. Take the existing coinselection code from `CashSelectionH2Impl.kt`:

```sql
SELECT vs.transaction_id, vs.output_index, ccs.pennies, SET(@t, ifnull(@t,0)+ccs.pennies) total_pennies, vs.lock_id
FROM vault_states AS vs, contract_cash_states AS ccs
WHERE vs.transaction_id = ccs.transaction_id AND vs.output_index = ccs.output_index
AND vs.state_status = 0
AND vs.relevancy_status = 0
AND ccs.ccy_code = ? and @t < ?
AND (vs.lock_id = ? OR vs.lock_id is null)
```

Notice that the only property required which is not accessible from the `StatePointer` is the `ccy_code`. This is not necessarily a problem though, as the `pointer` specified in the pointer can be used as a proxy for the `ccy_code` or "token type".




