package core.node

interface DummyContractBackdoor {
    fun generateInitial(owner: core.PartyAndReference, magicNumber: Int, notary: core.Party): core.TransactionBuilder

    fun inspectState(state: core.ContractState): Int
}