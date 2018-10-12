# LinearPointer

## Background

Occasionally there is a need to link two `ContractState`s. This has the
effect of creating a "one-to-one" or "one-to-many" relationship between
the two states.

The linking can be done by including a `StateRef` in a `ContractState`
as follows:

    data class FooState(val ref: StateRef)

or if the by including a `StateAndRef<T>`:

    data class FooState(val ref: StateAndRef<BarState>)

Linking to a `StateRef` or `StateAndRef<T>` is only recommended if a
specific version of a state is required.

The process to resolve the `StateRef`

If the most up-to-date version of a `ContractState` is required, the
referenced state must be a `LinearState`. This means it is possible to
query the vault for the most

some on-ledger data contained within a `ContractState` with a
`ContractState` and that data, for whatever reason, cannot be included
_within_ the referring `ContractState`.

Typically the data which is being referred to will exist in a different
state, usually a `LinearState` and it will be managed by and evolve
independently to the `ContractState` which refers to it.

A relationship between a [ContractState] and a [LinearState] can be
modelled without any special types. However,



`LinearPointer` allows a `ContractState` to "point" to another `LinearState` creating a "many- to-one" relationship between all the states containing the pointer to a particular `LinearState` and the `LinearState` being pointed to.

It is a useful pattern when one state depends on the contained data within another state and there is an expectation that the state being depended upon will involve independently to the depending state.

abstract class LinearPointer {
    abstract val pointer: UniqueIdentifier
    inline fun<reified T : LinearState>resolve(services: ServiceHub): T? {
        val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(pointer))
        return services.vaultService.queryBy<T>(query).states.singleOrNull()?.state?.data
    }
}
The LinearPointer contains the linearId of the LinearState being pointed to and a resolve() method. Resolving a LinearPointer returns a StateAndRef containing the latest version of the LinearState that the node calling resolve() is aware of. There are two issues to note with LinearPointer s:

If the node calling resolve() has not seen any transactions containing a LinearState with the specified linearId then resolve() will return null .
Even if the node calling resolve() has seen and stored transactions containing a LinearState with the specified linearId, there is no guarantee the StateAndRef returned is the most recent version of the LinearState.
Both of the above problems can be resolved by adding the pointed-to LinearState as a reference state to the transaction containing the state with the LinearPointer. This way, the pointed-to state travels around with the pointer, such that the LinearPointer can always be resolved. Furthermore, the reference states feature will ensure that the pointed-to state remains current. It's worth noting that embedding the pointed-to state may not always be preferable, especially if it is quite large.