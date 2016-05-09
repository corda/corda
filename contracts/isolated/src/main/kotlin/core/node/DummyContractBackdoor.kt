package core.node

interface DummyContractBackdoor {
    fun generateInitial(owner: core.PartyAndReference, magicNumber: Int) : core.TransactionBuilder

    fun inspectState(state: core.ContractState) : Int
}