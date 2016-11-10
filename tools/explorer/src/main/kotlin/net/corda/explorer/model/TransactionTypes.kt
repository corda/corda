package net.corda.explorer.model

enum class CashTransaction(val partyNameA: String, val partyNameB: String?) {
    Issue("Issuer Bank", "Receiver Bank"),
    Pay("Payer", "Payee"),
    Exit("Issuer Bank", null);
}
