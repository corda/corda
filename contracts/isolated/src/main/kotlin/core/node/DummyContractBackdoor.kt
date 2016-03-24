package core.node

interface DummyContractBackdoor {
    fun generateInitial(owner: core.PartyReference, magicNumber: Int) : core.TransactionBuilder

    fun inspectState(state: core.ContractState) : Int
}